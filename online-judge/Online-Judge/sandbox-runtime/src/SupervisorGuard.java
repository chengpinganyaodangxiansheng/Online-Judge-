import java.nio.file.Path;

/** 监督进程被强制杀死时补上退出保护，不依赖该进程执行 最终清理代码块。 */
public final class SupervisorGuard implements Runnable {
    /** 独立监督进程的句柄，用于检查监督存活情况及提交结束后的清理。 */
    private final Process supervisor;

    /** 本执行环境专属的控制组父路径，供分阶段建组和整环境终止使用。 */
    private final Path parent;

    /** 仅接收管理器创建的进程句柄和本容器专属任务根组。 */
    public SupervisorGuard(Process supervisor, Path parent) {
        this.supervisor = supervisor;
        this.parent = parent;
    }

    /** 监督器退出即先标记平台故障再整组终止；控制文件失效时退出 容器主进程。 */
    @Override
    public void run() {
        // 等待独立监督进程退出；监督停止意味着已经不能可靠保证用户代码会按期限终止。
        // 因此记录致命故障并终止整个环境，异常时直接结束管理进程，不能继续接受无保护执行。
        try {
            supervisor.waitFor();
            RuntimeFiles.publish("fatal", "SUPERVISOR_EXITED");
            RuntimeFiles.control(parent.resolve("cgroup.kill"), "1");
        } catch (Exception failure) {
            Runtime.getRuntime().halt(70);
        }
    }
}
