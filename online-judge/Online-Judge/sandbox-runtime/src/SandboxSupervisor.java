import java.nio.file.*;

/** 独立管理进程：不依赖结果发送线程，按总期限和阶段 处理器/墙钟期限终止。 */
public final class SandboxSupervisor {
    /** 总保护及自身异常都通过本容器专属任务根组终止，不依赖 容器主进程 的信号处理。 */
    public static void main(String[] args) throws Exception {
        // 无论监督正常退出还是异常退出，都要记录故障并终止整个环境，不能留下无监督的用户进程。
        try {
            supervise();
        } finally {
            // 先留下平台终止证据，避免整组终止的瞬间被管理器误读成用户 运行错误。
            try {
                RuntimeFiles.publish("fatal", "SUPERVISION_STOPPED");
            } finally {
                // sandboxManager传递的manager.parent.toString()的参数
                RuntimeFiles.control(Paths.get(args[0]).resolve("cgroup.kill"), "1");
            }
        }
    }

    /** 监督器自身异常时由入口终止管理器，不能静默丢失保护继续运行用户代码。 */
    private static void supervise() throws Exception {
        // 发布监督进程身份，管理程序看到此状态后才继续自检和接收提交。
        RuntimeFiles.publish("supervisor",
                // /proc/self/stat 是 Linux 提供的当前进程信息文件，开头的第一个字段就是进程编号。
                RuntimeFiles.read(Paths.get("/proc/self/stat")).split(" ")[0]);
        while (true) {
            // 先检查整次提交的截止时间，再检查当前编译或运行阶段，两个时间限制都必须满足。
            long now = RuntimeFiles.now();
            Path budget = Paths.get("/control/budget");
            if (Files.exists(budget) && now >= Long.parseLong(RuntimeFiles.read(budget))) {
                return;
            }
            Path stage = Paths.get("/control/stage");
            if (Files.exists(stage)) {
                String[] state = RuntimeFiles.read(stage).split("\\|");
                if (state[3].equals("ACTIVE")) {
                    Path group = Paths.get(state[0]);
                    try {
                        if (now >= Long.parseLong(state[1])       // cpu.stat 是 Linux 内核提供的资源组统计文件。内核负责统计并提供里面的数据
                                // 内容类似于：usage_usec 1200000
                                //           user_usec 1000000
                                //           system_usec 200000
                                || RuntimeFiles.metric(group.resolve("cpu.stat"), "usage_usec") // usage_usec：累计使用的 CPU 时间。
                                >= Long.parseLong(state[2])) {
                            // 两类超时当前共用同一原因位；终止入口仍会持锁复查阶段身份。
                            StageControl.stop(group, 1);
                        }
                    } catch (NoSuchFileException removed) {
                        // 管理器可能已完成并删除旧组；复查身份与状态，不能把正常推进当作监督故障。
                        String[] current = RuntimeFiles.read(stage).split("\\|");
                        if (current[0].equals(state[0]) && current[3].equals("ACTIVE")) {
                            throw removed;
                        }
                    }
                }
            }
            Thread.sleep(10);
            // 短暂休眠后再次检查，避免监督循环持续空转占用处理器。
        }
    }
}
