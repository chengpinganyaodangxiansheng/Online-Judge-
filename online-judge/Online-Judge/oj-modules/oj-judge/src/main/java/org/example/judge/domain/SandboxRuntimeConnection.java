package org.example.judge.domain;

import org.example.judge.callback.SandboxProtocolCallback;
import java.io.*;

/** 单容器管理连接持有的有界管道与解帧器，不包含业务判题逻辑。 */
public class SandboxRuntimeConnection implements Closeable {
    /** 交给容器连接读取的有界管道输入端，缓冲容量为65536字节。 */
    public final PipedInputStream input = new PipedInputStream(65536);

    /** 后端写入提交协议的管道输出端，与输入端配对传输请求。 */
    public final PipedOutputStream sender;

    /** 容器输出的协议解析回调，负责分片拼接、长度限制和完整帧排队。 */
    public final SandboxProtocolCallback callback = new SandboxProtocolCallback();

    /** 把发送端接到固定容量输入管道，后端写入的数据由容器连接读取；管道无法连接时抛出异常，不产生可用连接。 */
    public SandboxRuntimeConnection() throws IOException {
        // 把输出端接到上面已经分配的输入端；后端向发送端写字节，容器连接从输入端读取。
        // 例如提交源码编码后写入发送端，字节先经过最多六十四千字节的管道缓冲再传向容器。
        // 这行是在把“写入端”和“读取端”连接成一根管道。之后写进 sender 的字节，就能从 input 读出来。
        sender = new PipedOutputStream(input);
    }

    /** 依次关闭协议回调、发送端和输入端；嵌套收尾保证前一步抛出异常时仍尝试后续关闭，异常交给上层保留回收追踪。 */
    @Override
    public void close() throws IOException {
        // 三项关闭互相独立：前一个关闭抛异常，后两个仍必须尝试，避免只关闭回调却泄漏管道。
        try {
            // 先结束接收回调及其连接，使它不再继续向本地结果队列写入消息。
            callback.close();
        } finally {
            try {
                // 关闭请求发送端，通知读端不会再有后续数据。
                sender.close();
            } finally {
                // 即使前两步失败，也尝试释放输入端缓冲和管道资源。
                input.close();
            }
        }
    }
}
