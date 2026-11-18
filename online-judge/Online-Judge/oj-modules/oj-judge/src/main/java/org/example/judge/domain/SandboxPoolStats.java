package org.example.judge.domain;

import lombok.Data;

/** 环境计账观测值，不能用就绪队列长度代替总资源占用。 */
@Data
public class SandboxPoolStats {
    /** 未关闭执行环境总数，包含正常环境和仍待完整回收的环境。 */
    private int total;

    /** 已预留但尚未进入就绪状态的环境数量。 */
    private int creating;

    /** 当前已验证并等待独占领取的环境数量。 */
    private int ready;

    /** 已独占领取的环境数量，领取不代表任务结果已完成。 */
    private int leased;

    /** 已退役且尚未完成全部资源回收的环境数量。 */
    private int recycling;

    /** 完整执行环境总额度上限，用于和当前占用量对照观察。 */
    private int hardCapacity;

    /** 将环境计数格式化为日志摘要，便于对照正常容量、待回收数量与总额度。 */
    @Override
    public String toString() {
        return "总占用="
                + total
                + "，创建中="
                + creating
                + "，已就绪="
                + ready
                + "，已领取="
                + leased
                + "，回收中="
                + recycling
                + "，总额度上限="
                + hardCapacity;
    }
}
