package org.example.api.domain.vo;

import lombok.Data;

/** 提交与轮询接口的公开任务视图，不包含运行令牌、源码或隐藏用例。 */
@Data
public class JudgeTaskVO {
    /** 字符串传输长整型编号，避免 JavaScript 丢失精度。 */
    private String submitId;

    /** RECEIVED/RUNNING/RETRY_WAIT 为进行中，FINISHED/SYSTEM_FAILED 为终态。 */
    private String state;

    /** 实际取得执行权的累计次数，不是 MQ 消息投递次数。 */
    private Integer attemptCount;

    /** 已记录的失败原因，用于展示故障和排查恢复过程。 */
    private String failureReason;

    /** 判题完成才有结果；FINISHED 也可能是 WA、CE 等未通过结果。 */
    private UserQuestionResultVO result;
}
