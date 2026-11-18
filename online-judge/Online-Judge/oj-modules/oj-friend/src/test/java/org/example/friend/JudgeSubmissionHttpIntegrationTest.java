package org.example.friend;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.example.common.security.service.TokenService;
import org.example.friend.domain.user.dto.UserSubmitDTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Duration;
import java.nio.file.Path;
import java.nio.file.Files;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 HTTP 受理、鉴权及异步判题验收，只允许使用 oj_reliability_test。
 * 普通用例需要独立 Judge；宕机、断库、管理员状态注入用例由额外开关分别启用。
 */
@EnabledIfSystemProperty(named = "judge.http.integration", matches = "true")
@SpringBootTest(
        classes = OFriendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.cloud.nacos.discovery.enabled=false",
            "spring.cloud.nacos.config.enabled=false"
        })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JudgeSubmissionHttpIntegrationTest {
    /** 测试用接口客户端，用于验证真实请求、鉴权及响应结构。 */
    @Autowired TestRestTemplate http;

    /** 测试用数据库访问工具，用于准备测试数据并核对持久化结果。 */
    @Autowired JdbcTemplate jdbc;

    /** 测试登录凭证服务，用于构造不同用户或管理员的认证身份。 */
    @Autowired TokenService tokens;

    /** 短事务执行模板，使一次业务操作中的多项数据库写入共同提交或回滚。 */
    @Autowired org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    /** 测试环境的登录凭证签名配置，仅用于构造鉴权测试请求，不应写入日志。 */
    @Value("${jwt.secret}")
    String secret;

    /** 提交对应的题目编号，用于读取题目及关联判题记录。 */
    private Long questionId;

    /** 提交者或成绩归属用户的编号，用于权限校验和用户维度的数据关联。 */
    private Long userId;

    /** 当前测试用户的登录凭证，用于带身份访问提交及查询接口。 */
    private String token;

    /** 另一测试用户的登录凭证，用于验证不能查询他人的任务。 */
    private String otherToken;

    /** 管理员测试凭证，用于验证失败任务排查及授权重试。 */
    private String adminToken;

    /** 预期可正确求解测试题目的源码，用于构造正常提交请求。 */
    private final String correctCode =
            "public class Solution { static int solve(int a,int b) { return a+b; } }";

    /** 为本轮创建独立题目和用户身份，避免覆盖已有业务数据。 */
    @BeforeAll
    void prepare() {
        assertEquals("oj_reliability_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        questionId = IdWorker.getId();
        jdbc.update(
                "INSERT INTO tb_question(question_id,title,difficulty,time_limit,space_limit,content,question_case,default_code,main_fuc,create_by,create_time) VALUES(?, 'reliability-test',1,2000,262144,'sum',?,?,?,1,NOW())",
                questionId,
                "[{\"input\":\"1 2\",\"output\":\"3\"},{\"input\":\"3 4\",\"output\":\"7\"}]",
                correctCode,
                "public static void main(String[] args) { System.out.print(solve(Integer.parseInt(args[0]), Integer.parseInt(args[1]))); }");
        userId = IdWorker.getId();
        token = tokens.createToken(userId, secret, 1, "judge-test", null);
        otherToken = tokens.createToken(IdWorker.getId(), secret, 1, "judge-other", null);
        adminToken = tokens.createToken(IdWorker.getId(), secret, 2, "judge-admin", null);
    }

    /** 默认构造新提交；验证网络重试时复用同一个 DTO，不能再次调用此方法生成新键。 */
    private UserSubmitDTO request(String code) {
        UserSubmitDTO request = new UserSubmitDTO();
        request.setQuestionId(questionId);
        request.setQuestionVersion(1L);
        request.setProgramType(0);
        request.setUserCode(code);
        request.setIdempotencyKey(UUID.randomUUID().toString());
        return request;
    }

    /** 携带指定身份凭证发送提交类接口请求，将响应解析为便于断言的结构化对象。 */
    private JSONObject post(String path, Object body, String auth) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (auth != null) {
            headers.setBearerAuth(auth);
        }
        return JSON.parseObject(
                http.postForObject(path, new HttpEntity<>(body, headers), String.class));
    }

    /** 按提交编号调用进度接口，使用指定身份验证任务归属和公开结果。 */
    private JSONObject task(String id, String auth) {
        return get("/user/question/submissions/" + id, auth);
    }

    /** 携带指定身份凭证发送查询请求，返回解析后的接口响应。 */
    private JSONObject get(String path, String auth) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(auth);
        return JSON.parseObject(
                http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class)
                        .getBody());
    }

    /** 通过真实受理接口提交请求，断言成功后提取提交编号，供后续查询与数据库核对使用。 */
    private String submit(UserSubmitDTO request) {
        JSONObject response = post("/user/question/submit", request, token);
        assertEquals(1000, response.getIntValue("code"), response.toJSONString());
        return response.getJSONObject("data").getString("submitId");
    }

    /** 使用默认等待期限调用完成状态轮询，要求任务最终得到有效判定，避免无限等待。 */
    private JSONObject finished(String id) {
        return finished(id, 45);
    }

    /** 有界轮询同一任务；只允许 FINISHED 通过，系统失败或等待超时都输出任务状态。 */
    private JSONObject finished(String id, int seconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            String state = task(id, token).getJSONObject("data").getString("state");
            if (state.equals("FINISHED") || state.equals("SYSTEM_FAILED")) {
                break;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }
        JSONObject data = task(id, token).getJSONObject("data");
        assertEquals("FINISHED", data.getString("state"), data.toJSONString());
        return data;
    }

    /** 在任务领取后终止首个工作进程，再启动新进程，验证租约补偿建立新尝试且有效结果只写入一次。 */
    @Test
    // 本例会启动并终止自己的 Judge JVM；在独立环境启用，验证接替进程通过租约补偿恢复。
    @EnabledIfSystemProperty(named = "judge.worker.integration", matches = "true")
    void workerCrashAfterClaimRecoversOnNewProcess() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).resolve("../..").normalize();
        assertTrue(Files.exists(root.resolve("deploy/dev/oj-judge.local.yml")));
        Process first = startWorker(root, "worker-crash-first.log");
        Process replacement = null;
        try {
            waitForWorker(first);
            jdbc.update("UPDATE tb_question SET time_limit=15000 WHERE question_id=?", questionId);
            String id =
                    submit(
                            request(
                                    "public class Solution { static int solve(int a,int b) { try { Thread.sleep(6000); } catch (InterruptedException e) { throw new RuntimeException(e); } return a+b; } }"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!"RUNNING"
                    .equals(
                            jdbc.queryForObject(
                                    "SELECT state FROM tb_judge_task WHERE submit_id=?",
                                    String.class,
                                    id))) {
                assertTrue(System.nanoTime() < deadline, "First worker did not claim task");
                Thread.sleep(100);
            }
            String firstToken =
                    jdbc.queryForObject(
                            "SELECT run_token FROM tb_judge_task WHERE submit_id=?",
                            String.class,
                            id);
            first.destroyForcibly();
            assertTrue(first.waitFor(15, TimeUnit.SECONDS));
            replacement = startWorker(root, "worker-crash-replacement.log");
            waitForWorker(replacement);
            JSONObject result = finished(id, 100);
            assertEquals("AC", result.getJSONObject("result").getString("verdict"));
            assertEquals(2, result.getIntValue("attemptCount"));
            assertEquals(
                    "LEASE_EXPIRED",
                    jdbc.queryForObject(
                            "SELECT failure_reason FROM tb_judge_attempt WHERE run_token=?",
                            String.class,
                            firstToken));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM tb_user_submit WHERE submit_id=?",
                            Integer.class,
                            id));
            assertEquals(
                    2,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM tb_judge_attempt WHERE submit_id=?",
                            Integer.class,
                            id));
            assertEquals(
                    2,
                    jdbc.queryForObject(
                            "SELECT COUNT(DISTINCT worker_id) FROM tb_judge_attempt WHERE submit_id=?",
                            Integer.class,
                            id));
        } finally {
            if (first.isAlive()) {
                first.destroyForcibly();
                first.waitFor(15, TimeUnit.SECONDS);
            }
            if (replacement != null && replacement.isAlive()) {
                replacement.destroy();
                replacement.waitFor(15, TimeUnit.SECONDS);
            }
        }
    }

    /** 启动使用测试配置和独立容器前缀的工作进程，日志写到项目目录。 */
    private Process startWorker(Path root, String log) throws Exception {
        String java =
                Path.of(
                                System.getProperty("java.home"),
                                "bin",
                                System.getProperty("os.name").startsWith("Windows")
                                        ? "java.exe"
                                        : "java")
                        .toString();
        return new ProcessBuilder(
                        java,
                        "-Dfile.encoding=UTF-8",
                        "-jar",
                        "oj-modules/oj-judge/target/oj-judge-1.0-SNAPSHOT.jar",
                        "--spring.cloud.nacos.config.enabled=false",
                        "--spring.cloud.nacos.discovery.enabled=false",
                        "--spring.config.additional-location=file:deploy/dev/oj-judge.local.yml",
                        "--sandbox.docker.name-prefix=oj-reliable-test-jdk")
                .directory(root.toFile())
                .redirectErrorStream(true)
                .redirectOutput(root.resolve(log).toFile())
                .start();
    }

    /** 同时检查进程存活及端口就绪，避免对已经启动失败的进程继续发请求。 */
    private void waitForWorker(Process process) throws Exception {
        // 全量验收时本机同时运行 Docker 和依赖服务，启动等待与任务执行预算分别计时。
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (System.nanoTime() < deadline) {
            assertTrue(process.isAlive(), "Worker exited before startup");
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("localhost", 8081), 200);
                return;
            } catch (java.io.IOException notReady) {
                Thread.sleep(200);
            }
        }
        fail("Worker startup timed out");
    }

    /** 验证比赛新提交必须报名；已受理请求在比赛截止后重放仍返回原任务和原快照。 */
    @Test
    // 未报名不允许新提交；已受理请求在比赛截止后仍返回原任务，新键则拒绝。
    void examAdmissionRequiresRegistrationAndReplaySurvivesDeadline() {
        long exam = IdWorker.getId();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        jdbc.update(
                "INSERT INTO tb_exam(exam_id,title,start_time,end_time,status,create_by,create_time) VALUES(?,'admission-test',?,?,1,1,NOW())",
                exam,
                now.minusMinutes(1),
                now.plusMinutes(10));
        jdbc.update(
                "INSERT INTO tb_exam_question(exam_question_id,exam_id,question_id,question_order,create_by,create_time,judge_question_json) "
                        + "SELECT ?,?,question_id,1,1,NOW(),JSON_OBJECT('questionId',question_id,'questionVersion',question_version,'difficulty',difficulty,'timeLimit',time_limit,'spaceLimit',space_limit,'questionCase',question_case,'mainFuc',main_fuc) FROM tb_question WHERE question_id=?",
                IdWorker.getId(),
                exam,
                questionId);
        UserSubmitDTO request = request(correctCode);
        request.setExamId(exam);
        assertEquals(3702, post("/user/question/submit", request, token).getIntValue("code"));
        jdbc.update(
                "INSERT INTO tb_user_exam(user_exam_id,user_id,exam_id,score,create_by,create_time) VALUES(?,?,?,0,1,NOW())",
                IdWorker.getId(),
                userId,
                exam);
        String id = submit(request);
        String snapshot =
                jdbc.queryForObject(
                        "SELECT snapshot_json FROM tb_judge_snapshot WHERE submit_id=?",
                        String.class,
                        id);
        jdbc.update("UPDATE tb_exam SET end_time=? WHERE exam_id=?", now.minusSeconds(1), exam);
        assertEquals(id, submit(request));
        assertEquals(
                snapshot,
                jdbc.queryForObject(
                        "SELECT snapshot_json FROM tb_judge_snapshot WHERE submit_id=?",
                        String.class,
                        id));
        request.setIdempotencyKey(UUID.randomUUID().toString());
        assertEquals(3702, post("/user/question/submit", request, token).getIntValue("code"));
    }

    /** 在任务运行时临时中断数据库，恢复后验证有限重试完成任务且历史有效结果不重复写入。 */
    @Test
    // 本例会短暂停止并重新启动指定 MySQL 容器，普通回归不启用此开关。
    @EnabledIfSystemProperty(named = "judge.outage.integration", matches = "true")
    void databaseOutageRecoversRunningTask() throws Exception {
        jdbc.update("UPDATE tb_question SET time_limit=15000 WHERE question_id=?", questionId);
        String id =
                submit(
                        request(
                                "public class Solution { static int solve(int a,int b) { try { Thread.sleep(6000); } catch (InterruptedException e) { throw new RuntimeException(e); } return a+b; } }"));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!"RUNNING"
                .equals(
                        jdbc.queryForObject(
                                "SELECT state FROM tb_judge_task WHERE submit_id=?",
                                String.class,
                                id))) {
            assertTrue(System.nanoTime() < deadline, "Worker did not claim test task");
            Thread.sleep(100);
        }
        try {
            Process stopped =
                    new ProcessBuilder("docker", "stop", "-t", "1", "oj-reliability-mysql")
                            .redirectErrorStream(true)
                            .start();
            assertTrue(stopped.waitFor(20, TimeUnit.SECONDS));
            assertEquals(0, stopped.exitValue());
            Thread.sleep(35000);
        } finally {
            Process started =
                    new ProcessBuilder("docker", "start", "oj-reliability-mysql")
                            .redirectErrorStream(true)
                            .start();
            assertTrue(started.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, started.exitValue());
        }
        JSONObject result = finished(id, 100);
        assertEquals("AC", result.getJSONObject("result").getString("verdict"));
        assertTrue(result.getIntValue("attemptCount") >= 2);
        assertTrue(result.getIntValue("attemptCount") <= 3);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM tb_user_submit WHERE submit_id=?",
                        Integer.class,
                        id));
    }

    /** 验证普通用户无权排查或恢复失败投递，管理员可保留原快照和失败历史并创建新派发代次。 */
    @Test
    @EnabledIfSystemProperty(named = "judge.admin.integration", matches = "true")
    // 注入 DEAD 状态，验证普通用户无管理权限，管理员重试保留快照、旧事件和审计。
    void adminCanInspectDeadDeliveryAndRetryWithoutChangingSnapshot() {
        String id = submit(request(correctCode));
        String snapshot =
                jdbc.queryForObject(
                        "SELECT snapshot_json FROM tb_judge_snapshot WHERE submit_id=?",
                        String.class,
                        id);
        jdbc.update(
                "UPDATE tb_judge_task SET state='SYSTEM_FAILED',failure_reason='DELIVERY_FAILED' WHERE submit_id=?",
                id);
        jdbc.update(
                "UPDATE tb_judge_outbox SET state='DEAD',send_count=5,failure_reason='PUBLISH_UNCONFIRMED' WHERE submit_id=?",
                id);
        String path = "/user/question/submissions/" + id + "/deliveries";
        assertEquals(3001, get(path, token).getIntValue("code"));
        JSONObject delivery = get(path, adminToken).getJSONArray("data").getJSONObject(0);
        assertEquals("DEAD", delivery.getString("state"));
        assertEquals(5, delivery.getIntValue("sendCount"));
        assertFalse(delivery.containsKey("payload"));
        assertEquals(3001, get("/user/question/submissions/failed", token).getIntValue("code"));
        assertTrue(
                get("/user/question/submissions/failed", adminToken).getJSONArray("data").stream()
                        .anyMatch(value -> id.equals(((JSONObject) value).getString("submitId"))));
        assertEquals(
                1000,
                post(
                                "/user/question/submissions/" + id + "/retry",
                                Map.of("reason", "Infrastructure restored"),
                                adminToken)
                        .getIntValue("code"));
        assertEquals(
                snapshot,
                jdbc.queryForObject(
                        "SELECT snapshot_json FROM tb_judge_snapshot WHERE submit_id=?",
                        String.class,
                        id));
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT dispatch_seq FROM tb_judge_task WHERE submit_id=?",
                        Integer.class,
                        id));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM tb_judge_retry_audit WHERE submit_id=?",
                        Integer.class,
                        id));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM tb_judge_outbox WHERE submit_id=? AND state='DEAD'",
                        Integer.class,
                        id));
    }

    /** 并发发送同键请求，核对仅创建一组任务、快照和事件，并验证内容冲突及他人查询被拒绝。 */
    @Test
    // 四个并发同键请求应返回同一任务；同键不同代码冲突，其他用户不可读取隐藏结果。
    void concurrentAcceptanceConflictAndOwnership() throws Exception {
        UserSubmitDTO request = request(correctCode);
        assertEquals(3001, post("/user/question/submit", request, null).getIntValue("code"));
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(executor.submit(() -> submit(request)));
            }
            Set<String> ids = new HashSet<>();
            for (Future<String> result : results) {
                ids.add(result.get(15, TimeUnit.SECONDS));
            }
            assertEquals(1, ids.size());
            String id = ids.iterator().next();
            request.setUserCode(correctCode + " ");
            assertEquals(409, post("/user/question/submit", request, token).getIntValue("code"));
            assertEquals(3003, task(id, otherToken).getIntValue("code"));
            assertEquals("AC", finished(id).getJSONObject("result").getString("verdict"));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM tb_judge_snapshot WHERE submit_id=?",
                            Integer.class,
                            id));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM tb_judge_outbox WHERE submit_id=? AND event_type='JudgeRequested'",
                            Integer.class,
                            id));
            assertNull(
                    task(id, token)
                            .getJSONObject("data")
                            .getJSONObject("result")
                            .get("userExeResultList"));
        } finally {
            executor.shutdownNow();
        }
    }

    /** 对已定榜比赛请求人工重试，确认被拒绝且任务代次、审计记录和事件数量不变。 */
    @Test
    @EnabledIfSystemProperty(named = "judge.admin.integration", matches = "true")
    // 即使是管理员，也不能重试已定榜比赛任务；状态、代次和审计记录均不能变化。
    void finalizedCompetitionCannotBeManuallyRetried() {
        String id = submit(request(correctCode));
        long exam = IdWorker.getId();
        jdbc.update(
                "INSERT INTO tb_exam(exam_id,title,start_time,end_time,status,settlement_state,create_by,create_time) "
                        + "VALUES(?,'finalized-retry-test',NOW(),NOW(),1,'FINALIZED',1,NOW())",
                exam);
        jdbc.update(
                "UPDATE tb_judge_task SET exam_id=?,state='SYSTEM_FAILED',failure_reason='DELIVERY_FAILED' WHERE submit_id=?",
                exam,
                id);
        assertEquals(
                3702,
                post(
                                "/user/question/submissions/" + id + "/retry",
                                Map.of("reason", "Infrastructure restored"),
                                adminToken)
                        .getIntValue("code"));
        assertEquals(
                "SYSTEM_FAILED",
                jdbc.queryForObject(
                        "SELECT state FROM tb_judge_task WHERE submit_id=?", String.class, id));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT dispatch_seq FROM tb_judge_task WHERE submit_id=?",
                        Integer.class,
                        id));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM tb_judge_retry_audit WHERE submit_id=?",
                        Integer.class,
                        id));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM tb_judge_outbox WHERE submit_id=?",
                        Integer.class,
                        id));
    }

    /** 让首个同键插入事务回滚，验证等待中的请求随后可完整创建任务、快照及事件。 */
    @Test
    // 首个事务占住唯一键后回滚，等待中的 HTTP 请求必须自己创建完整三件套，不能返回残留编号。
    void rolledBackFirstInsertAllowsWaitingRequestToCreateCompleteSubmission() throws Exception {
        UserSubmitDTO request = request(correctCode);
        long rolledBackId = IdWorker.getId();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        java.util.concurrent.atomic.AtomicReference<Future<String>> waiting =
                new java.util.concurrent.atomic.AtomicReference<>();
        try {
            transactionTemplate.execute(
                    status -> {
                        jdbc.update(
                                "INSERT INTO tb_judge_task(submit_id,user_id,question_id,idempotency_key,request_hash,state,queue_deadline) "
                                        + "VALUES(?,?,?,? ,?,'RECEIVED',DATE_ADD(NOW(),INTERVAL 120 SECOND))",
                                rolledBackId,
                                userId,
                                questionId,
                                request.getIdempotencyKey(),
                                "a".repeat(64));
                        waiting.set(executor.submit(() -> submit(request)));
                        assertThrows(
                                TimeoutException.class,
                                () -> waiting.get().get(500, TimeUnit.MILLISECONDS));
                        status.setRollbackOnly();
                        return null;
                    });
            String id = waiting.get().get(15, TimeUnit.SECONDS);
            assertNotEquals(Long.toString(rolledBackId), id);
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM tb_judge_task WHERE submit_id=?",
                            Integer.class,
                            rolledBackId));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM tb_judge_task WHERE user_id=? AND idempotency_key=?",
                            Integer.class,
                            userId,
                            request.getIdempotencyKey()));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM tb_judge_snapshot WHERE submit_id=?",
                            Integer.class,
                            id));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM tb_judge_outbox WHERE submit_id=?",
                            Integer.class,
                            id));
        } finally {
            executor.shutdownNow();
        }
    }

    /** 提交通过、答案错误、编译失败、运行错误及超时样例，验证有效判定直接完成且不消耗额外执行次数。 */
    @Test
    // 用真实用户代码触发各类可判定错误，均应一次执行后 FINISHED，而非当作系统异常重试。
    void validVerdictsDoNotRetry() {
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("AC", "return a+b;");
        cases.put("WA", "return a-b;");
        cases.put("CE", "return invalidSyntax;");
        cases.put("RE", "throw new RuntimeException(\"test\");");
        cases.put("TLE", "while(true) {}");
        cases.put("OLE", "while(true) { System.out.print(\"1234567890\"); }");
        // 持续保留分配触发任务组内核OOM；单个超大数组只会触发JVM自身异常，应归RE。
        cases.put(
                "MLE",
                "java.util.List<byte[]> data=new java.util.ArrayList<>(); while(true) data.add(new byte[1048576]);");
        for (Map.Entry<String, String> sample : cases.entrySet()) {
            String id =
                    submit(
                            request(
                                    "public class Solution { static int solve(int a,int b) { "
                                            + sample.getValue()
                                            + " } }"));
            JSONObject result = finished(id);
            assertEquals(
                    sample.getKey(),
                    result.getJSONObject("result").getString("verdict"),
                    result.toJSONString());
            assertEquals(1, result.getIntValue("attemptCount"));
            assertNotNull(result.getJSONObject("result").getJSONArray("stageEvidence"));
            assertFalse(result.getJSONObject("result").getJSONArray("stageEvidence").isEmpty());
        }
    }

    /** 验证已完成任务不能人工重试，题目更新后原幂等请求仍指向原快照，而新请求使用新版本校验。 */
    @Test
    // 修改题目版本与限制后，原键仍映射旧快照；新键必须携带新版本，已完成任务不可人工重试。
    void acceptedReplaySurvivesQuestionChangeAndFinishedCannotRetry() {
        UserSubmitDTO request = request(correctCode);
        String id = submit(request);
        finished(id);
        assertEquals(
                3001,
                post("/user/question/submissions/" + id + "/retry", Map.of("reason", "test"), token)
                        .getIntValue("code"));
        assertEquals(
                3702,
                post(
                                "/user/question/submissions/" + id + "/retry",
                                Map.of("reason", "test"),
                                adminToken)
                        .getIntValue("code"));
        String snapshot =
                jdbc.queryForObject(
                        "SELECT snapshot_json FROM tb_judge_snapshot WHERE submit_id=?",
                        String.class,
                        id);
        jdbc.update(
                "UPDATE tb_question SET question_version=2,time_limit=3000 WHERE question_id=?",
                questionId);
        try {
            assertEquals(id, submit(request));
            assertEquals(
                    snapshot,
                    jdbc.queryForObject(
                            "SELECT snapshot_json FROM tb_judge_snapshot WHERE submit_id=?",
                            String.class,
                            id));
            assertEquals(
                    409,
                    post("/user/question/submit", request(correctCode), token).getIntValue("code"));
            UserSubmitDTO fresh = request(correctCode);
            fresh.setQuestionVersion(2L);
            String freshId = submit(fresh);
            JSONObject freshSnapshot =
                    JSON.parseObject(
                            jdbc.queryForObject(
                                    "SELECT snapshot_json FROM tb_judge_snapshot WHERE submit_id=?",
                                    String.class,
                                    freshId));
            assertEquals(2, freshSnapshot.getIntValue("questionVersion"));
            assertEquals(3000, freshSnapshot.getLongValue("timeLimit"));
        } finally {
            jdbc.update(
                    "UPDATE tb_question SET question_version=1,time_limit=2000 WHERE question_id=?",
                    questionId);
        }
    }
}
