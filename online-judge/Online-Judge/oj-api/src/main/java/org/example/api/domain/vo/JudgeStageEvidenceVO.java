package org.example.api.domain.vo;

import lombok.Data;

/** 阶段判定证据，不含源码、用例输入或期望答案；随有效判题结果一起持久化。 */
@Data
public class JudgeStageEvidenceVO {
    /** -1 表示编译，非负值为固定用例编号。 */
    private int index;

    /** 当前阶段主进程的退出码；不能单独用来推断内存超限。 */
    private long exitCode;

    /** 是否具有本阶段超时证据，供结果分类使用；当前字段合并记录处理器时间与墙钟超时。 */
    private boolean timedOut;

    /** 当前阶段是否确认累计输出超限，超限时不能把截断输出当作正常答案。 */
    private boolean outputExceeded;

    /** 当前阶段任务组是否具有内核内存超限证据，不依赖用户打印的报错文字。 */
    private boolean memoryExceeded;

    /** 当前任务组清理是否已经确认；失败判定可以先于完整环境回收保存。 */
    private boolean cleanupComplete;

    /** 当前阶段任务组累计消耗的处理器时间，单位为微秒，包含线程和子进程消耗。 */
    private long cpuMicros;

    /** 当前阶段任务组准备耗时，不含预热及排队。 */
    private long preparationMicros;

    /** 启动至退出的墙钟耗时，与任务组 CPU 时间分别记录。 */
    private long wallMicros;

    /** 已执行的任务组、输出收尾及文件清理耗时；是否完整以 cleanupComplete 为准。 */
    private long cleanupMicros;

    /** 内核不支持或无法取得非关键峰值时保留 null。 */
    private Long peakBytes;
}
