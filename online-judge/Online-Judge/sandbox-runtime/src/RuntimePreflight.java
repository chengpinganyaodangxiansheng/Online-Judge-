import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** 入池前验证真正需要的委派控制与降权路径，而非仅检查容器 运行中 状态。 */
public final class RuntimePreflight {
    /** 工具类不实例化。 */
    private RuntimePreflight() {}

    /** 创建空探针组验证资源控制、禁止 交换空间、整组终止与删除，然后确认非 管理员 身份。 */
    public static void verify(Path parent) throws Exception {
        // 先建立一个空的试验资源组，实际写入限制配置，确认环境具备后续判题需要的控制能力。
        // resolve是将当前parent路径下进行拼接上other
        Path probe = parent.resolve("preflight");
        Files.createDirectory(probe);
        // 在这个路径下拼接文件，写入value  通过这种方式对Linux cgroup的内核来进行限制
        RuntimeFiles.control(probe.resolve("memory.max"), "268435456");
        RuntimeFiles.control(probe.resolve("memory.swap.max"), "0");
        RuntimeFiles.control(probe.resolve("cpu.max"), "100000 100000");
        RuntimeFiles.control(probe.resolve("pids.max"), "48");
        if (!"0".equals(RuntimeFiles.read(probe.resolve("memory.swap.max")))) {
            throw new IOException("无法确认交换空间已禁用");
        }
        // 空探针组也必须能终止并确认清空，以验证后续清理依赖的管理能力。
        RuntimeFiles.killAndWait(probe);
        Files.delete(probe);
        // 用实际低权限身份检查编号和访问边界，不能只根据启动配置推断权限有效。
        Process identity = new ProcessBuilder(
                                "su",
                                "-s",
                                "/bin/sh",
                                "-c",
                                "test \"$(id -u)\" = 65534 && test ! -w /cg && test ! -r"
                                    + " /control/supervisor",
                                "nobody")
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start();
        try {
            if (!identity.waitFor(2, TimeUnit.SECONDS) || identity.exitValue() != 0) {
                throw new IOException("权限隔离校验未通过");
            }
        } finally {
            if (identity.isAlive()) {
                identity.destroyForcibly();
            }
        }
    }
}
