package org.example.judge.domain;

import lombok.Getter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 单次执行的本地控制对象，连接消费线程、续租线程和看门狗；数据库令牌仍是最终写入依据。 */
@Getter
public class JudgeExecution {
    /** 本轮独占容器的编号，供执行、终止及退役回收使用。 */
    private final String containerId;

    /** 使用单调时钟计算本地保护期限，避免系统墙上时钟调整影响等待时长。 */
    private final long deadline;

    /** 本地执行是否尚未撤销的原子标志，协调消费线程、续租线程和看门狗的并发终止。 */
    private final AtomicBoolean active = new AtomicBoolean(true);

    /** 绑定本轮容器，并把秒数转换为单调时钟截止点；后续续租不会延长此保护期限。 */
    public JudgeExecution(String containerId, int seconds) {
        // 绑定本轮已经领取的容器，后续发送、终止和回收都使用这个编号。
        this.containerId = containerId;
        // 把秒数转换为纳秒并加到当前单调时钟上，得到本次执行固定的截止点。
        this.deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
    }

    /** 未被主动撤销且单调时钟未到固定期限才有效；满足一个条件不足以继续执行。 */
    public boolean isActive() {
        // 必须同时满足“没有被取消”和“当前时间早于截止点”；例如租约丢失后即使时间未到也失效。
        return active.get() && System.nanoTime() < deadline;
    }

    /** 每个沙箱阶段与等待循环都检查撤销标志，使租约丢失及时中断执行。 */
    public void checkActive() {
        // 执行权失效或线程收到中断，任一出现就拒绝启动下一阶段，交由上层记录恢复安排。
        if (!isActive() || Thread.currentThread().isInterrupted()) {
            // 取消、超时或线程中断任意一个发生，都向调用方报告失去执行资格。
            throw new IllegalStateException("执行权已失效");
        }
    }

    /** 原子撤销本地执行资格并返回撤销前状态，只有首次调用返回是；不修改数据库令牌，也不表示容器或附属资源已经回收。 */
    public boolean cancel() {
        // 把 active 改成 false。返回修改之前的值。
        return active.getAndSet(false);
    }
}
