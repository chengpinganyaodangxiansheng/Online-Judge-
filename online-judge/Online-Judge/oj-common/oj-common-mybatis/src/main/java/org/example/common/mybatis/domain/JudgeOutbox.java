package org.example.common.mybatis.domain;

import lombok.Data;

/** 待投递事件记录，与业务变更同事务写入；SENT 只说明消息发送确认，不说明任务完成。 */
@Data
public class JudgeOutbox {
    /** 持久化事件的唯一编号，同一事件重复投递时保持不变。 */
    private String eventId;

    /** 提交任务的唯一编号，用于关联任务、快照、派发事件和历史结果。 */
    private Long submitId;

    /** 任务派发代次，用来识别过期消息；消息发送重试不会自行递增代次。 */
    private Integer dispatchSeq;

    /** 原始事件载荷，重投时复用，不能重新读取当前题目构造消息。 */
    private String payload;

    /** PENDING 待发送、INFLIGHT 已领取、SENT 已确认、DEAD 发送预算耗尽。 */
    private String state;

    /** 事件已领取发送的累计次数，用于限制自动投递预算。 */
    private Integer sendCount;

    /** 当前发布者的领取令牌，拦截旧发布回调对新一轮状态的覆盖。 */
    private String claimToken;
}
