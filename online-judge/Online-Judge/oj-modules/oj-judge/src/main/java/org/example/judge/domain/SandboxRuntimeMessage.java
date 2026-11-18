package org.example.judge.domain;

import lombok.Data;

/** 可信执行器返回的阶段帧，按 attempt 和用例编号关联，用户输出仅是字段数据。 */
@Data
public class SandboxRuntimeMessage {
    /** 执行器消息类型：一为就绪，二为阶段证据，三为提交执行结束。 */
    private int type;

    /** 提交级执行协议的身份标识，用于拒绝其他执行尝试的响应。 */
    private String attempt;

    /** 阶段编号；负一表示编译，非负数表示按固定顺序排列的运行用例。 */
    private int index;

    /** 当前阶段的退出、超限、输出及清理证据，供分类器生成判定。 */
    private SandboxStageResult evidence;

    /** 当前阶段任务组累计消耗的处理器时间，单位为微秒，包含线程和子进程消耗。 */
    private long cpuMicros;

    /** 当前阶段任务组内存峰值，单位为字节；不可用时不伪造测量值。 */
    private Long peakBytes;

    /** 阶段启动前准备耗时，单位为微秒，与用例运行耗时分开记录。 */
    private long preparationMicros;

    /** 阶段进程从启动到退出的实际经过时间，单位为微秒，不等同于处理器消耗。 */
    private long wallMicros;

    /** 阶段退出后的终止确认和相关清理耗时，单位为微秒。 */
    private long cleanupMicros;
}
