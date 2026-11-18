import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 进程间文件锁协调启动、完成与终止；超时只能作用于同一阶段身份。 */
public final class StageControl {
    /** 工具类不实例化。 */
    private StageControl() {}

    /**
     * 接收任务组路径、执行模式、用户参数、墙钟截止点和处理器微秒预算。 在进程内互斥和跨进程文件锁保护下发布阶段身份，再启动可信降权入口并返回进程句柄。
     * 用户参数作为独立参数传递，不拼接为命令文本；调用失败向管理程序抛出。
     */
    // synchronized防止的是同一个JVM中的不同线程竞争  文件锁是防止不同JVM进程来争抢权限
    public static synchronized Process start(Path group, String mode, String[] arguments, long wall, long cpu) throws Exception {
        // 例如资源组为 /cg/task/case-0、运行模式、参数为“3”和“5”，最终完整启动命令为：
        // /bin/sh /opt/sandbox/enter.sh /cg/task/case-0 run 3 5
        // 阶段入口脚本会先加入该资源组，再切换为低权限用户，最后执行：
        // java -XX:-UsePerfData -Xms8m -Xmx512m -cp /artifact Solution 3 5
        // 编译模式则执行 javac -encoding UTF-8 -d /work/case /work/case/Solution.java。
        // 目录名在这里是示例，实际 group 由管理程序根据父资源路径与阶段编号拼成。
        try (FileChannel channel = FileChannel.open(Paths.get("/control/lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            // 原子替换控制文件，监督进程只能读到完整的阶段身份、期限、状态和原因位。
            RuntimeFiles.publish("stage", group + "|" + wall + "|" + cpu + "|ACTIVE|0");
            // 列表的每项代表一个命令参数，不是先拼成一段可以任意执行的命令文本。
            List<String> command = new ArrayList<>();
            Collections.addAll(command, "/bin/sh", "/opt/sandbox/enter.sh", group.toString(), mode);
            Collections.addAll(command, arguments);
            // 追加参数保留各自边界，例如一个含空格的参数仍是一个参数，不会被再次解释成命令。
            // 可信入口先加入受控任务组再降低权限，不直接运行拼接的用户命令。
            return new ProcessBuilder(command).start();
        }
    }

    /** 按任务组身份完成当前阶段并返回已记录的原因位；完成与终止共用文件锁，身份不符抛出异常，先完成后到达的超时请求不再有效。 */
    public static synchronized int finish(Path group) throws Exception {
        // 文件锁让管理进程与监督进程互斥操作阶段记录；本进程的方法锁不足以保护另一个进程。
        // 核对身份后标记完成，之后到达的超时请求会看到已完成状态，不再终止该阶段。
        try (FileChannel channel = FileChannel.open(Paths.get("/control/lock"), StandardOpenOption.WRITE);
                FileLock lock = channel.lock()) {
            String[] state = RuntimeFiles.read(Paths.get("/control/stage")).split("\\|");
            if (!state[0].equals(group.toString())) {
                throw new IOException("当前阶段与预期任务组身份不匹配");
            }
            state[3] = "DONE";   // String.join("|", state): 把 state 数组的所有元素，用 | 连接成一个字符串。
            RuntimeFiles.publish("stage", String.join("|", state));
            return Integer.parseInt(state[4]);
        }
    }

    /** 输入预期任务组和原因位，仅对身份相同且仍活动的阶段合并原因并整组终止；已完成或身份已变化时忽略，防止迟到回调误杀下一例。 */
    public static synchronized void stop(Path expected, int reason) throws Exception {
        // 在同一文件锁内复查阶段归属和活动状态，过滤旧用例迟到的超时或输出超限通知。
        // 合并原因位再杀进程，避免只看到被杀的退出码，却不知道是平台因何主动终止。
        try (FileChannel channel = FileChannel.open(Paths.get("/control/lock"), StandardOpenOption.WRITE);
                FileLock lock = channel.lock()) {
            String[] state = RuntimeFiles.read(Paths.get("/control/stage")).split("\\|"); // 匹配字面量的竖线字符 |。
            if (!state[0].equals(expected.toString()) || !state[3].equals("ACTIVE")) {
                return;
            }
            state[4] = Integer.toString(Integer.parseInt(state[4]) | reason); // | 把原因给叠加起来
            RuntimeFiles.publish("stage", String.join("|", state));
            RuntimeFiles.control(expected.resolve("cgroup.kill"), "1");
        }
    }
}
