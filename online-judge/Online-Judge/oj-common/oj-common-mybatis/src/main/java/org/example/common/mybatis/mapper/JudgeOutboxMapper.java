package org.example.common.mybatis.mapper;

import org.apache.ibatis.annotations.*;
import org.example.common.mybatis.domain.JudgeOutbox;
import java.util.List;

/** Outbox 投递状态及领取令牌管理；网络发送本身不放在数据库事务内。 */
@Mapper
public interface JudgeOutboxMapper {
    // 本接口只操作持久化消息表，不直接发送消息；网络发布必须在领取事务提交之后进行。
    // 发送领取令牌只标识一轮发布，不能替代任务执行令牌；回调必须携带当时保存的令牌。
    /** 排名事件与成绩一起提交，当前判题投递扫描不会发送此类型事件。 */
    @Insert("INSERT INTO tb_judge_outbox(event_id,submit_id,dispatch_seq,event_type,payload) VALUES(#{id},#{submit},1,'RankChanged',#{payload})")
    int rankChanged(@Param("id") String id, @Param("submit") Long submit, @Param("payload") String payload);

    /** 由受理或恢复事务写入，默认类型为 JudgeRequested、状态为 PENDING。 */
    @Insert("INSERT INTO tb_judge_outbox(event_id,submit_id,dispatch_seq,payload) VALUES(#{eventId},#{submitId},#{dispatchSeq},#{payload})")
    int insert(JudgeOutbox event);

    /**
     * 仅扫描 JudgeRequested，且满足以下三组任一组：
     * PENDING 并已到发送时间；INFLIGHT 并已过领取期限；DEAD 但同任务同代次仍处于 RECEIVED。
     * 第三组不是重发 DEAD 消息，而是补记任务投递失败；任务已执行或进入新代次时不再选中。
     * 返回值只是候选，实际处理时还要原子校验，避免多实例扫描后重复领取。
     */
    @Select("SELECT * FROM tb_judge_outbox WHERE event_type='JudgeRequested' AND ((state='PENDING' AND next_retry_at<=NOW(6)) OR "
                    + "(state='INFLIGHT' AND claim_until<=NOW(6)) OR (state='DEAD' AND EXISTS "
                    + "(SELECT 1 FROM tb_judge_task t WHERE t.submit_id=tb_judge_outbox.submit_id AND t.dispatch_seq=tb_judge_outbox.dispatch_seq AND t.state='RECEIVED'))) ORDER BY created_at LIMIT #{limit}")
    List<JudgeOutbox> candidates(int limit);

    /** 每次实际领取扣一次发送预算，唯一令牌隔离不同发布者的迟到回调。 */
    @Update("UPDATE tb_judge_outbox SET state='INFLIGHT',claim_token=#{token},claim_until=TIMESTAMPADD(SECOND,#{seconds},NOW(6)),send_count=send_count+1 "
                    + "WHERE event_id=#{id} AND state='PENDING' AND next_retry_at<=NOW(6) AND send_count<#{max}")
    int claim(@Param("id") String id, @Param("token") String token, @Param("seconds") int seconds, @Param("max") int max);

    /** 按事件编号读取持久化归属和载荷；不存在返回 null，不能把未经落库的消息当有效任务。 */
    @Select("SELECT * FROM tb_judge_outbox WHERE event_id=#{id}")
    JudgeOutbox find(String id);

    /** 仅本轮发布者可以写成功，已被回收或重新领取后旧确认不再生效。 */
    @Update("UPDATE tb_judge_outbox SET state='SENT',claim_until=NULL WHERE event_id=#{id} AND state='INFLIGHT' AND claim_token=#{token}")
    int sent(@Param("id") String id, @Param("token") String token);

    /** 未确认发送按指数退避重试，最多等待 300 秒；预算耗尽进入 DEAD 供人工排查。 */
    @Update("UPDATE tb_judge_outbox SET state=IF(send_count>=#{max},'DEAD','PENDING'),failure_reason=#{reason},"
                    + "next_retry_at=TIMESTAMPADD(SECOND,LEAST(300,POW(2,send_count)),NOW(6)),claim_until=NULL "
                    + "WHERE event_id=#{id} AND state='INFLIGHT' AND claim_token=#{token}")
    int failed(@Param("id") String id, @Param("token") String token, @Param("max") int max, @Param("reason") String reason);

    /** 发布者宕机后回收过期领取并清除令牌，仍沿用原事件编号和载荷重试。 */
    @Update("UPDATE tb_judge_outbox SET state=IF(send_count>=#{max},'DEAD','PENDING'),failure_reason='CLAIM_TIMEOUT',"
                    + "next_retry_at=TIMESTAMPADD(SECOND,LEAST(300,POW(2,send_count)),NOW(6)),claim_token=NULL,claim_until=NULL "
                    + "WHERE event_id=#{id} AND state='INFLIGHT' AND claim_until<=NOW(6)")
    int recover(@Param("id") String id, @Param("max") int max);
}
