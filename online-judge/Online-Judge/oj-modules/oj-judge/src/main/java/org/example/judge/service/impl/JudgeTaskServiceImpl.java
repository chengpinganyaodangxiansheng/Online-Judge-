package org.example.judge.service.impl;

import com.alibaba.fastjson2.JSON;
import cn.hutool.core.bean.BeanUtil;
import org.example.api.domain.dto.JudgeRequestedDTO;
import org.example.api.domain.dto.JudgeSubmitDTO;
import org.example.api.domain.vo.UserQuestionResultVO;
import org.example.common.mybatis.domain.JudgeTask;
import org.example.common.mybatis.domain.JudgeOutbox;
import org.example.common.mybatis.mapper.JudgeTaskMapper;
import org.example.common.mybatis.mapper.JudgeOutboxMapper;
import org.example.common.mybatis.mapper.JudgeScoreMapper;
import org.example.api.domain.dto.RankChangedDTO;
import org.example.judge.domain.UserSubmit;
import org.example.judge.mapper.UserSubmitMapper;
import org.example.judge.service.JudgeTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 判题任务的数据库状态迁移。每个入口使用短事务，Docker 执行由消费者在事务外完成。
 * 方法内相互调用复用入口事务；状态是否可迁移最终由 Mapper 的条件更新决定。
 */
@Service
public class JudgeTaskServiceImpl implements JudgeTaskService {
    /** 任务数据库访问对象，用于查询和条件推进可靠判题任务。 */
    @Autowired
    private JudgeTaskMapper taskMapper;

    /** 本地消息表访问对象，用于核对事件领取、重发及发布状态。 */
    @Autowired
    private JudgeOutboxMapper outboxMapper;

    /** 历史提交结果访问对象，在完成事务中写入兼容的判题历史记录。 */
    @Autowired
    private UserSubmitMapper submitMapper;

    /** 成绩持久化访问对象，负责比赛锁、单题最高分合并和用户总分更新。 */
    @Autowired
    private JudgeScoreMapper scoreMapper;

    /** 执行租约的续期长度，单位为秒，续租仍受固定保护期限约束。 */
    @Value("${judge.task.lease-seconds:30}")
    private int leaseSeconds;

    /** 每轮等待执行的期限，单位为秒，覆盖消息投递、队列积压和本地资源等待。 */
    @Value("${judge.task.queue-seconds:120}")
    private int queueSeconds;

    /** 一轮自动执行预算的上限，包含首次成功领取后的执行尝试。 */
    @Value("${judge.task.max-attempts:3}")
    private int maxAttempts;

    /** 单轮执行的固定保护期限，单位为秒，不随续租无限延长。 */
    @Value("${judge.task.protection-seconds:600}")
    private int protectionSeconds;

    /** 当前判题进程随机生成的执行来源标识，写入任务与尝试历史；不同于资源池的稳定节点标识。 */
    private final String workerId = UUID.randomUUID().toString();

    /** 验证事件归属并处理过期状态；返回 true 仅表示可尝试领取，不代表已经拥有执行权。 */
    @Override
    @Transactional
    public boolean ready(JudgeRequestedDTO message) {
        JudgeOutbox event = outboxMapper.find(message.getEventId());
        // 三项任一不符就拒绝：事件必须真实存在，且必须属于消息指定的任务及派发代次。
        // 不能仅凭消息中的 submitId 执行，否则错误事件可能触发无关任务；短路检查先排除 null。
        if (event == null || !event.getSubmitId().equals(message.getSubmitId())
                || !event.getDispatchSeq().equals(message.getDispatchSeq())) {
            throw new IllegalArgumentException("派发事件不存在或与任务及代次不匹配");
        }
        // 尝试终结排队超时任务。
        taskMapper.expireQueue(message.getSubmitId());
        // 再查任务。
        JudgeTask task = taskMapper.find(message.getSubmitId());
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        // 重复消息遇到失效执行时，先持久化恢复安排；有效租约不会被条件更新推进。
        // 若读到运行中任务，顺带进行恢复检查。
        if ("RUNNING".equals(task.getState())) {
            recover(task.getSubmitId());
        }
        // 只有待领取状态且代次相同才允许继续等资源。RUNNING 的重复消息即使触发恢复，
        // 此处仍返回 false；新一轮执行由恢复事务生成的新代次事件触发，不复用旧消息。
        return "RECEIVED".equals(task.getState()) && task.getDispatchSeq().equals(message.getDispatchSeq());
    }

    /** 资源就绪后竞争执行权；成功则同事务创建 attempt，竞争失败返回 null。 */
    @Override
    @Transactional
    public JudgeTask claim(JudgeRequestedDTO message) {
        JudgeOutbox event = outboxMapper.find(message.getEventId());
        // ready 只是之前的一次预检；拿到容器后仍须核对事件存在、任务归属、派发代次。
        // 随后的 claim SQL 再原子检查任务状态及截止时间，不能依赖预检结果直接执行。
        if (event == null || !event.getSubmitId().equals(message.getSubmitId())
                || !event.getDispatchSeq().equals(message.getDispatchSeq())) {
            throw new IllegalArgumentException("派发事件不存在或与任务及代次不匹配");
        }
        taskMapper.expireQueue(message.getSubmitId());
        // 每次执行生成不同令牌，旧进程即使恢复运行，也不能提交新一轮任务的结果。
        String token = UUID.randomUUID().toString();
        // 这里的task任务的状态变成了RUNNING
        if (taskMapper.claim(message.getSubmitId(), message.getDispatchSeq(), token, workerId, leaseSeconds, protectionSeconds) != 1) {
            return null;
        }
        taskMapper.startAttempt(message.getSubmitId());
        return taskMapper.find(message.getSubmitId());
    }

    /**
     * 原子提交终态、历史记录、比赛成绩及排名事件；其中任一步异常均回滚。
     * @return 当前执行权仍有效且结果已写入时为 true；过期或重复完成返回 false
     */
    @Override
    @Transactional
    public boolean finish(JudgeTask task, UserQuestionResultVO result) {
        if (task.getExamId() != null) {
            String examState = scoreMapper.lockExam(task.getExamId());
            // 不存在的比赛无法计分；FINALIZED 已定榜不能再改成绩。
            // 此处不按比赛结束时间拒绝：截止前已受理的任务允许在截止后完成。
            if (examState == null || "FINALIZED".equals(examState)) {
                return false;
            }
        }
        // 先以状态、令牌和有效期拦截重复/迟到结果，成功后才允许写历史记录和计分。
        if (taskMapper.finish(task.getSubmitId(), task.getRunToken(), JSON.toJSONString(result)) != 1) {
            return false;
        }
        JudgeSubmitDTO snapshot = JSON.parseObject(taskMapper.snapshotJson(task.getSubmitId()), JudgeSubmitDTO.class);
        UserSubmit submission = new UserSubmit();
        BeanUtil.copyProperties(snapshot, submission);
        BeanUtil.copyProperties(result, submission);
        submission.setSubmitId(task.getSubmitId());
        submission.setCreateBy(task.getUserId());
        submission.setCreateTime(LocalDateTime.now());
        // 完整用例诊断保存在任务结果，兼容旧表的1000字符字段。
        submission.setCaseJudgeRes(null);
        if (submission.getExeMessage() != null && submission.getExeMessage().length() > 500) {
            submission.setExeMessage(submission.getExeMessage().substring(0, 500));
        }
        submitMapper.insert(submission);
        if (task.getExamId() != null) {
            // 锁定用户比赛总分后合并单题最高分，防止不同题同时完成时相互覆盖总分。
            Integer oldTotal = scoreMapper.lockScore(task.getExamId(), task.getUserId());
            if (oldTotal == null) {
                throw new IllegalStateException("缺少比赛报名记录");
            }
            scoreMapper.aggregate(task.getSubmitId(), result.getScore());
            long total = scoreMapper.total(task.getExamId(), task.getUserId());
            // 总分超过 INT 容量会溢出；总分低于原值违背“单题最高分只增不减”的约定。
            // 任一异常都抛错回滚整个完成事务，不能留下已完成任务和错误成绩。
            if (total > Integer.MAX_VALUE || total < oldTotal) {
                throw new IllegalStateException("成绩汇总结果无效");
            }
            scoreMapper.updateTotal(task.getExamId(), task.getUserId(), total);
            // 仅总分增长才产生排名事件，载荷使用绝对总分，便于后续幂等投影。
            if (total > oldTotal) {
                RankChangedDTO event = new RankChangedDTO();
                event.setExamId(task.getExamId());
                event.setUserId(task.getUserId());
                event.setTotalScore(total);
                outboxMapper.rankChanged(UUID.randomUUID().toString(), task.getSubmitId(), JSON.toJSONString(event));
            }
        }
        taskMapper.endAttempt(task.getRunToken(), result.getVerdict(), null);
        return true;
    }

    /**
     * 将系统异常转为有限重试或最终失败；用户代码的 WA/CE 等结果走 finish。
     * expired 区分过期补偿与仍持有租约的执行方报错，防止两者相互覆盖。
     */
    @Override
    @Transactional
    public void fail(JudgeTask task, String reason, boolean expired) {
        // 人工重试只重置本轮预算，不删除累计尝试次数；坏快照重复执行也无法修复，直接终结。
        int attempts = task.getAttemptCount() - (task.getRetryBase() == null ? 0 : task.getRetryBase());
        // 本轮次数耗尽 OR 快照不可用，任一成立都最终失败；否则进入 RETRY_WAIT 等待退避。
        String state = attempts >= maxAttempts || "INVALID_SNAPSHOT".equals(reason)
                        ? "SYSTEM_FAILED"
                        : "RETRY_WAIT";
        // 退避按累计 attemptCount 指数增长：2、4、8、16、30 秒，之后封顶 30 秒。
        // 内层 min 限制移位位数，外层 min 限制实际等待；人工重试不清空累计次数。
        int backoff = Math.min(30, 1 << Math.min(task.getAttemptCount(), 5));
        if (taskMapper.failAttempt(task.getSubmitId(), task.getRunToken(),
                        state, reason, queueSeconds, backoff, expired) == 1) {
            taskMapper.endAttempt(task.getRunToken(), "SYSTEM_FAILED", reason);
        } else if (!expired) {
            // 执行方更新失败可能是租约刚过期，转交带行锁的恢复逻辑重新判断。
            // 已经是过期补偿时不再递归恢复，避免失效条件不满足时相互调用不结束。
            // 租约刚过期时也先持久化补偿安排，再允许消费者确认消息。
            recover(task.getSubmitId());
        }
    }

    /** 扫描或重复消息触发补偿；锁定任务后检查实际状态，避免多个扫描器重复派发。 */
    @Override
    @Transactional
    public void recover(Long submitId) {
        JudgeTask task = taskMapper.lock(submitId);
        // 任务不存在直接返回；若待领取/重试等待任务已被 SQL 标记排队超时，也不再安排自动重试。
        // 任务不存在或排队超时已终结，直接返回。
        if (task == null || taskMapper.expireQueue(submitId) == 1) {
            // taskMapper.expireQueue 如果是RETRY_WAIT状态先判定是否已经超时；如果已经超时，就结束任务，不再安排重试
            return;
        }
        // RUNNING 分支是否真的过期仍由 failAttempt 的数据库时间条件裁决。
        // RETRY_WAIT 还必须满足 SQL 中“代次相同、退避到点、排队未超时”，且成功更新一行，才新建事件。
        if ("RUNNING".equals(task.getState())) {
            fail(task, "LEASE_EXPIRED", true);
        } else if ("RETRY_WAIT".equals(task.getState()) && taskMapper.dispatch(submitId, task.getDispatchSeq()) == 1) {
            // 状态回到 RECEIVED、派发代次递增、新事件入库必须一起提交。
            JudgeRequestedDTO message = new JudgeRequestedDTO();
            message.setEventId(UUID.randomUUID().toString());
            message.setSubmitId(submitId);
            message.setDispatchSeq(task.getDispatchSeq() + 1);
            JudgeOutbox event = new JudgeOutbox();
            BeanUtil.copyProperties(message, event);
            event.setPayload(JSON.toJSONString(message));
            outboxMapper.insert(event);
        }
    }
}
