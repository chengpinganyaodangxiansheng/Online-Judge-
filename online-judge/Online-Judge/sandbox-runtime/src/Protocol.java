import java.io.*;
import java.nio.charset.StandardCharsets;

/** 长度前缀二进制协议；所有长度在分配前验证，用户数据不参与控制帧解析。 */
public final class Protocol {
    /** 工具类不实例化。 */
    private Protocol() {}

    /**
     * 从控制输入读取一次提交，解析执行身份、源码、资源预算和有序输入。 先限制整帧大小，再限制字符串、用例数量及预算范围，防止超大分配。
     * 返回完整请求对象；类型错误、字段越界、截断或尾部多余数据均抛出协议异常。
     */
    /**
     * [请求内容长度 length：4 字节]
     * [请求内容：
     *     请求类型：4 字节
     *     attempt：由 writeText 编码
     *     source：由 writeText 编码
     *     cpuMillis：8 字节
     *     memoryBytes：8 字节
     *     totalMillis：8 字节
     *     测试用例数量 count：4 字节
     *     第 1 个输入：由 writeText 编码
     *     第 2 个输入：由 writeText 编码
     *     ...
     * ]
     */
    public static RunRequest request(DataInputStream input) throws IOException {
        // 数据帧就是一整条消息；先读消息长度，再读取对应字节，避免把两条消息混在一起。
        int length = input.readInt();
        if (length < 4 || length > 4 * 1024 * 1024) {
            throw new IOException("请求数据帧长度超出限制");
        }
        // 完整读取指定长度，短帧不能被当成缺省值继续执行。
        byte[] bytes = new byte[length];
        // 	连续读取，直到填满指定数组；读不够会抛异常
        input.readFully(bytes);
        DataInputStream frame = new DataInputStream(new ByteArrayInputStream(bytes));
        if (frame.readInt() != 10) {
            throw new IOException("请求类型无效");
        }
        RunRequest request = new RunRequest();
        request.attempt = text(frame, 128);
        request.source = text(frame, 262144);
        request.cpuMillis = frame.readLong();
        request.memoryBytes = frame.readLong();
        request.totalMillis = frame.readLong();
        int count = frame.readInt();
        if (count < 1 || count > 128 || request.cpuMillis < 1
                || request.cpuMillis > 60000 || request.memoryBytes < 16777216
                || request.memoryBytes > 268435456 || request.totalMillis < 1000
                || request.totalMillis > 600000) {
            throw new IOException("测试用例数量或资源限制无效");
        }
        request.inputs = new String[count];
        for (int i = 0; i < count; i++) {
            request.inputs[i] = text(frame, 16384);
        }
        if (frame.available() != 0) {
            throw new IOException("请求包含多余数据");
        }
        return request;
    }

    /** 先校验非负长度及调用方指定的字节上限，再分配并解码字符串；非法长度或截断输入抛出异常。 */
    private static String text(DataInputStream input, int maximum) throws IOException {
        // 输入中的长度不能直接用于分配数组，先检查范围，避免损坏请求耗尽管理程序内存。
        int length = input.readInt();
        if (length < 0 || length > maximum) {
            throw new IOException("字符串字节长度超出限制");
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** 写入片段长度及内容；调用方负责保证片段有界，发送入口另行限制整帧大小。 */
    public static void bytes(DataOutputStream output, byte[] value) throws IOException {
        // 接收方先读取字节数，才能准确判断当前字段在哪里结束。
        output.writeInt(value.length); // 空数组的话这里又是写入0这个数据 int类型 算4个字节
        output.write(value);// 数组为空算没数据 0字节
    }

    /** 检查单帧大小后写入帧长和载荷并刷新输出，供后端按帧解析；阻塞发送期限由独立监督进程约束。 */
    public static void send(DataOutputStream output, byte[] frame) throws IOException {
        if (frame.length > 262144) {
            throw new IOException("结果数据帧大小超出限制");
        }
        output.writeInt(frame.length); // 计算一个长度 又是4个字节
        output.write(frame); // 没有内容 就不算字节
        output.flush();
    }
}
