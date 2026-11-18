/** 一次提交的受限输入，期望答案不发送给容器。 */
public final class RunRequest {
    /** 提交级执行协议的身份标识，用于拒绝其他执行尝试的响应。 */
    public String attempt;

    /** 本次提交已组装的源码，受协议长度限制，用于一次性编译。 */
    public String source;

    /** 每个运行用例的任务组处理器时间预算，单位为毫秒。 */
    public long cpuMillis;

    /** 每个运行用例的任务组内存预算，单位为字节。 */
    public long memoryBytes;

    /** 整个提交级执行允许的最长保护时间，单位为毫秒。 */
    public long totalMillis;

    /** 按固定执行顺序排列的用例输入，协议解析时限制数量及每项字节数。 */
    public String[] inputs;
}
