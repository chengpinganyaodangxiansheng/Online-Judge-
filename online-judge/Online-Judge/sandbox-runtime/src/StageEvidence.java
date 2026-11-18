/** 当前阶段独立证据；原因标志 位1超时、位2输出超限、位4内核 内存不足终止。 */
public final class StageEvidence {
    /** 是否已确认本阶段所需清理完成，未确认时不得据此继续下一用例。 */
    public boolean cleanupComplete = true;

    /** 阶段编号；负一表示编译，非负数表示按固定顺序排列的运行用例。 */
    public int index;

    /** 当前阶段的超限位集合：第一位为超时，第二位为输出超限，第三位为内存超限，可同时保留多个原因。 */
    public int flags;

    /** 当前阶段主进程退出码，与可信超限证据共同参与分类。 */
    public int exit;

    /** 当前阶段任务组累计消耗的处理器时间，单位为微秒，包含线程和子进程消耗。 */
    public long cpuMicros;

    /** 阶段启动前准备耗时，单位为微秒，与用例运行耗时分开记录。 */
    public long preparationMicros;

    /** 阶段进程从启动到退出的实际经过时间，单位为微秒，不等同于处理器消耗。 */
    public long wallMicros;

    /** 阶段退出后的终止确认和相关清理耗时，单位为微秒。 */
    public long cleanupMicros;

    /** 当前阶段任务组内存峰值，单位为字节；不可用时不伪造测量值。 */
    public long peakBytes;

    /** 限额内保存的标准输出字节片段，超限状态另由原因位记录。 */
    public byte[] stdout;

    /** 限额内保存的标准错误字节片段，不能通过报错文字伪造内存超限证据。 */
    public byte[] stderr;
}
