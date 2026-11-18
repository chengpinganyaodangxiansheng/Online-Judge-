package org.example.judge.service;

import org.example.common.mybatis.domain.JudgeOutbox;

/** 持久化事件发送入口，集中复用领取、确认、退避与失败处理。 */
public interface OutboxDeliveryService {
    /** 处理一个扫描候选：先按数据库状态竞争发送资格，再在事务外发布并核对确认及退回；候选可能已失效，不能直接视为已经领取。 */
    void deliver(JudgeOutbox candidate);
}
