package org.example.judge.domain;

import lombok.Data;

/** 单个编译或运行阶段的执行证据；先判断超时/输出超限，再读取正常退出码。 */
@Data
public class SandboxStageResult {
    /** 当前阶段主进程的退出码；不能单独用来推断内存超限。 */
    private long exitCode;

    /** 是否具有本阶段超时证据，供结果分类使用；当前字段合并记录处理器时间与墙钟超时。 */
    private boolean timedOut;

    /** 当前阶段是否确认累计输出超限，超限时不能把截断输出当作正常答案。 */
    private boolean outputExceeded;

    /** 当前阶段限额内的标准输出文本，只有无超限且证据完整时才可比较答案。 */
    private String output;

    /** 当前阶段限额内的标准错误文本，仅供诊断，不作为内核超限证据。 */
    private String error;

    /** 只能由当前阶段任务组的内核证据设置，不能根据 stderr 或退出码猜测。 */
    private boolean memoryExceeded;

    /** 外层 OOM、管理通道丢失等基础设施异常优先阻止形成用户判定。 */
    private boolean infrastructureFailure;

    /** 只有执行器确认任务组已清空并删除后才为 true；缺失时禁止通过判定。 */
    private boolean cleanupComplete;
}
