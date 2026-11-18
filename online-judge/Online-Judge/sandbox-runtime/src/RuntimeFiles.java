import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** 容器内可信文件操作：固定根目录，不跟随用户软链接。 */
public final class RuntimeFiles {
    /** 工具类不实例化。 */
    private RuntimeFiles() {}

    /** 使用跨进程一致的 操作系统 单调启动时间，避免系统时间调整影响截止时间。 */
    public static long now() throws IOException {
        return (long) (Double.parseDouble(read(Paths.get("/proc/uptime")).split(" ")[0]) * 1000);
    }

    /** 只读取管理文件或 控制组 小型统计文件，不用于用户输出。 */
    public static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
    }

    /** 写入内核控制文件时不创建或删除文件。 */
    public static void control(Path path, String value) throws IOException {
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
    }

    /** 原子发布管理状态，读取方不会读到半条记录。 */
    public static void publish(String name, String value) throws IOException {
        // 先完整写临时文件，再原子替换正式文件；另一个进程不会读到只写了一半的状态。
        Path temp = Paths.get("/control/" + name + ".new");
        Files.write(temp, value.getBytes(StandardCharsets.UTF_8));
        Files.move(
                temp,
                Paths.get("/control/" + name),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    /** 从内核键值统计中读取必需证据；缺失时抛出而非猜测为零。 */
    public static long metric(Path file, String key) throws IOException {
        // 内核统计按“名称 数值”排列，只返回指定指标；找不到就报错，不能猜成零并误判通过。
        // populated 1  这是一行
        // frozen   0   这是第二行
        for (String line : read(file).split("\n")) {
            // \\s+匹配一个或多个连续的空白字符。
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 2 && parts[0].equals(key)) {
                return Long.parseLong(parts[1]);
            }
        }
        throw new IOException("缺少控制组统计指标：" + key);
    }

    /** 清理指定可写区，不跟随符号链接且不收集全目录文件列表。 */
    public static void clear(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        // 目录存在,则负责遍历：找到文件、进入子目录、遍历完目录按照CleanupVisitor来进行删除
        Files.walkFileTree(root, new CleanupVisitor(root));
    }

    /** 组内所有进程确认退出后才允许清理文件和删除组。 */
    public static void killAndWait(Path group) throws Exception {
        // 请求内核终止整个任务组，而不只是管理程序持有的主进程。
        control(group.resolve("cgroup.kill"), "1");
        long deadline = now() + 2000;
        // 必须确认内核记录的组内进程已经清空，才能进入下一阶段。
        // populated: 0	该资源组及其子组里已经没有进程
        while (metric(group.resolve("cgroup.events"), "populated") != 0) {
            if (now() >= deadline) {
                throw new IOException("等待任务组进程清空超时");
            }
            Thread.sleep(10);
        }
    }
}
