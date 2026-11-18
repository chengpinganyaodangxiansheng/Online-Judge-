package org.example.friend.service.user.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.example.api.domain.dto.JudgeRequestedDTO;
import org.example.api.domain.dto.JudgeSubmitDTO;
import org.example.api.domain.vo.JudgeTaskVO;
import org.example.api.domain.vo.UserQuestionResultVO;
import org.example.common.core.constants.Constants;
import org.example.common.core.enums.ResultCode;
import org.example.common.core.utils.ThreadLocalUtil;
import org.example.common.mybatis.domain.JudgeTask;
import org.example.common.mybatis.domain.JudgeOutbox;
import org.example.common.mybatis.mapper.JudgeTaskMapper;
import org.example.common.mybatis.mapper.JudgeOutboxMapper;
import org.example.common.mybatis.mapper.JudgeScoreMapper;
import org.example.common.redis.service.RedisService;
import org.example.common.core.domain.LoginUser;
import org.example.common.core.constants.CacheConstants;
import org.example.common.core.enums.UserIdentity;
import org.example.common.security.exception.ServiceException;
import org.example.friend.domain.question.Question;
import org.example.friend.domain.question.QuestionCase;
import org.example.friend.domain.user.dto.UserSubmitDTO;
import org.example.friend.mapper.user.JudgeAdmissionMapper;
import org.example.friend.mapper.user.JudgeInspectionMapper;
import org.example.friend.domain.user.vo.JudgeDeliveryVO;
import org.example.friend.service.user.JudgeSubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 提交受理业务：校验提交资格，将任务、固定题目快照和待发送事件一起落库。
 * 此处不执行用户代码；Judge 服务消费事件后执行，客户端凭提交编号查询进度。
 */
@Service
public class JudgeSubmissionServiceImpl implements JudgeSubmissionService {
    /** 任务数据库访问对象，用于查询和条件推进可靠判题任务。 */
    @Autowired
    private JudgeTaskMapper taskMapper;

    /** 本地消息表访问对象，用于核对事件领取、重发及发布状态。 */
    @Autowired
    private JudgeOutboxMapper outboxMapper;

    /** 受理校验的数据访问对象，用于锁定业务行、读取冻结题目及检查报名关系。 */
    @Autowired
    private JudgeAdmissionMapper admissionMapper;

    /** 短事务执行模板，使一次业务操作中的多项数据库写入共同提交或回滚。 */
    @Autowired
    private TransactionTemplate transactionTemplate;

    /** 成绩持久化访问对象，负责比赛锁、单题最高分合并和用户总分更新。 */
    @Autowired
    private JudgeScoreMapper scoreMapper;

    /** 登录缓存访问服务，用于取得已认证用户的身份信息并检查管理权限。 */
    @Autowired
    private RedisService redisService;

    /** 管理排查数据访问对象，读取失败任务与不含完整载荷的投递摘要。 */
    @Autowired
    private JudgeInspectionMapper inspectionMapper;

    /** 每轮等待执行的期限，单位为秒，覆盖消息投递、队列积压和本地资源等待。 */
    @Value("${judge.task.queue-seconds:120}")
    // 本轮等待执行的期限，默认 120 秒；覆盖 Outbox、MQ 和资源等待，不是用户代码运行时限。
    private int queueSeconds;

    /** 受理时允许的用户源码最大字节数，避免超大请求进入数据库。 */
    @Value("${judge.task.max-code-bytes:65536}")
    private int maxCodeBytes;

    /** 受理时允许的最大用例数量，限制快照和执行请求规模。 */
    @Value("${judge.task.max-cases:100}")
    private int maxCases;

    /**
     * 受理当前用户的提交；同一幂等键、相同内容返回原任务，不重复创建。
     * @param request 原始代码、题目/比赛信息及客户端生成的幂等键
     * @return 已持久化任务的公开视图，返回成功不代表判题已经完成
     */
    @Override
    public JudgeTaskVO submit(UserSubmitDTO request) {
        Long userId = currentUser();
        // 以下是“任一条件不满足就拒绝”的参数校验：
        // 1. 必须指定题目及语言，当前可靠判题只支持 programType=0（Java）。
        // 2. 幂等键必须存在且为 8～64 位字母、数字、下划线或连字符，用于识别同一次提交。
        // 3. 原始代码不能为 null 或全空白，UTF-8 字节数不能超过配置上限，防止过大请求入库。
        // || 从左向右短路，null 检查通过后才会调用 matches/isBlank/getBytes，避免空指针。
        if (request.getQuestionId() == null || request.getProgramType() == null
                || request.getProgramType() != 0 || request.getIdempotencyKey() == null
                || !request.getIdempotencyKey().matches("[A-Za-z0-9_-]{8,64}")
                || request.getUserCode() == null || request.getUserCode().isBlank()
                || request.getUserCode().getBytes(StandardCharsets.UTF_8).length > maxCodeBytes) {
            throw new ServiceException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        // 哈希使用原始代码且不包含题目版本，已受理请求在题目更新后仍可重放原任务。
        String hash = DigestUtil.sha256Hex(JSON.toJSONString(Arrays.asList(request.getQuestionId(), request.getExamId(),
                                        request.getProgramType(), request.getUserCode())));
        // 按“用户＋幂等键”查询，不是按代码或哈希查询；找到后仍需 replay 核对内容。
        // 新提交应使用新键；客户端 Bug 或直接调用接口仍可能把旧键与新代码一起传来。
        JudgeTask original = taskMapper.findRequest(userId, request.getIdempotencyKey());
        if (original != null) {
            return replay(original, hash);
        }
        try {
            // accept 正常返回后，execute 还要提交事务，提交成功后才向调用者返回任务。
            // 回调抛出 RuntimeException/Error 时，事务模板通过事务管理器回滚，再向外抛异常；
            // 这里不必手动 rollback。若在回调内吞掉异常，则不能指望模板自动知道需要回滚。
            // 返回 null/false 或 SQL 影响 0 行也不自动代表回滚，须由业务抛错或标记 rollbackOnly。
            return transactionTemplate.execute(status -> accept(request, userId, hash));
        } catch (DuplicateKeyException exception) {
            // 并发示例：A、B 都查不到同键任务；A 先插入，B 的插入等待 A 的事务结果。
            // A 提交后 B 遇到唯一键冲突；若 A 回滚，B 可能继续插入成功，不会必然走到这里。
            // B 创建失败不等于用户的提交不存在：A 可能已经创建成功，因此允许核实并返回 A 的任务。
            // 只能恢复用户幂等键冲突；当前创建事务已由 TransactionTemplate 完整回滚。
            // 此顺序基于当前 Controller -> submit 没有外层事务；后续若增加外层事务须重新审查。
            // DuplicateKeyException 也可能是 submit_id 主键等其他唯一约束冲突，不能一概恢复。
            // uk_judge_user_key 是建表 SQL 中 UNIQUE(user_id,idempotency_key) 的索引名称。
            // getMostSpecificCause 取得底层原因；当前实现依赖其错误消息中的索引名识别冲突。
            // 错误消息缺失，或冲突约束不是 uk_judge_user_key，都无法证明是重复提交，必须继续抛错。
            // 先判 null 也避免调用 null.contains；|| 短路使消息为空时不再执行右侧。
            if (exception.getMostSpecificCause().getMessage() == null
                    || !exception.getMostSpecificCause()
                            .getMessage()
                            .contains("uk_judge_user_key")) {
                throw exception;
            }
            // 异常没有提供原任务和请求内容；回滚后重新读取，才能取得 submitId 并校验哈希。
            // 查询自身若断连等报错，会继续向外传播；不能伪装成受理成功。
            JudgeTask existing = taskMapper.findRequest(userId, request.getIdempotencyKey());
            // 查不到可确认的原任务，保留原异常；查到也必须通过 replay 的内容核对。
            if (existing == null) {
                throw exception;
            }
            return replay(existing, hash);
        }
    }

    /** 在 submit 创建的短事务内校验并落库；任何一步失败都不留下半份提交。
     * */
    private JudgeTaskVO accept(UserSubmitDTO request, Long userId, String hash) {
        // examId 非空才走比赛检查；查询返回 null 表示比赛不存在、未发布、未开始、已截止或不再 OPEN。
        // 先查是否已有同键任务：已受理请求即使重试时比赛截止，仍应返回原任务；只有新请求被拒绝。
        if (request.getExamId() != null && admissionMapper.lockOpenExam(request.getExamId()) == null) {
            // 从前置查询到这里之间，另一并发请求可能已受理同键任务，因此拒绝新请求前再查。
            // 此分支不表示一定发生过锁等待；正常提交之间的比赛共享锁彼此兼容。
            JudgeTask previous = taskMapper.findRequest(userId, request.getIdempotencyKey());
            if (previous != null) {
                return replay(previous, hash);
            }
            throw new ServiceException(ResultCode.SUBMIT_NOT_ALLOWED);
        }
        // 练习读取并锁定当前题目；比赛使用发布时冻结的题目，避免赛中编辑改变判题规则。
        Question question = request.getExamId() == null ? admissionMapper.lockQuestion(request.getQuestionId())
                        : JSON.parseObject(admissionMapper.frozenQuestion(request.getExamId(), request.getQuestionId()), Question.class);
        // 再查用于发现并发受理结果，仍不能代替唯一约束：两个请求依然可能同时查不到。
        JudgeTask previous = taskMapper.findRequest(userId, request.getIdempotencyKey());
        if (previous != null) {
            return replay(previous, hash);
        }
        // 拒绝条件分两层：
        // 1. question 为 null：练习题不存在，或比赛没有可用的冻结题目快照，两种提交都拒绝。
        // 2. 仅当 examId 非空时检查参赛关系：未恰好报名一次，或者题目未恰好关联本场一次，任一失败即拒绝。
        // 因而比赛要求“题目快照存在 AND 已报名 AND 题目属于本场”；练习不需要报名和比赛归属检查。
        if (question == null || (request.getExamId() != null && (admissionMapper.registered(request.getExamId(), userId) != 1
                || admissionMapper.containsQuestion(request.getExamId(), request.getQuestionId()) != 1))) {
            throw new ServiceException(ResultCode.SUBMIT_NOT_ALLOWED);
        }
        List<QuestionCase> cases = JSON.parseArray(question.getQuestionCase(), QuestionCase.class);
        // 仅新受理的练习请求比较页面版本与当前版本，不一致要求刷新；比赛已由冻结快照决定版本。
        // 前面已处理原任务重放，所以题目版本变化不会让已受理的同键请求失效。
        if (request.getExamId() == null && !Objects.equals(question.getQuestionVersion(), request.getQuestionVersion())) {
            throw new ServiceException(ResultCode.QUESTION_VERSION_CONFLICT);
        }
        // 判题资料不完整时不创建任务：用例必须存在且数量为 1～maxCases；入口方法、时间/空间限制及难度不能缺失。
        // 此处只检查这些字段是否缺失；数值是否大于 0、输入输出是否配对由 Judge 执行前的 validSnapshot 再校验。
        if (cases == null || cases.isEmpty() || cases.size() > maxCases
                || question.getMainFuc() == null || question.getTimeLimit() == null
                || question.getSpaceLimit() == null || question.getDifficulty() == null) {
            throw new ServiceException(ResultCode.SUBMIT_NOT_ALLOWED);
        }
        int insertion = request.getUserCode().lastIndexOf('}');
        if (insertion < 0) {
            throw new ServiceException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        // 在服务端拼入入口方法及隐藏用例；执行与重试只读取这份快照，不再读取最新题目。
        JudgeSubmitDTO snapshot = new JudgeSubmitDTO();
        BeanUtil.copyProperties(question, snapshot);
        snapshot.setUserId(userId);
        snapshot.setExamId(request.getExamId());
        snapshot.setProgramType(request.getProgramType());
        snapshot.setUserCode(request.getUserCode().substring(0, insertion)
                        + "\n"
                        + question.getMainFuc()
                        + "\n"
                        + request.getUserCode().substring(insertion));
        snapshot.setInputList(cases.stream().map(QuestionCase::getInput).toList());
        snapshot.setOutputList(cases.stream().map(QuestionCase::getOutput).toList());
        // 与可信执行器协议的硬限额一致，避免受理后才因无法执行而消耗系统重试预算。
        if (snapshot.getInputList().size() > 128 || snapshot.getTimeLimit() < 1
                || snapshot.getTimeLimit() > 60000 || snapshot.getSpaceLimit() < 16384
                || snapshot.getSpaceLimit() > 262144 || snapshot.getUserCode().getBytes(StandardCharsets.UTF_8).length > 262144
                || snapshot.getInputList().stream().anyMatch(
                        input -> input != null && input.getBytes(StandardCharsets.UTF_8).length > 16384)
                || snapshot.getOutputList().stream().anyMatch(output -> output == null
                || output.getBytes(StandardCharsets.UTF_8).length > 65536)) {
            throw new ServiceException(ResultCode.SUBMIT_NOT_ALLOWED);
        }
        JudgeTask task = new JudgeTask();
        task.setSubmitId(IdWorker.getId());
        task.setUserId(userId);
        task.setQuestionId(request.getQuestionId());
        task.setExamId(request.getExamId());
        task.setIdempotencyKey(request.getIdempotencyKey());
        task.setRequestHash(hash);
        // 三项写入共用事务：成功提交的任务具有快照和待发送事件；任何一项写入异常均回滚。
        // snapshot 此前仅在内存组装，现在才落库；含服务端入口、用例和限制，不只是原始代码。
        // insert 用数据库 NOW(6)+queueSeconds 计算 queue_deadline，领取时检查是否已经超时。
        // 此处只保存待投递事件，不访问 RabbitMQ，也不等待判题执行。
        taskMapper.insert(task, queueSeconds);
        taskMapper.snapshot(task.getSubmitId(), JSON.toJSONString(snapshot));
        JudgeRequestedDTO message = new JudgeRequestedDTO();
        message.setEventId(UUID.randomUUID().toString());
        message.setSubmitId(task.getSubmitId());
        message.setDispatchSeq(1);
        JudgeOutbox event = new JudgeOutbox();
        BeanUtil.copyProperties(message, event);
        event.setPayload(JSON.toJSONString(message));
        outboxMapper.insert(event);
        return view(taskMapper.find(task.getSubmitId()));
    }

    /** 仅允许查询本人任务；不存在和不属于本人使用相同错误，避免暴露其他用户的提交。 */
    @Override
    public JudgeTaskVO find(Long submitId) {
        Long userId = currentUser();
        JudgeTask task = taskMapper.find(submitId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new ServiceException(ResultCode.FAILED_NOT_EXISTS);
        }
        return view(task);
    }

    /** 从认证上下文取用户身份，不接受请求体指定的用户编号。 */
    private Long currentUser() {
        Long userId = ThreadLocalUtil.get(Constants.USER_ID, Long.class);
        if (userId == null) {
            throw new ServiceException(ResultCode.FAILED_UNAUTHORIZED);
        }
        return userId;
    }

    /** 管理员重试系统失败任务；保留原快照和历史 attempt，并在同一事务记录重试原因。 */
    @Override
    public JudgeTaskVO retry(Long submitId, String reason) {
        Long operator = requireAdmin();
        // 重试原因必须可追溯：既不能未提供/全空白，也不能超过审计字段允许的 255 个字符。
        if (reason == null || reason.isBlank() || reason.length() > 255) {
            throw new ServiceException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        return transactionTemplate.execute(
                status -> {
                    JudgeTask task = taskMapper.find(submitId);
                    if (task == null) {
                        throw new ServiceException(ResultCode.FAILED_NOT_EXISTS);
                    }
                    if (task.getExamId() != null) {
                        String state = scoreMapper.lockExam(task.getExamId());
                        // 比赛不存在就没有合法计分归属；已 FINALIZED 则不能通过重试改变已定榜结果。
                        if (state == null || "FINALIZED".equals(state)) {
                            throw new ServiceException(ResultCode.SUBMIT_NOT_ALLOWED);
                        }
                    }
                    // 与完成判题保持比赛行在前、任务行在后的锁顺序，已定榜比赛禁止重试。
                    task = taskMapper.lock(submitId);
                    if (taskMapper.manualRetry(submitId, queueSeconds) != 1) {
                        throw new ServiceException(ResultCode.SUBMIT_NOT_ALLOWED);
                    }
                    // 新派发代次使旧消息失效；数据库同时重置本轮自动重试预算。
                    int seq = task.getDispatchSeq() + 1;
                    JudgeRequestedDTO message = new JudgeRequestedDTO();
                    message.setSubmitId(submitId);
                    message.setDispatchSeq(seq);
                    message.setEventId(UUID.randomUUID().toString());
                    JudgeOutbox event = new JudgeOutbox();
                    BeanUtil.copyProperties(message, event);
                    event.setPayload(JSON.toJSONString(message));
                    outboxMapper.insert(event);
                    taskMapper.auditRetry(submitId, operator, reason, seq);
                    return view(taskMapper.find(submitId));
                });
    }

    /** 管理员分页式排查入口，限制单次返回量，避免一次读取全部失败任务。 */
    @Override
    public List<JudgeTaskVO> failed(int limit) {
        requireAdmin();
        if (limit < 1 || limit > 100) {
            throw new ServiceException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        return inspectionMapper.failed(limit).stream().map(this::view).toList();
    }

    /** 管理员查看任务的投递状态，用于区分执行失败与消息投递失败。 */
    @Override
    public List<JudgeDeliveryVO> deliveries(Long submitId) {
        requireAdmin();
        if (taskMapper.find(submitId) == null) {
            throw new ServiceException(ResultCode.FAILED_NOT_EXISTS);
        }
        return inspectionMapper.deliveries(submitId);
    }

    /** 依据登录缓存中的身份鉴权，不能仅凭调用者传入的编号执行管理操作。 */
    private Long requireAdmin() {
        // 线程上下文提供当前登录身份，再从登录缓存读取权限；不能相信请求体自报的管理员编号。
        Long operator = currentUser();
        String key = ThreadLocalUtil.get(Constants.USER_KEY, String.class);
        LoginUser login =
                key == null
                        ? null
                        : redisService.getCacheObject(
                                CacheConstants.LOGIN_TOKEN_KEY + key, LoginUser.class);
        // 登录缓存不存在（含过期）或身份不是管理员，都拒绝管理操作；有用户编号不等于拥有管理员权限。
        if (login == null || !UserIdentity.ADMIN.getValue().equals(login.getIdentity())) {
            throw new ServiceException(ResultCode.FAILED_UNAUTHORIZED);
        }
        return operator;
    }

    /**
     * 幂等键相同而内容不同是冲突，不能把另一份代码的任务当作本次提交成功。
     * 例如 K1+代码A 超时后，错误客户端用 K1+代码B 重试，就必须拒绝；正确重试应保持原内容。
     * 同内容返回原任务当前公开状态及已有结果，不是返回原请求体，也不重新创建任务。
     */
    private JudgeTaskVO replay(JudgeTask task, String hash) {
        if (!hash.equals(task.getRequestHash())) {
            throw new ServiceException(ResultCode.SUBMIT_CONFLICT);
        }
        return view(task);
    }

    /** 组装公开结果；长整型编号转字符串，防止浏览器数字精度丢失。 */
    private JudgeTaskVO view(JudgeTask task) {
        // 数据库对象包含内部信息，不能直接作为接口响应；这里只挑选用户需要的状态和结果。
        // 提交编号转为字符串，避免浏览器把长整数截断精度后查询错任务。
        JudgeTaskVO view = new JudgeTaskVO();
        view.setSubmitId(task.getSubmitId().toString());
        view.setState(task.getState());
        view.setAttemptCount(task.getAttemptCount()); // 执行尝试次数。
        view.setFailureReason(task.getFailureReason());
        if (task.getResultJson() != null) {
            UserQuestionResultVO result = JSON.parseObject(task.getResultJson(), UserQuestionResultVO.class);
            // 隐藏用例和期望输出只存数据库，不通过公开任务接口泄露。
            result.setUserExeResultList(null);
            view.setResult(result);
        }
        return view;
    }
}
