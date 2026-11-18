package org.example.common.mybatis.mapper;

import org.apache.ibatis.annotations.*;
import org.example.common.mybatis.domain.JudgeTask;
import java.util.List;

/**
 * 任务状态机的持久化边界：更新影响行数为 1 才表示本次操作取得了资格。
 * 有效期统一使用数据库时间，避免不同服务的本机时钟偏差影响并发判断。
 */
@Mapper
public interface JudgeTaskMapper {
    // 所有期限比较以数据库时间为准；不能仅凭之前读出的对象认定仍有执行资格。
    // 条件更新返回零表示状态迁移未获准，不能忽略返回值继续写结果或启动用户程序。
    // 多条写入之间的原子性由上层短事务保证，单条语句不代表整个业务流程已经完成。
    /** 仅系统失败可人工重试；递增代次隔离旧消息，retry_base 保留历史并开启新预算。 */
    @Update("UPDATE tb_judge_task SET state='RECEIVED',dispatch_seq=dispatch_seq+1,retry_base=attempt_count,"
                    + "run_token=NULL,lease_until=NULL,finished_at=NULL,failure_reason=NULL,next_retry_at=NULL,"
                    + "queue_deadline=TIMESTAMPADD(SECOND,#{queue},NOW(6)) WHERE submit_id=#{id} AND state='SYSTEM_FAILED'")
    int manualRetry(@Param("id") Long id, @Param("queue") int queue);

    /** 与人工重试同事务记录操作人和原因，保留每次重派发的追溯依据。 */
    @Insert("INSERT INTO tb_judge_retry_audit(submit_id,operator_id,reason,dispatch_seq) VALUES(#{id},#{operator},#{reason},#{seq})")
    int auditRetry(
            @Param("id") Long id,
            @Param("operator") Long operator,
            @Param("reason") String reason,
            @Param("seq") int seq);

    /** 幂等键只在同一用户内唯一，不允许跨用户复用任务。 */
    @Select("SELECT * FROM tb_judge_task WHERE user_id=#{userId} AND idempotency_key=#{key}")
    JudgeTask findRequest(@Param("userId") Long userId, @Param("key") String key);

    /** 普通读取任务，不加业务行锁；不存在返回 null，读取成功不代表取得执行权。 */
    @Select("SELECT * FROM tb_judge_task WHERE submit_id=#{id}")
    JudgeTask find(Long id);

    /** 必须在业务事务内调用，行锁覆盖后续状态检查和写入。 */
    @Select("SELECT * FROM tb_judge_task WHERE submit_id=#{id} FOR UPDATE")
    JudgeTask lock(Long id);

    /** 首次受理即开始排队计时，覆盖 Outbox、MQ 和本地资源等待。 */
    @Insert("INSERT INTO tb_judge_task(submit_id,user_id,question_id,exam_id,idempotency_key,request_hash,state,queue_deadline) "
                    + "VALUES(#{task.submitId},#{task.userId},#{task.questionId},#{task.examId},#{task.idempotencyKey},#{task.requestHash},'RECEIVED',TIMESTAMPADD(SECOND,#{queueSeconds},NOW(6)))")
    int insert(@Param("task") JudgeTask task, @Param("queueSeconds") int queueSeconds);

    /** 快照只插入不更新，自动重试和人工重试使用同一份规则及代码。 */
    @Insert("INSERT INTO tb_judge_snapshot(submit_id,snapshot_json) VALUES(#{id},#{json})")
    int snapshot(@Param("id") Long id, @Param("json") String json);

    /** 读取首次受理的固定执行规则，缺失时由消费者按非法快照处理，不用最新题目补造。 */
    @Select("SELECT snapshot_json FROM tb_judge_snapshot WHERE submit_id=#{id}")
    String snapshotJson(Long id);

    /** 同一代次仅一个消费者可把 RECEIVED 改为 RUNNING；过期排队任务不能开始执行。 */
    @Update("UPDATE tb_judge_task SET state='RUNNING',run_token=#{token},worker_id=#{worker},attempt_count=attempt_count+1,"
                    + "lease_until=TIMESTAMPADD(SECOND,#{lease},NOW(6)),execution_deadline=TIMESTAMPADD(SECOND,#{protection},NOW(6)) WHERE submit_id=#{id} AND state='RECEIVED' AND dispatch_seq=#{seq} AND queue_deadline>NOW(6)")
    int claim(@Param("id") Long id, @Param("seq") int seq, @Param("token") String token, @Param("worker") String worker, @Param("lease") int lease, @Param("protection") int protection);

    /** 领取成功后在同一事务保存实际执行历史，不把重复消息计作一次执行。 */
    @Insert("INSERT INTO tb_judge_attempt(submit_id,attempt_no,dispatch_seq,run_token,worker_id) "
                    + "SELECT submit_id,attempt_count,dispatch_seq,run_token,worker_id FROM tb_judge_task WHERE submit_id=#{id}")
    int startAttempt(Long id);

    /**
     * 续租须同时满足：任务 RUNNING、run_token 属于本次执行、租约未过期、固定保护期限未到。
     * LEAST 取“本次续租到期时间”和“固定保护期限”的较早值，不能靠续租无限执行。
     * 返回 0 表示不再拥有有效续租资格，消费者必须撤销本地执行，不能复活旧租约。
     */
    @Update("UPDATE tb_judge_task SET lease_until=LEAST(execution_deadline,TIMESTAMPADD(SECOND,#{lease},NOW(6))) WHERE submit_id=#{id} AND state='RUNNING' AND run_token=#{token} AND lease_until>NOW(6) AND execution_deadline>NOW(6)")
    int renew(@Param("id") Long id, @Param("token") String token, @Param("lease") int lease);

    /** 状态、令牌、租约和保护期限同时匹配才接收结果，阻止重复完成和旧进程迟到写入。 */
    @Update("UPDATE tb_judge_task SET state='FINISHED',result_json=#{json},finished_at=NOW(6),failure_reason=NULL "
                    + "WHERE submit_id=#{id} AND state='RUNNING' AND run_token=#{token} AND lease_until>NOW(6) AND execution_deadline>NOW(6)")
    int finish(@Param("id") Long id, @Param("token") String token, @Param("json") String json);

    /** 同一个 attempt 只结束一次，补偿或重复回调不会重写已记录的原因。 */
    @Update("UPDATE tb_judge_attempt SET finished_at=NOW(6),outcome=#{outcome},failure_reason=#{reason} WHERE run_token=#{token} AND finished_at IS NULL")
    int endAttempt(
            @Param("token") String token,
            @Param("outcome") String outcome,
            @Param("reason") String reason);

    /**
     * 三类候选满足任一组即可进入扫描：RUNNING 且租约已过期；
     * RECEIVED/RETRY_WAIT 且排队超时；RETRY_WAIT 且退避已结束。
     * 同时满足多组也只返回一行。这里只发现候选，恢复事务须重新检查，排队超时优先终结。
     */
    @Select("SELECT submit_id FROM tb_judge_task WHERE (state='RUNNING' AND lease_until<=NOW(6)) OR "
                    + "(state IN ('RECEIVED','RETRY_WAIT') AND queue_deadline<=NOW(6)) OR (state='RETRY_WAIT' AND next_retry_at<=NOW(6)) "
                    + "ORDER BY submit_id LIMIT #{limit}")
    List<Long> recoveryCandidates(int limit);

    /** 排队超时直接终结，不继续自动重试，避免不可执行任务长期占用队列。 */
    @Update("UPDATE tb_judge_task SET state='SYSTEM_FAILED',failure_reason='QUEUE_TIMEOUT',finished_at=NOW(6) "
                    + "WHERE submit_id=#{id} AND state IN ('RECEIVED','RETRY_WAIT') AND queue_deadline<=NOW(6)")
    int expireQueue(Long id);

    /**
     * 任务必须仍为 RUNNING 且令牌匹配，再按 expired 区分两种互斥权限：
     * true 为补偿扫描，只处理 lease_until 已到期；false 为执行方报错，只处理尚未到期租约。
     * 任一前置条件不符返回 0，不覆盖新执行者或终态。成功则清除执行权、重算排队与退避期限；
     * state 为 SYSTEM_FAILED 才记录完成时间，否则保留未完成状态等待重派发。
     * 恢复逻辑：处理租约过期。  #{expired}=true AND lease_until<=NOW(6)
     * 执行者：执行出错时主动报告失败。 #{expired}=false AND lease_until>NOW(6)
     */
    @Update("UPDATE tb_judge_task SET state=#{state},failure_reason=#{reason},run_token=NULL,lease_until=NULL,"
                    + "queue_deadline=TIMESTAMPADD(SECOND,#{queue},NOW(6)),next_retry_at=TIMESTAMPADD(SECOND,#{backoff},NOW(6)),"
                    + "finished_at=IF(#{state}='SYSTEM_FAILED',NOW(6),NULL) WHERE submit_id=#{id} AND state='RUNNING' AND run_token=#{token} "
                    + "AND ((#{expired}=true AND lease_until<=NOW(6)) OR (#{expired}=false AND lease_until>NOW(6)))")
    int failAttempt(@Param("id") Long id,
            @Param("token") String token,
            @Param("state") String state,
            @Param("reason") String reason,
            @Param("queue") int queue,
            @Param("backoff") int backoff,
            @Param("expired") boolean expired);

    /** 退避结束且排队期限未到才重派发；代次比较阻止多个恢复线程重复推进。 */
    @Update("UPDATE tb_judge_task SET state='RECEIVED',dispatch_seq=dispatch_seq+1,next_retry_at=NULL "
                    + "WHERE submit_id=#{id} AND state='RETRY_WAIT' AND dispatch_seq=#{seq} AND next_retry_at<=NOW(6) AND queue_deadline>NOW(6)")
    int dispatch(@Param("id") Long id, @Param("seq") int seq);

    /** DEAD 事件只影响同代次且尚未领取的任务，不能将 RUNNING/FINISHED 回退为失败。 */
    @Update("UPDATE tb_judge_task t JOIN tb_judge_outbox o ON o.submit_id=t.submit_id AND o.dispatch_seq=t.dispatch_seq "
                    + "SET t.state='SYSTEM_FAILED',t.failure_reason='DELIVERY_FAILED',t.finished_at=NOW(6) "
                    + "WHERE o.event_id=#{eventId} AND o.state='DEAD' AND t.state='RECEIVED'")
    int deliveryFailed(String eventId);
}
