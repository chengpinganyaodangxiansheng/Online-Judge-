package org.example.judge.callback;

import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 有界收集 Docker 输出，stdout/stderr 共用字节预算，避免恶意输出耗尽 Judge 内存。 */
public class BoundedOutputCallback extends ExecStartResultCallback {
    /** 接收端允许保留的输出字节上限，超出后停止继续累积。 */
    private final int limit;

    /** 限额内收集的标准输出缓冲，与错误输出共同受接收端总预算约束。 */
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    /** 限额内收集的标准错误缓冲，与标准输出共同受接收端总预算约束。 */
    private final ByteArrayOutputStream error = new ByteArrayOutputStream();

    /** 输出累计量是否已超过限额，供调用方拒绝使用截断结果。 */
    private volatile boolean exceeded;

    /** 阶段输出结束通知，供等待方有期限地等待回调完成。 */
    private final CountDownLatch completion = new CountDownLatch(1);

    /** 保存接收端总输出字节限额，后续标准输出和错误输出共用此预算。 */
    public BoundedOutputCallback(int limit) {
        this.limit = limit;
    }

    /** 在同一把锁内计算剩余预算并截断写入，错误流也不能绕过总输出限制。 */
    @Override
    public synchronized void onNext(Frame frame) {
        // 两种输出共同扣减预算，不能通过切换输出通道绕过限制；同步锁保护并发更新的累计大小。
        int remaining = Math.max(0, limit - output.size() - error.size());
        byte[] data = frame.getPayload();
        // 恰好填满预算仍合法，只有新帧超出剩余额度才标记超限；多余字节丢弃，不再扩大缓冲区。
        if (data.length > remaining) {
            exceeded = true;
        }
        ByteArrayOutputStream stream = frame.getStreamType() == StreamType.STDERR ? error : output;
        stream.write(data, 0, Math.min(data.length, remaining));
    }

    /** 正常结束时先让父类关闭流，再唤醒阶段等待者；finally 保证清理异常时也能解除等待。 */
    @Override
    public void onComplete() {
        try {
            super.onComplete();
        } finally {
            completion.countDown();
        }
    }

    /** 父类保存流错误后唤醒等待者，awaitStage 会重新抛出，不能误认为正常结束。 */
    @Override
    public void onError(Throwable failure) {
        try {
            super.onError(failure);
        } finally {
            completion.countDown();
        }
    }

    /**
     * 短暂超时只返回 false，不关闭 Docker 输出流，调用方可以继续等待并检查执行权。
     * 使用独立 latch 避免父类限时等待关闭流；流异常必须上抛，不能当作正常进程结束。
     */
    public boolean awaitStage(long timeout, TimeUnit unit) throws InterruptedException {
        // 等到通知后还要检查流是否异常结束，不能把“结束”直接等同于“执行成功”。
        boolean done = completion.await(timeout, unit);
        throwFirstError();
        return done;
    }

    /** 返回输出是否超限，调用方据此拒绝把截断内容当作正常结果。 */
    public boolean isExceeded() {
        return exceeded;
    }

    /** 在同步保护下返回已保留的标准输出文本，不继续读取底层流。 */
    public synchronized String output() {
        return output.toString(StandardCharsets.UTF_8);
    }

    /** 在同步保护下返回已保留的错误输出文本，仅供有界诊断使用。 */
    public synchronized String error() {
        return error.toString(StandardCharsets.UTF_8);
    }
}
