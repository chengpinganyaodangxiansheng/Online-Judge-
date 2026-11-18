package org.example.api.domain.dto;

import lombok.Data;

/** MQ 判题通知，仅携带任务定位信息；源码和隐藏用例从数据库快照读取。 */
@Data
public class JudgeRequestedDTO {
    /** Outbox 事件编号，同一次事件重投保持不变。 */
    private String eventId;

    /** 提交任务的唯一编号，用于关联任务、快照、派发事件和历史结果。 */
    private Long submitId;

    /** 派发代次，自动/人工重派发递增，用来拒绝旧代次消息。 */
    private Integer dispatchSeq;
}
