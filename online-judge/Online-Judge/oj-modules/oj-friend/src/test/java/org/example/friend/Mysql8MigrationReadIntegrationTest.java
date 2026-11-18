package org.example.friend;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.example.common.security.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 迁移后的业务库只读验收，显式启用；临时登录令牌仅用于鉴权，结束时删除。 */
@EnabledIfSystemProperty(named = "mysql.migration.integration", matches = "true")
@SpringBootTest(
        classes = OFriendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false"
        })
class Mysql8MigrationReadIntegrationTest {
    /** 测试用数据库访问工具，用于准备测试数据并核对持久化结果。 */
    @Autowired JdbcTemplate jdbc;

    /** 测试用接口客户端，用于验证真实请求、鉴权及响应结构。 */
    @Autowired TestRestTemplate http;

    /** 测试登录凭证服务，用于构造不同用户或管理员的认证身份。 */
    @Autowired TokenService tokens;

    /** 测试环境的登录凭证签名配置，仅用于构造鉴权测试请求，不应写入日志。 */
    @Value("${jwt.secret}")
    String secret;

    /** 留空访问本测试启动的随机端口；配置地址时可核验外部已启动的真实 Friend 服务。 */
    @Value("${mysql.migration.http-base-url:}")
    String httpBaseUrl;

    /** 在迁移后的数据库上通过鉴权接口读取题目及版本，核对提交和任务数量不变，验证读取兼容性。 */
    @Test
    // 对照 JDBC 读取值逐题校验 HTTP 返回的中文标题和版本，并确认没有新增提交或任务。
    void migratedQuestionsRemainReadableThroughAuthenticatedHttpWithoutWritingBusinessData() {
        assertEquals("bitoj_dev", jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertTrue(jdbc.queryForObject("SELECT VERSION()", String.class).startsWith("8.4."));
        List<Map<String, Object>> questions =
                jdbc.queryForList(
                        "SELECT question_id,title,question_version FROM tb_question ORDER BY question_id");
        assertFalse(questions.isEmpty());
        Long userId = jdbc.queryForObject("SELECT MIN(user_id) FROM tb_user", Long.class);
        assertNotNull(userId);
        Long originalSubmissions =
                jdbc.queryForObject("SELECT COUNT(*) FROM tb_user_submit", Long.class);
        Long originalTasks = jdbc.queryForObject("SELECT COUNT(*) FROM tb_judge_task", Long.class);
        String token = tokens.createToken(userId, secret, 1, "migration-read-check", null);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            for (Map<String, Object> question : questions) {
                String response =
                        http.exchange(
                                        httpBaseUrl
                                                + "/question/detail?questionId="
                                                + question.get("question_id"),
                                        HttpMethod.GET,
                                        new HttpEntity<>(headers),
                                        String.class)
                                .getBody();
                JSONObject body = JSON.parseObject(response);
                assertEquals(1000, body.getIntValue("code"));
                assertEquals(question.get("title"), body.getJSONObject("data").getString("title"));
                assertEquals(
                        ((Number) question.get("question_version")).longValue(),
                        body.getJSONObject("data").getLongValue("questionVersion"));
            }
            assertEquals(
                    originalSubmissions,
                    jdbc.queryForObject("SELECT COUNT(*) FROM tb_user_submit", Long.class));
            assertEquals(
                    originalTasks,
                    jdbc.queryForObject("SELECT COUNT(*) FROM tb_judge_task", Long.class));
        } finally {
            tokens.deleteLoginUser(token, secret);
        }
    }
}
