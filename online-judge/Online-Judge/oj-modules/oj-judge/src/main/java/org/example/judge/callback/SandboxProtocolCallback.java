package org.example.judge.callback;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Docker attach 增量解帧：先校验长度再分配，最多保留4条有界帧。 */
@Slf4j
public class SandboxProtocolCallback extends ResultCallback.Adapter<Frame> {
    /** 最多保存4个完整协议帧的有界队列，慢消费者不能造成无限内存积压。 */
    private final ArrayBlockingQueue<byte[]> frames = new ArrayBlockingQueue<>(4);

    /** 四字节帧长头缓冲，用于在分配载荷之前验证长度。 */
    private final byte[] header = new byte[4];

    /** 当前已接收的帧长头字节数，用于处理头部跨回调分片。 */
    private int headerSize;

    /** 已通过长度校验后分配的当前帧载荷缓冲，填满后才放入完整帧队列。 */
    private byte[] payload;

    /** 当前协议帧载荷已写入的位置，用于跨回调拼接剩余字节。 */
    private int position;

    /** 协议或连接故障记录，已有完整帧交付后向调用方报告，避免静默丢失证据。 */
    private volatile Throwable failure;

    /** 底层输出流是否已结束，队列已有证据仍可先交付给接收方。 */
    private volatile boolean completed;

    /** Docker 分块边界与协议帧无关，跨块累积并限制单帧及队列大小。 */
    @Override
    public synchronized void onNext(Frame frame) {
        if (failure != null) {
            return;
        }

        if (frame.getStreamType() != StreamType.STDOUT) {
            onError(new IOException("可信执行器产生了非预期的标准错误输出"));
            return;
        }
        // 一次网络回调可能只有半条消息，也可能含多条消息，所以逐字节推进解析状态。
        // 成员字段保留进度，下次回调接着读，不能把一次回调误当成一条完整业务消息。
        for (byte value : frame.getPayload()) {
            if (payload == null) {
                // 尚未开始读取正文时，先凑齐四字节的长度头。
                header[headerSize++] = value;
                if (headerSize == 4) {
                    int length = ByteBuffer.wrap(header).getInt();
                    // 长度合法才创建正文数组，避免异常执行器用巨大长度耗尽后端内存。
                    if (length < 8 || length > 262144) {
                        onError(new IOException("执行器数据帧长度超限"));
                        return;
                    }
                    payload = new byte[length];
                    position = 0;
                    headerSize = 0;
                }
            } else {
                payload[position++] = value;
                if (position == payload.length) {
                    // 正文收齐后尝试放入有界队列。队列满就报告通信故障，不无限排队占内存。
                    if (!frames.offer(payload)) {
                        onError(new IOException("执行器接收队列已满"));
                        return;
                    }
                    // 清空正文引用，下一字节重新按下一条消息的长度头处理。
                    payload = null;
                }
            }
        }
    }

    /** 传输失败不可变为用户 OLE，后端会停止本轮执行。 */
    @Override
    public void onError(Throwable error) {
        // 先记录原始故障，再关闭传输；接收方读取完已经收齐的证据后会看到这个故障。
        failure = error;
        try {
            close();
        } catch (IOException closeFailure) {
            // 保留原始通信故障供消费者判定，关闭失败另行记录，不能覆盖首个失败原因。
            log.warn("执行器通信失败后关闭连接仍失败，保留原始故障并等待资源回收", closeFailure);
        }
    }

    /** 断流不重连；已收到完整帧仍可读取，半帧不能形成有效结果。 */
    @Override
    public void onComplete() {
        completed = true;
    }

    /** 有限等待完整帧，失败和断流必须显式上报。 */
    public byte[] receive(long milliseconds) throws InterruptedException {
        // 先交付故障发生前已完整接收的证据，后续断流不能覆盖此前已确认的首个失败。
        byte[] confirmed = frames.poll();
        if (confirmed != null) {
            return confirmed;
        }
        if (failure != null) {
            throw new IllegalStateException("执行器通信协议处理失败", failure);
        }
        // 最多等待调用方指定的时间。单纯超时可以重试，但断流或故障必须显式报告。
        byte[] frame = frames.poll(milliseconds, TimeUnit.MILLISECONDS);
        if (frame == null && failure != null) {
            throw new IllegalStateException("执行器通信协议处理失败", failure);
        }
        if (frame == null && completed) {
            throw new IllegalStateException("执行器连接已断开");
        }
        return frame;
    }
}
