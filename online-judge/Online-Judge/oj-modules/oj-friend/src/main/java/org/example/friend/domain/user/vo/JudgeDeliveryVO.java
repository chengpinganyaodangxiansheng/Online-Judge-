package org.example.friend.domain.user.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 管理员可见的投递记录，保留代次、发送次数和失败原因，不返回事件中的内部数据。 */
@Data
public class JudgeDeliveryVO {
    /** 持久化事件的唯一编号，同一事件重复投递时保持不变。 */
    private String eventId;

    /** 任务派发代次，用来识别过期消息；消息发送重试不会自行递增代次。 */
    private Integer dispatchSeq;

    /** 该事件的投递状态，区分待发送、发送中、已确认和自动发送耗尽。 */
    private String state;

    /** 事件已领取发送的累计次数，用于限制自动投递预算。 */
    private Integer sendCount;

    /** 已记录的失败原因，用于展示故障和排查恢复过程。 */
    private String failureReason;

    /** 下次允许重试的数据库时间，扫描器须等待退避结束后再处理。 */
    private LocalDateTime nextRetryAt;
}
