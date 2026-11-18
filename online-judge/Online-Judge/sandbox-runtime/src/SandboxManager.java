import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.stream.Stream;

/** 每容器只执行一次提交；编译和用例各使用独立任务组，清理确认后才推进。 */
public final class SandboxManager {
    /** 本执行环境专属的控制组父路径，供分阶段建组和整环境终止使用。 */
    // 这里的父路径指编译组和用例组共同所属的资源目录，不是源码目录。例如父路径为 /cg/task，则用例组为 /cg/task/case-0。实际目录名由启动脚本扫描后传入。
    private final Path parent;

    /** 向后端发送可信协议帧的数据输出流，与用户程序的标准输出隔离。 */
    private final DataOutputStream output = new DataOutputStream(System.out);

    /** 整次提交的固定截止点，使用系统运行时长计时，控制发送和执行的总预算。 */
    private long totalDeadline;

    /** 独立监督进程的句柄，用于检查监督存活情况及提交结束后的清理。 */
    private Process supervisor;

    /** 保存容器自身的委派范围，不接受请求传入 控制组 路径。 */
    private SandboxManager(Path parent) {
        this.parent = parent;
    }

    /** 建立独立监督进程，完成自检后报告 就绪状态；异常退出使外层按系统故障处理。 */
    public static void main(String[] args) throws Exception {
        // 路径来源完整过程：后端创建 /sys/fs/cgroup/oj-runtime-资源编号，并把它挂载到容器的 /cg。
        // 创建容器时指定该资源组为父组，引擎在其下建立容器自己的子组。
        // start.sh 查找 /cg 下唯一的直接子目录，将实际路径作为第一个启动参数传进这里。
        // 下文 /cg/task 仅用于讲解，实际子目录名称由引擎决定；这里没有把 task 写死为真实目录名。
        // 启动参数指定本容器的资源管理目录；创建独立监督进程，防止发送结果阻塞时失去超时保护。
        // Paths.get(args[0]): 获取程序启动时传入的第一个命令行参数，并将其转换成 Java 的 Path 路径对象。
        // 启动start.sh脚本执行 java ... SandboxManager "$group"，因此 args[0] = $group是脚本找到的唯一资源子目录。Paths.get 将该字符串转成路径对象，本身不创建目录。构造方法把路径保存到 parent，例如传入 /cg/task，后续操作都以这个容器内目录为资源根。
        SandboxManager manager = new SandboxManager(Paths.get(args[0]));
        // 创建监督进程
        // 完整命令示例为 java -Xms8m -Xmx24m -XX:-UsePerfData -cp /opt/sandbox/classes SandboxSupervisor /cg/task。最后的目录替换为实际父路径。初始堆八兆、最大堆二十四兆，关闭性能数据文件，从指定目录加载监督类。各参数独立传递，直到 start 才创建独立进程，用来在管理程序阻塞时仍执行超时保护。
        manager.supervisor = new ProcessBuilder(
                                "java",
                                "-Xms8m",
                                "-Xmx24m",
                                "-XX:-UsePerfData",
                                "-cp",
                                "/opt/sandbox/classes",
                                "SandboxSupervisor",
                                manager.parent.toString())
                        // 将监督进程的正常输出写入空设备并丢弃，防止它污染后端读取的管理协议。
                        .redirectOutput(new File("/dev/null"))
                        // 同样丢弃监督进程的错误输出；监督是否存活由守卫检查，状态通过管理文件传递。
                        .redirectError(new File("/dev/null"))
                        .start();
        // 创建本地守卫线程，它等待独立监督进程退出；监督死亡后负责终止整个资源环境。
        Thread guard = new Thread(new SupervisorGuard(manager.supervisor, manager.parent),
                        "supervisor-guard");
        // 将守卫标记为守护线程，虚拟机不需要为了这个线程单独保持存活。
        guard.setDaemon(true);
        // 启动守卫并发执行，主线程可以继续等待监督程序初始化。
        guard.start();
        // 当前系统单调时间加五千毫秒，得到监督程序启动的最晚允许时刻。
        long readyDeadline = RuntimeFiles.now() + 5000;
        // 监督程序启动后会发布此文件；文件尚未出现就重复等待。Paths.get 生成路径对象，exists 才实际检查文件是否存在，感叹号表示取反。
        while (!Files.exists(Paths.get("/control/supervisor"))) {
            // 监督进程已经退出，或者等待已超过五秒，任一成立都报错，不能在缺少监督保护时接收学生代码。
            if (!manager.supervisor.isAlive() || RuntimeFiles.now() > readyDeadline) {
                throw new IOException("监督进程启动失败");
            }
            // 暂停当前线程十毫秒后再检查文件，避免等待循环一直空转占用处理器；文件出现后循环结束。
            Thread.sleep(10);
        }
        // 验证控制组和低权限访问边界，失败时不能向后端声明执行器已经就绪。
        // 在同一资源范围内实际测试资源限制、进程清理和降权能力，检查失败就中止启动。
        RuntimePreflight.verify(manager.parent);
        // 创建用户专用工作目录并设置允许用户写入的权限。
        manager.prepareWorkspace();
        // 清空工作区、临时目录和共享内存目录，确保首次运行从干净环境开始。
        manager.cleanupWritable();
        // 发送类型一的就绪消息；尚未接收提交，所以执行标识和阶段证据为空。
        // 管理程序 System.out → Docker → 后端  发送“我准备好了”
        manager.message(1, "", null);
        // 当前容器只接收一次提交，后续输入由断连守卫监控，不再接纳另一份代码。
        // 从后端连接到容器的标准输入读取一份二进制请求，解析源码、用例输入和时间内存预算。
        // 后端 → Docker → 管理程序 System.in 等待“这次要执行哪些源码和输入”
        RunRequest request = Protocol.request(new DataInputStream(System.in));
        // 请求读完后创建输入守卫，继续监视同一连接；后端断开或意外发送第二份请求时终止本环境。
        Thread inputGuard = new Thread(new InputGuard(System.in, manager.parent), "input-guard");
        // 输入守卫也设为守护线程，避免只因等待输入而阻碍虚拟机结束。
        inputGuard.setDaemon(true);
        // 启动输入守卫，使主线程运行代码时仍能发现后端断连。
        inputGuard.start();
        // 当前单调时间加提交总预算，得到编译和全部用例共用的固定截止点。
        manager.totalDeadline = RuntimeFiles.now() + request.totalMillis;
        // 将截止点转成十进制文本并发布到 /control/budget，独立监督进程通过该文件获取期限。
        RuntimeFiles.publish("budget", Long.toString(manager.totalDeadline));
        try {
            // 开始编译一次、按顺序执行用例并发送证据的主流程。
            manager.execute(request);
        } finally {
            // 无论正常完成还是发生异常，都请求强制停止监督进程；守卫发现监督退出后负责终止环境。
            manager.supervisor.destroyForcibly();
        }
    }

    /** 编译产物发布后逐例运行，首个运行故障停止后续用例；答案由后端比较。 */
    private void execute(RunRequest request) throws Exception {
        // 将本次源码写入用例工作区，整个提交只编译一次，后续用例使用同一份受保护的编译结果。
        prepareWorkspace();
        Files.write(Paths.get("/work/case/Solution.java"),
                request.source.getBytes(StandardCharsets.UTF_8));
        // 编译使用独立阶段和预算，负一编号使后端能够与运行用例区分。
        StageEvidence compiled = stage(-1, new String[0], 30000, 268435456);
        if (!compiled.cleanupComplete) {
            if (compiled.flags == 0 && compiled.exit == 0) {
                throw new IOException("编译阶段清理结果尚未确认");
            }
            // 本次执行身份＋完整阶段结果
            message(2, request.attempt, compiled);
            // 本次执行身份＋结束标识，没有阶段数据
            message(3, request.attempt, null);
            // 第一条回答“编译发生了什么”，第二条回答“这次执行到此结束，不再有后续阶段”。
            return;
        }
        // 已确认编译失败先交付证据，退出后由完整环境回收负责剩余文件，绝不继续运行用例。
        if (compiled.flags != 0 || compiled.exit != 0) {
            compiled.cleanupComplete = false;
            message(2, request.attempt, compiled);
            message(3, request.attempt, null);
            return;
        }
        // 记录产物处理开始时间。
        long artifactStarted = System.nanoTime();
        // 将限额内编译产物复制到受保护目录，然后清理编译工作区。
        try {
            // 检查并复制类文件。
            publishArtifacts();
        } catch (ArtifactLimitException limit) {
            compiled.exit = 1;
            compiled.cleanupComplete = false;
            compiled.stderr = limit.getMessage().getBytes(StandardCharsets.UTF_8);
            // 发送阶段失败结果。
            message(2, request.attempt, compiled);
            // 发送结束消息。
            message(3, request.attempt, null);
            return;
        }
        cleanupWritable();
        compiled.cleanupMicros += (System.nanoTime() - artifactStarted) / 1000;
        // 把编译的结果发送给后端
        message(2, request.attempt, compiled);
        if (compiled.flags != 0 || compiled.exit != 0) {
            message(3, request.attempt, null);
            return;
        }
        // 从第零个输入开始逐例处理；每轮运行前准备工作区，成功后清理，再处理下一例。
        for (int i = 0; i < request.inputs.length; i++) {
            prepareWorkspace();
            // 纯空白输入变成空参数数组，否则按普通空格拆分。例如“3 5”得到两个命令行参数；这里不是把输入写到子进程标准输入。
            String[] arguments = request.inputs[i].trim().isEmpty() ? new String[0] : request.inputs[i].split(" ");
            // 每例新建任务组和进程，只复用本提交受保护的编译产物。
            StageEvidence result = stage(i, arguments, request.cpuMillis, request.memoryBytes);
            if (!result.cleanupComplete) {
                if (result.flags == 0 && result.exit == 0) {
                    throw new IOException("测试用例清理结果尚未确认");
                }
                message(2, request.attempt, result);
                message(3, request.attempt, null);
                return;
            }
            // 失败后没有下一用例，先保存判定；退出容器和后台回收不会覆盖已确认结果。
            if (result.flags != 0 || result.exit != 0) {
                result.cleanupComplete = false;
                message(2, request.attempt, result);
                message(3, request.attempt, null);
                return;
            }
            long cleanupStarted = System.nanoTime();
            cleanupWritable();
            result.cleanupMicros += (System.nanoTime() - cleanupStarted) / 1000;
            message(2, request.attempt, result);
            if (result.flags != 0 || result.exit != 0) {
                break;
            }
        }
        message(3, request.attempt, null);
    }

    /** 新建任务组、启动降权进程并收集证据；进程退出不等于清理完成。 */
    private StageEvidence stage(int index, String[] arguments, long cpuMillis, long memory) throws Exception {
        // 每个阶段单独计时并建立资源组，保证当前用例的资源统计不会混入前一个用例。
        long prepared = System.nanoTime();
        if (!supervisor.isAlive() || RuntimeFiles.now() >= totalDeadline) {
            throw new IOException("监督进程已退出或本次提交已超过总时间限制");
        }
        // resolve 在父路径后拼接子路径；负一编号选择编译组，零选择第一个用例组。例如父路径 /cg/task 得到 /cg/task/compile 或 /cg/task/case-0。
        Path group = parent.resolve(index < 0 ? "compile" : "case-" + index);
        // 限制内存、进程数量和处理器带宽，并禁止使用交换空间，防止用户程序挤占其他任务资源。
        // 在资源控制文件系统中创建阶段子组，随后向其中的内核控制文件写入限额。
        Files.createDirectory(group);
        // 将字节单位的内存预算转成文本写入内核限制文件，例如二百五十六兆字节对应 268435456。
        RuntimeFiles.control(group.resolve("memory.max"), Long.toString(memory));
        // 禁止组内使用交换空间，避免内存压力被转移到磁盘而掩盖资源消耗。
        RuntimeFiles.control(group.resolve("memory.swap.max"), "0");
        // 限制组内进程及线程数量为四十八，阻止无限创建执行单元。
        RuntimeFiles.control(group.resolve("pids.max"), "48");
        // 每十万微秒周期最多使用十万微秒处理器时间，限制为约一个核心的处理器带宽。
        RuntimeFiles.control(group.resolve("cpu.max"), "100000 100000");
        // 启用内存不足时按组终止，避免仅终止某个子进程后其他进程继续残留。
        RuntimeFiles.control(group.resolve("memory.oom.group"), "1");
        if (!RuntimeFiles.read(group.resolve("memory.swap.max")).equals("0")) {
            throw new IOException("禁用任务组交换空间失败");
        }
        long launched = System.nanoTime();
        // 启动命令示例为 /bin/sh /opt/sandbox/enter.sh /cg/task/case-0 run 3 5。入口脚本加入资源组、降低权限，再运行用户代码；实际经过时间上限为处理器预算五倍加两秒，处理器预算由毫秒转成微秒。
        Process process = StageControl.start(group, index < 0 ? "compile" : "run", arguments,
                        RuntimeFiles.now() + cpuMillis * 5 + 2000, cpuMillis * 1000);
        // 关闭写往子进程标准输入的管道，让子进程读标准输入时得到结束信号；用例输入由命令行参数提供。
        process.getOutputStream().close();
        // 标准输出和错误输出必须同时读取，否则其中一条管道写满可能让用户程序卡住。
        BoundedCollector stdout = new BoundedCollector(process.getInputStream(), group);
        BoundedCollector stderr = new BoundedCollector(process.getErrorStream(), group);
        Thread outThread = new Thread(stdout);
        Thread errThread = new Thread(stderr);
        outThread.start();
        errThread.start();
        StageEvidence result = new StageEvidence();
        result.index = index;
        // 阻塞等待主进程结束并保存退出码；超时保护由独立监督进程负责，主进程退出后仍须核对残留和清理。
        result.exit = process.waitFor();
        // 退出码只说明主进程已经结束，还需要检查监督状态、残留子进程和资源使用记录。
        if (Files.exists(Paths.get("/control/fatal"))) {
            throw new IOException("执行器监督已停止");
        }
        long exited = System.nanoTime();
        result.preparationMicros = (launched - prepared) / 1000;
        result.wallMicros = (exited - launched) / 1000;
        // 主进程退出先撤销迟到终止资格并取回合法原因，再确认其他子进程清空。
        result.flags = StageControl.finish(group);
        try {
            RuntimeFiles.killAndWait(group);
        } catch (Exception cleanupFailure) {
            result.cleanupComplete = false;
        }
        // 最多等待标准输出收集线程两秒，读取完进程退出前留在管道里的数据。
        // 当前管理程序主线程，最多等待正常输出收集线程结束 2000 毫秒。
        outThread.join(2000);
        // 再最多等待错误输出收集线程两秒，避免诊断尚未读完就返回。
        errThread.join(2000);
        if (outThread.isAlive() || errThread.isAlive()) {
            throw new IOException("等待输出收集完成超时");
        }
        // 收集器异常继续向上抛出，不能将缺失输出冒充空字符串参与判题。
        // 检查错误输出收集是否成功；成功则把已收集的错误输出字节放进本阶段结果。失败则会抛出failure异常
        result.stdout = stdout.bytes();
        result.stderr = stderr.bytes();
        /**
         * flag 本次计算	    结果含义
            0	0 | 2 = 2	输出超限
            1	1 | 2 = 3	已有超时，同时输出超限
            2	2 | 2 = 2	已记录过输出超限，重复加入不变
        */
        if (stdout.exceeded || stderr.exceeded) {
            result.flags |= 2;
        }
        // 只读取本阶段新建任务组的内核杀进程证据，不使用用户打印的错误文字。
        if (RuntimeFiles.metric(group.resolve("memory.events"), "oom_kill") > 0) {
            result.flags |= 4;
        }
        result.cpuMicros = RuntimeFiles.metric(group.resolve("cpu.stat"), "usage_usec");
        // 退出恰好落在两个监督采样点之间时，最终内核计数仍必须执行 处理器 限额。
        if (result.cpuMicros >= cpuMillis * 1000) {
            result.flags |= 1;
        }
        Path peak = group.resolve("memory.peak");
        result.peakBytes = Files.exists(peak) ? Long.parseLong(RuntimeFiles.read(peak)) : -1;
        if (result.cleanupComplete) {
            try {
                // 只会删除最后一级的 例如 /cg/容器子组/complie只删除complie
                Files.delete(group);
            } catch (IOException cleanupFailure) {
                result.cleanupComplete = false;
            }
        }
        result.cleanupMicros = (System.nanoTime() - exited) / 1000;
        return result;
    }

    /** 每例仅开放 用例工作区、临时目录和共享内存区；父目录和只读编译产物保持管理身份所有。 */
    private void prepareWorkspace() throws Exception {
        // 只给用户程序开放专用工作目录的读写权限，源码和运行期间产生的文件都放在这里。
        Files.createDirectories(Paths.get("/work/case"));
        Files.setPosixFilePermissions(Paths.get("/work/case"), PosixFilePermissions.fromString("rwxrwxrwx"));
    }

    /** 清理全部允许用户写入的位置，失败时不启动下一例。 */
    private void cleanupWritable() throws Exception {
        // 主进程退出不代表文件已消失；清理用户可写的工作区、临时区及共享内存区。
        // 删除后再遍历确认目录为空，检查失败就停止后续用例，避免前一例污染后一例。
        for (String directory : new String[] {"/work/case", "/tmp", "/dev/shm"}) {
            RuntimeFiles.clear(Paths.get(directory));
        }
        for (String directory : new String[] {"/work/case", "/tmp", "/dev/shm"}) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(Paths.get(directory))) {
                // 目录迭代器还能取到任意一个条目就说明未清空，不能继续运行下一用例。
                if (files.iterator().hasNext()) {
                    throw new IOException("用户可写目录清理失败，仍有残留文件");
                }
            }
        }
    }

    /** 仅复制受数量与总大小限制的普通 字节码 文件，拒绝链接及越界产物。 */
    private void publishArtifacts() throws Exception {
        // 编译成功后只保留受限数量和大小的类文件，复制到用户无权修改的产物区。
        // 拒绝软链接和越界路径，防止编译产物引用工作区之外的内容；不把源码一并公开给运行阶段。
        Path root = Paths.get("/work/case");
        int count = 0;
        long bytes = 0;
        // Files.walk(root): 遍历这个目录及其子目录。
        try (Stream<Path> paths = Files.walk(root)) {
            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                // 符号链接可以理解为一个“指向其他路径的入口”。这里遇到它直接拒绝，避免产物间接指向其他位置。
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("编译产物中不允许包含符号链接");
                }
                // 当前路径是目录：本轮跳过。  文件名不是以 .class 结束：本轮跳过。
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        || !path.toString().endsWith(".class")) {
                    continue;
                }
                // 不是普通文件。
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || ++count > 1024
                        || (bytes += Files.size(path)) > 16777216) {
                    throw new ArtifactLimitException();
                }
                // 先求产物相对工作区的路径，再拼到受保护目录并规范化。例如 /work/case/A.class 对应 /artifact/A.class。
                // relativize: 从 root 到 path 怎么走”的相对路径
                // normalize() 用来按路径规则消除多余的层级，让路径表达更简洁。主要处理：
                // .：当前目录，可以消去。
                //目录名/..：进入这个目录后又返回，可以一起消去。
                // 这里的路径还没创建只是计划是这样规划路径，下面代码才是路径创建。
                Path target = Paths.get("/artifact").resolve(root.relativize(path)).normalize();
                // 检查规范化后的目标仍位于受控产物目录内，若路径越界则拒绝复制。
                if (!target.startsWith(Paths.get("/artifact"))) {
                    throw new IOException("编译产物路径超出受控目录");
                }
                // target.getParent(): /artifact
                Files.createDirectories(target.getParent());
                Files.copy(path, target);
                Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("r--r--r--"));
            }
        }
        if (count == 0) {
            throw new IOException("未找到编译生成的类文件");
        }
    }

    /** 单帧缓冲有界；发送期间独立监督进程额外施加五秒截止时间。 */
    private void message(int type, String attempt, StageEvidence stage) throws Exception {
        // 将消息类型、执行身份和阶段证据按固定顺序写入帧，后端按同一协议解码。
        // 用户输出只作为帧内的长度受限字段，不能把用户打印的文字直接当成控制消息。
        if (Files.exists(Paths.get("/control/fatal"))) {
            throw new IOException("执行器监督已停止");
        }
        // bytes 保存整条消息的字节
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // frame 用于按整数、字节数组等格式写入这块缓冲。
        DataOutputStream frame = new DataOutputStream(bytes);
        frame.writeInt(type); // 1的类型 算4个字节
        Protocol.bytes(frame, attempt.getBytes(StandardCharsets.UTF_8)); // type为1时有4个字节，""此时里面计算也有4个字节 所以frame有8个字节
        if (stage != null) {
            frame.writeInt(stage.index);
            frame.writeInt(stage.flags);
            frame.writeInt(stage.exit);
            frame.writeLong(stage.cpuMicros);
            frame.writeLong(stage.peakBytes);
            frame.writeBoolean(stage.cleanupComplete);
            frame.writeLong(stage.preparationMicros);
            frame.writeLong(stage.wallMicros);
            frame.writeLong(stage.cleanupMicros);
            Protocol.bytes(frame, stage.stdout);
            Protocol.bytes(frame, stage.stderr);
        }
        // 暂时缩短监督期限，防止发送一直阻塞。
        if (totalDeadline > 0) {
            RuntimeFiles.publish("budget", Long.toString(Math.min(totalDeadline, RuntimeFiles.now() + 5000)));
        }
        // 按可信协议发送有界结果；发送期间监督进程继续限制阻塞时间。
        Protocol.send(output, bytes.toByteArray()); // 1 "" NULL 此时加上send方法里面的4字节 就为12字节发送send消息， 此时就是发送Ready消息给后端，告诉管理程序和监控程序准备就绪了
        // 恢复原来的总截止时间，让后续工作继续使用原预算。
        if (totalDeadline > 0) {
            RuntimeFiles.publish("budget", Long.toString(totalDeadline));
        }
    }
}
