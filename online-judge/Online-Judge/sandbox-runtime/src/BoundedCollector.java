import java.io.*;
import java.nio.file.Path;

/** 标准输出和标准错误 独立累计，缓冲和诊断均有上限，超限后终止整个任务组。 */
public final class BoundedCollector implements Runnable {
    /** 待流式读取的用户输出流，每个收集器只负责一个输出通道。 */
    private final InputStream stream;

    /** 当前阶段任务组路径，输出超限时据此终止整个任务组。 */
    private final Path group;

    /** 仅保存限额内输出片段的字节缓冲，累计输出超限后不再无限扩张。 */
    private final ByteArrayOutputStream kept = new ByteArrayOutputStream();

    /** 流读取或终止处理异常，读取最终片段时向管理程序传播，不能伪装成正常输出。 */
    private volatile Exception failure;

    /** 输出累计量是否已超过限额，供调用方拒绝使用截断结果。 */
    public volatile boolean exceeded;

    /** 保存当前阶段管道与身份，用户输出永远不能写入管理控制流。 */
    public BoundedCollector(InputStream stream, Path group) {
        this.stream = stream;
        this.group = group;
    }

    /** 分块读取并累计；保留最多六十四千字节，越界不会继续无界保存。 */
    public void run() {
        // 实际输出累计量与已保存片段大小分开统计；缓冲存满后也不能把累计量重新归零。
        long total = 0;
        try {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                // 每次只保存剩余额度允许的部分，保证错误诊断最多占固定大小的内存。
                kept.write(buffer, 0, Math.min(count, 65536 - kept.size()));
                if (total > 65536) {
                    exceeded = true;
                    // 超限必须终止整个任务组，而不只是截断显示；原因位二供后端判为输出超限。
                    StageControl.stop(group, 2);
                    break;
                }
            }
        } catch (Exception error) {
            // 保存读取异常，主线程取结果时重新抛出，避免把读取失败误认为程序正常输出结束。
            failure = error;
        }
    }

    /** 收集线程结束后取得有界数据，管道异常不能伪装成正常输出。 */
    public byte[] bytes() throws Exception {
        if (failure != null) {
            throw failure;
        }
        return kept.toByteArray();
    }
}
