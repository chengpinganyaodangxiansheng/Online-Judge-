package org.example.common.mybatis.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.common.mybatis.domain.JudgeReliabilityStats;

/** 只读统计；Outbox 仅统计 JudgeRequested，不把待接入的排名事件算成判题积压。 */
@Mapper
public interface JudgeReliabilityStatsMapper {
    /** 一次读取判题事件积压、过期租约及失败数量，返回监控快照；查询失败向上抛出，不能解释为零积压。 */
    @Select("SELECT "
                    + "(SELECT COUNT(*) FROM tb_judge_outbox WHERE state='PENDING' AND event_type='JudgeRequested') AS pending_events,"
                    + "(SELECT COUNT(*) FROM tb_judge_outbox WHERE state='INFLIGHT' AND event_type='JudgeRequested') AS inflight_events,"
                    + "(SELECT COUNT(*) FROM tb_judge_outbox WHERE state='DEAD' AND event_type='JudgeRequested') AS dead_events,"
                    + "(SELECT COUNT(*) FROM tb_judge_task WHERE state='RUNNING' AND lease_until<=NOW(6)) AS expired_leases,"
                    + "(SELECT COUNT(*) FROM tb_judge_task WHERE state='RETRY_WAIT') AS retry_waiting,"
                    + "(SELECT COUNT(*) FROM tb_judge_task WHERE state='SYSTEM_FAILED') AS failed_tasks")
    JudgeReliabilityStats current();
}
