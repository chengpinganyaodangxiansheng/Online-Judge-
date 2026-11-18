package org.example.judge.job;

import lombok.extern.slf4j.Slf4j;
import org.example.common.mybatis.domain.JudgeTask;
import org.example.common.mybatis.mapper.JudgeTaskMapper;
import org.example.common.mybatis.mapper.JudgeOutboxMapper;
import org.example.common.mybatis.domain.JudgeOutbox;
import org.example.common.mybatis.domain.JudgeReliabilityStats;
import org.example.common.mybatis.mapper.JudgeReliabilityStatsMapper;
import org.example.judge.config.DockerSandBoxPool;
import org.example.judge.service.JudgeTaskService;
import org.example.judge.service.OutboxDeliveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/** 定时入口仅发现候选并委托业务服务；可重复扫描，正确性依赖数据库条件更新。 */
@Slf4j
@Component
public class JudgeRecoveryJob {
    /** 任务数据库访问对象，用于查询和条件推进可靠判题任务。 */
    @Autowired private JudgeTaskMapper taskMapper;

    /** 本地消息表访问对象，用于核对事件领取、重发及发布状态。 */
    @Autowired private JudgeOutboxMapper outboxMapper;

    /** 任务状态迁移服务，以短事务处理领取、完成、失败和故障恢复。 */
    @Autowired private JudgeTaskService taskService;

    /** 事件投递服务，集中执行条件领取、事务外发送和令牌校验更新。 */
    @Autowired private OutboxDeliveryService deliveryService;

    /** 可靠性统计访问对象，用于核对积压、租约过期及重试数量。 */
    @Autowired private JudgeReliabilityStatsMapper statsMapper;

    /** 独立有界发布线程池，限制并发发送数及等待发送的内存任务数量。 */
    @Autowired private ExecutorService outboxPublishExecutor;

    /** 一次性就绪容器池，提供领取、退役、回收和可用资源探测。 */
    @Autowired private DockerSandBoxPool dockerPool;

    /** 单轮定时扫描最多处理的候选数量，避免一次扫描占用过多资源。 */
    @Value("${judge.task.scan-batch:20}")
    private int batch;

    /** 回收已使用或失效的执行环境，使等待资源的消费者有机会恢复。 */
    @Scheduled(fixedDelayString = "${judge.task.recycle-ms:2000}")
    public void recycle() {
        // 执行一轮退役资源回收，未清理成功的环境仍保留记录，等待后续定时重试。
        dockerPool.recoverRetired();
    }

    /** 补池与回收分开调度，始终遵守数据库中的正常缺口与硬额度。 */
    @Scheduled(fixedDelayString = "${sandbox.docker.pool.replenish-ms:2000}")
    public void replenish() {
        // 尝试申请一份额度并建立就绪环境；容量已够或总额度已满时本轮不创建。
        dockerPool.replenish();
    }

    /** 只记录状态计数，不记录源码和隐藏用例；终态失败需管理员排查。 */
    @Scheduled(fixedDelayString = "${judge.task.metrics-ms:30000}")
    public void report() {
        // 同时观察环境占用和任务积压，帮助区分“没有执行资源”和“消息或任务恢复异常”。
        // 日志只记录数量与状态，不把源码、隐藏用例等大块内容输出。
        log.info("沙箱环境资源统计：{}", dockerPool.statistics());
        JudgeReliabilityStats stats = statsMapper.current();
        // 输出数据库中各类积压数量，便于定位投递、执行租约或重试阶段的问题。
        log.info("判题可靠性统计：待投递事件数={}，投递中事件数={}，死信事件数={}，过期租约数={}，等待重试任务数={}，失败任务数={}",
                stats.getPendingEvents(),
                stats.getInflightEvents(),
                stats.getDeadEvents(),
                stats.getExpiredLeases(),
                stats.getRetryWaiting(),
                stats.getFailedTasks());
        if (stats.getDeadEvents() > 0 || stats.getFailedTasks() > 0) {
            // 死信事件或最终失败任务任一存在，都输出告警，提示需要排查自动恢复未能解决的问题。
            log.warn("判题故障需要排查：死信事件数={}，失败任务数={}", stats.getDeadEvents(), stats.getFailedTasks());
        }
    }

    /** 批量提交发送任务，真正领取发生在工作线程，线程池满时不消耗数据库发送次数。
     * fixedDelayString: 表示本次 publish 返回后，再等待配置间隔触发下一次。
     * */
    @Scheduled(fixedDelayString = "${judge.outbox.scan-ms:1000}")
    public void publish() {
        // 扫描得到的只是候选，其他发布者可能已经处理它；工作线程里的投递服务还要竞争领取。
        // 网络发布可能等待确认，所以放到独立有界线程池，避免拖住定时扫描线程。
        for (JudgeOutbox event : outboxMapper.candidates(batch)) {
            // 每轮最多读取配置批量的候选事件，逐个提交到线程池；候选此时还没有取得投递权。
            try {
                outboxPublishExecutor.execute(
                        // 这段代码在发布工作线程执行，定时扫描线程提交后可以继续处理下一候选。
                        () -> {
                            try {
                                deliveryService.deliver(event);
                            } catch (RuntimeException exception) {
                                // 单条投递失败只记录延后，让数据库状态和后续扫描负责恢复。
                                log.warn("本地消息表事件投递已延后，事件编号={}", event.getEventId());
                            }
                        });
            } catch (RejectedExecutionException busy) {
                // 候选尚未领取，保留数据库发送机会，后续扫描继续处理。
                break;
            }
        }
    }

    /** 每个任务独立事务恢复，单条失败不阻断本批其他任务，下次扫描继续处理。 */
    @Scheduled(fixedDelayString = "${judge.task.scan-ms:2000}")
    public void recover() {
        // 每个候选任务分别调用事务服务，按数据库当前状态决定是否恢复。
        // 单条失败只记录日志，其他任务继续检查，失败项留给下一轮扫描。
        for (Long id : taskMapper.recoveryCandidates(batch)) {
            // 逐个检查可能需要恢复的任务；查询只是发现候选，实际更新还要由服务核对当前状态。
            try {
                // 每条任务通过独立事务处理，避免把整批任务绑定到一个长事务。
                taskService.recover(id);
                // 恢复后重新查询，以数据库当前记录为准输出检查结果。
                JudgeTask task = taskMapper.find(id);
                if (task != null) {
                    // 记录仍存在才读取字段，避免查询期间记录消失导致空值访问。
                    log.info("判题恢复检查：提交编号={}，状态={}，派发代次={}，执行次数={}，工作进程编号={}，原因={}",
                            id,
                            task.getState(),
                            task.getDispatchSeq(),
                            task.getAttemptCount(),
                            task.getWorkerId(),
                            task.getFailureReason());
                }
            } catch (RuntimeException exception) {
                // 当前任务失败后记录编号，本循环继续下一任务，后续扫描仍可重试失败项。
                log.warn("任务恢复已延后，提交编号={}", id);
            }
        }
    }
}
