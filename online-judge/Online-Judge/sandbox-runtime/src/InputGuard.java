import java.io.InputStream;
import java.nio.file.Path;

/** 单次提交读完后独立监视 容器通信 输入；断连或多余控制数据都取消旧执行。 */
public final class InputGuard implements Runnable {
    /** 提交请求读取后继续监视的控制输入流，断开或异常输入触发终止。 */
    private final InputStream input;

    /** 本执行环境专属的控制组父路径，供分阶段建组和整环境终止使用。 */
    private final Path parent;

    /** 输入是管理器专属通道，用户 虚拟机 不继承此管道。 */
    public InputGuard(InputStream input, Path parent) {
        this.input = input;
        this.parent = parent;
    }

    /** 输入流结束 或任何第二条请求均禁止继续推进，终止不依赖结果发送线程。 */
    @Override
    public void run() {
        // 提交已经读取完毕，这条输入流只用于监视控制连接是否仍有效。
        // 后端断开或继续发送非预期数据时停止本环境，避免失去控制的用户程序继续占用资源。
        try {
            input.read();
            RuntimeFiles.publish("fatal", "CONTROL_DISCONNECTED");
            RuntimeFiles.control(parent.resolve("cgroup.kill"), "1");
        } catch (Exception failure) {
            Runtime.getRuntime().halt(70);
        }
    }
}
