package org.example.common.mybatis.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 可靠判题任务的持久化视图，同一提交跨多次派发和执行始终使用同一提交编号。
 * 派发代次过滤旧消息，执行令牌约束本轮结果写入，累计次数限制自动执行预算，三者不能互相替代。
 * 本对象是一次查询的快照；真正领取、续租或完成必须通过数据库条件更新重新校验。
 */
@Data
public class JudgeTask {
    /** 提交任务的唯一编号，查询进度、读取快照和关联执行历史均使用此编号。 */
    private Long submitId;

    /** 提交者的用户编号，用于任务归属校验及用户维度的请求幂等约束。 */
    private Long userId;

    /** 本次提交对应的题目编号；实际执行规则读取受理时保存的不可变快照。 */
    private Long questionId;

    /** 比赛编号；练习提交为空，比赛提交完成时参与权威成绩事务。 */
    private Long examId;

    /** 用户维度唯一的请求标识，重复网络请求复用此键。 */
    private String idempotencyKey;

    /** 服务端计算的内容摘要，用于识别同键不同内容的冲突。 */
    private String requestHash;

    /** RECEIVED → RUNNING → FINISHED；系统异常可经 RETRY_WAIT 重派发或进入 SYSTEM_FAILED。 */
    private String state;

    /** 派发代次区分旧消息，与实际执行次数是不同概念。 */
    private Integer dispatchSeq;

    /** 累计成功领取执行权的次数；消息重发和竞争失败不增加该次数。 */
    private Integer attemptCount;

    /** 最近一次人工重试时的累计次数，attemptCount - retryBase 为本轮已用预算。 */
    private Integer retryBase;

    /** 本次执行独占令牌；续租、完成、失败都必须携带该令牌。 */
    private String runToken;

    /** 当前执行尝试所属工作进程的标识，用于追踪执行来源及故障诊断。 */
    private String workerId;

    /** 数据库时钟下的执行租约到期时间，续租不能超过固定执行保护期限。 */
    private LocalDateTime leaseUntil;

    /** 等待投递、MQ 和本地资源的共同截止时间。 */
    private LocalDateTime queueDeadline;

    /** 数据库记录的下次允许重试时间，补偿扫描在退避结束后才可安排派发。 */
    private LocalDateTime nextRetryAt;

    /** 最近记录的系统失败原因，供用户展示、管理排查及重试诊断使用。 */
    private String failureReason;

    /** 内部完整结果，公开接口必须去除隐藏用例后再返回。 */
    private String resultJson;
}
