package org.example.common.mybatis.domain;

import lombok.Data;

/** 数据库实时状态计数，用于观察投递积压、失效租约及需人工处理的失败任务。 */
@Data
public class JudgeReliabilityStats {
    /** 尚待投递的判题事件数量，用于观察消息积压。 */
    private long pendingEvents;

    /** 已被发布者领取但尚未确认投递结果的事件数量。 */
    private long inflightEvents;

    /** 自动发送预算耗尽的判题事件数量，用于提示人工排查。 */
    private long deadEvents;

    /** 仍处于执行中但租约已过期的任务数量，用于观察补偿需求。 */
    private long expiredLeases;

    /** 已持久化重试安排、正在等待退避结束的任务数量。 */
    private long retryWaiting;

    /** 已进入系统失败终态的任务数量，用于故障监控。 */
    private long failedTasks;
}
