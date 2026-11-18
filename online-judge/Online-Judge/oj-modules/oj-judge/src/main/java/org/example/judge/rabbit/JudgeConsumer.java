package org.example.judge.rabbit;

import com.alibaba.fastjson2.JSONException;
import lombok.extern.slf4j.Slf4j;
import com.alibaba.fastjson2.JSON;
import com.rabbitmq.client.Channel;
import org.example.api.domain.dto.JudgeRequestedDTO;
import org.example.api.domain.vo.UserQuestionResultVO;
import org.example.common.mybatis.domain.JudgeTask;
import org.example.common.mybatis.mapper.JudgeTaskMapper;
import org.example.judge.config.JudgeTaskConfig;
import org.example.judge.config.DockerSandBoxPool;
import org.example.judge.domain.JudgeExecution;
import org.example.judge.service.JudgeTaskService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.example.api.domain.dto.JudgeSubmitDTO;
import org.example.judge.service.JudgeService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Judge 服务的异步执行入口：先取得资源与执行权，后运行快照，持久化结果或补偿后才 ACK。 */
@Slf4j
@Component
public class JudgeConsumer {

    /** 判题业务服务，使用沙箱执行快照并将执行证据转换为业务结果。 */
    @Autowired
    private JudgeService judgeService;

    /** 任务状态迁移服务，以短事务处理领取、完成、失败和故障恢复。 */
    @Autowired
    private JudgeTaskService taskService;

    /** 任务数据库访问对象，用于查询和条件推进可靠判题任务。 */
    @Autowired
    private JudgeTaskMapper taskMapper;

    /** 独立调度线程池，用于续租、固定期限看门狗和消费恢复探测。 */
    @Autowired
    private ScheduledExecutorService judgeLeaseExecutor;

    /** 消息监听容器注册器，用于找到真实消费入口并暂停或恢复消费。 */
    @Autowired
    private RabbitListenerEndpointRegistry registry;

    /** 一次性就绪容器池，提供领取、退役、回收和可用资源探测。 */
    @Autowired
    private DockerSandBoxPool dockerPool;

    /** 消费者在本地等待就绪资源的最长秒数，到期后暂停入口而不是直接判任务失败。 */
    @Value("${judge.task.resource-wait-seconds:10}")
    private int resourceWaitSeconds;

    /** 执行租约的续期长度，单位为秒，续租仍受固定保护期限约束。 */
    @Value("${judge.task.lease-seconds:30}")
    private int leaseSeconds;

    /** 单轮执行的固定保护期限，单位为秒，不随续租无限延长。 */
    @Value("${judge.task.protection-seconds:600}")
    private int protectionSeconds;

    /** 消费暂停状态的原子标志，避免多个故障回调重复启动暂停流程。 */
    private final AtomicBoolean paused = new AtomicBoolean();

    /** 手动确认消息；数据库故障时保留未确认消息，通过暂停消费和连接关闭后的重投恢复。
     * id 是监听器的注册名称，后面暂停和恢复时用它查找监听容器。
     * queues 指定业务队列。
     * containerFactory 指定监听器配置工厂。
     * raw 是收到的消息及其属性。
     * channel 用来向 RabbitMQ 发确认或拒绝。
     * */
    @RabbitListener(id = "reliableJudge", queues = JudgeTaskConfig.QUEUE, containerFactory = "judgeListenerFactory")
    public void consume(Message raw, Channel channel) throws IOException {
        // tag 是此次投递在当前通道上的标识，用来 ACK/reject；它不是 submitId，也不是数据库 eventId。
        long tag = raw.getMessageProperties().getDeliveryTag();
        JudgeRequestedDTO message;
        try {
            // 无法识别的消息拒绝且不重新入队，避免毒消息不断占用消费线程。
            message = JSON.parseObject(raw.getBody(), JudgeRequestedDTO.class);
            // 事件编号定位 Outbox，提交编号定位任务，派发代次过滤旧消息；对象或任一定位字段缺失都拒绝。
            if (message == null || message.getSubmitId() == null || message.getDispatchSeq() == null || message.getEventId() == null) {
                throw new IllegalArgumentException("事件数据无效");
            }
        } catch (RuntimeException invalid) {
            // 拒绝这一条投递，`false` 表示不要求重新入队。
            channel.basicReject(tag, false);
            return;
        }
        JudgeTask task;
        long resourceWaitStarted = System.nanoTime();
        String container = null;
        try {
            // 等待资源期间不占用任务租约；每轮仍校验排队截止时间和派发代次。
            long waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(resourceWaitSeconds);
            do {
                // ready=false 表示此消息不再需要执行，且必要的过期恢复已落库，可以 ACK。
                // ready 抛数据库异常则不能走 ACK，外层会暂停消费以保留消息。
                // 第一次到这里的outbox的状态应该是INFLIGHT, task的状态应该是RECIVCE
                if (!taskService.ready(message)) {
                    // false是关闭批量确认消息,只确认tag编号的消息
                    channel.basicAck(tag, false);
                    return;
                }
                container = dockerPool.pollContainer(250);
                // 仅“仍没有容器 AND 本轮资源等待未到期”才继续，拿到容器或等待到期任一发生即退出。
            } while (container == null && System.nanoTime() < waitUntil);
            if (container == null) {
                pauseConsumption();
                return;
            }
            // 拿到容器不等于取得执行权；数据库领取失败必须归还尚未执行用户代码的容器。
            // task的状态变成RUNNING
            task = taskService.claim(message);
        } catch (IllegalArgumentException invalid) {
            if (container != null) {
                dockerPool.returnContainer(container);
            }
            channel.basicReject(tag, false);
            return;
        } catch (RuntimeException | InterruptedException unavailable) {
            if (container != null) {
                dockerPool.returnContainer(container);
            }
            pauseConsumption();
            return;
        }
        if (task == null) {
            dockerPool.returnContainer(container);
            channel.basicAck(tag, false);
            return;
        }
        long executionStarted = System.nanoTime();
        log.info("已取得判题资源，提交编号={}，资源等待耗时（毫秒）={}",
                message.getSubmitId(),
                TimeUnit.NANOSECONDS.toMillis(executionStarted - resourceWaitStarted));
        log.info("已领取判题执行权，提交编号={}，事件编号={}，派发代次={}，执行令牌={}，工作进程编号={}，执行次数={}",
                task.getSubmitId(),
                message.getEventId(),
                task.getDispatchSeq(),
                task.getRunToken(),
                task.getWorkerId(),
                task.getAttemptCount());
        JudgeExecution execution = new JudgeExecution(container, protectionSeconds);
        Thread executingThread = Thread.currentThread();
        // 固定保护期限不随续租延长；即使业务线程卡住，也有独立调度线程撤销执行。


        ScheduledFuture<?> watchdog = judgeLeaseExecutor.schedule(() -> {
            // 延迟达到 protectionSeconds 后，由调度线程调用 stopExecution。
            stopExecution(execution, executingThread);
            }, protectionSeconds, TimeUnit.SECONDS);

        ScheduledFuture<?> renewal = judgeLeaseExecutor.scheduleAtFixedRate(() -> {
                            try {
                                if (!execution.isActive()) {
                                    return;
                                }
                                if (taskMapper.renew(task.getSubmitId(), task.getRunToken(), leaseSeconds) != 1) {
                                    stopExecution(execution, executingThread);
                                }
                            } catch (RuntimeException unavailable) {
                                stopExecution(execution, executingThread);
                            }
                        },
                        // 第一次延迟与周期都取 max(1, leaseSeconds / 3) 秒。
                        // 假设租约长度为30秒，则首次约10秒后尝试续租，之后按10秒周期安排。不是等30秒租约到期才续。
                        Math.max(1, leaseSeconds / 3),
                        Math.max(1, leaseSeconds / 3),
                        TimeUnit.SECONDS);
        try {
            JudgeSubmitDTO snapshot;
            try {
                snapshot = JSON.parseObject(taskMapper.snapshotJson(task.getSubmitId()), JudgeSubmitDTO.class);
            } catch (JSONException invalid) {
                snapshot = null;
            }
            // 固定快照损坏属于不可自动修复的错误，持久化最终失败后即可确认消息。
            if (!validSnapshot(snapshot)) {
                taskService.fail(task, "INVALID_SNAPSHOT", false);
                channel.basicAck(tag, false);
                return;
            }
            // 真正的编译和运行发生在 Judge 服务中，且不占用数据库事务。
            UserQuestionResultVO result = judgeService.doJudgeJavaCode(snapshot, execution);
            // 本地已撤销/超时 OR 数据库拒绝完成，任一成立都转入失败/恢复处理。
            // || 短路使本地已失效时根本不调用 finish；本地有效仍要由数据库令牌和租约作最终裁决。
            if (!execution.isActive() || !taskService.finish(task, result)) {
                taskService.fail(task, "EXECUTION_AUTHORITY_LOST", false);
            }
            // finish/fail 返回前已提交事务；ACK 丢失产生的重复消息不会再次完成或计分。
            channel.basicAck(tag, false);
        } catch (DataAccessException unavailable) {
            pauseConsumption();
        } catch (RuntimeException executionFailure) {
            log.warn("沙箱执行失败，提交编号={}，执行次数={}",
                    task.getSubmitId(),
                    task.getAttemptCount(),
                    executionFailure);
            try {
                taskService.fail(task, "SANDBOX_UNAVAILABLE", false);
                channel.basicAck(tag, false);
            } catch (RuntimeException unavailable) {
                pauseConsumption();
            }
        } finally {
            // 先撤销本地执行权，阻止迟到续租回调恢复执行；用过的容器退役，不直接放回空闲池。
            execution.cancel();
            watchdog.cancel(false);
            renewal.cancel(false);
            dockerPool.retire(execution.getContainerId());
            log.info("本轮判题处理结束，提交编号={}，事件编号={}，执行令牌={}，工作进程编号={}，执行次数={}，耗时（毫秒）={}",
                    task.getSubmitId(), message.getEventId(), task.getRunToken(), task.getWorkerId(), task.getAttemptCount(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - executionStarted));
            Thread.interrupted();
        }
    }

    /**
     * 执行前的快照完整性检查，所有 && 条件必须同时成立：
     * 1. 快照存在且语言为 Java（0），源码存在且不是空白。
     * 2. 时间限制、空间限制及难度均存在且大于 0，避免无效执行限制或计分参数。
     * 3. 输入列表存在且至少有一例，期望输出列表存在，两者长度相同，保证按下标配对。
     * 4. 每个期望输出不能为 null；空字符串仍是合法的期望输出，不应在这里拒绝。
     * 输入项允许 null/空白，沙箱将其解释为不传命令行参数，不能与缺少输入列表混淆。
     * && 的短路顺序保证先检查对象非空，再访问字段或列表；返回 false 时调用方记录
     * INVALID_SNAPSHOT 并终结任务，不进入沙箱，也不做无法修复此快照的自动重试。
     */
    private boolean validSnapshot(JudgeSubmitDTO snapshot) {
        // 先检查对象存在，再检查各字段，利用短路求值避免对空对象取值。
        // 输入与期望输出必须一一对应；否则无法可靠比较答案，应按快照错误处理而不是执行代码。
        return snapshot != null && Integer.valueOf(0).equals(snapshot.getProgramType())
                && snapshot.getUserCode() != null && !snapshot.getUserCode().isBlank()
                && snapshot.getTimeLimit() != null && snapshot.getTimeLimit() > 0
                && snapshot.getSpaceLimit() != null && snapshot.getSpaceLimit() > 0
                && snapshot.getDifficulty() != null && snapshot.getDifficulty() > 0
                && snapshot.getInputList() != null && !snapshot.getInputList().isEmpty()
                && snapshot.getOutputList() != null && snapshot.getInputList().size() == snapshot.getOutputList().size()
                && snapshot.getOutputList().stream().noneMatch(Objects::isNull);
    }

    /** 幂等撤销执行、唤醒阻塞线程并终止容器；终止失败仍交由回收流程继续处理。 */
    private void stopExecution(JudgeExecution execution, Thread executingThread) {
        // 多个线程可能同时发现超时或失权，只有首次撤销成功者负责中断和终止。
        // 先撤销本地资格再操作容器，容器调用即使很慢，业务线程也不能继续推进新阶段。
        if (!execution.cancel()) {
            return;
        }
        executingThread.interrupt();
        try {
            // 终止容器
            dockerPool.terminate(execution.getContainerId());
        } catch (RuntimeException failure) {
            log.warn("沙箱终止已延后，容器编号={}", execution.getContainerId());
        } finally {
            dockerPool.retire(execution.getContainerId());
        }
    }

    /** 原子标记避免重复停机；异步停止监听，避免消费线程等待自身退出而死锁。
     * // 1. 先把 paused 从 false 改为 true，避免重复提交暂停任务。
     * // 2. getListenerContainer("reliableJudge") 找到监听容器。
     * // 3. stop(...) 取消这个容器下消费者的订阅，停止给它们投递新消息。
     * // 4. 停止回调触发后，安排 10 秒后执行 resumeConsumption()。
     * */
    private void pauseConsumption() {
        // paused 初始为 false。多个消费线程同时进来时，
        // 只有第一个能把它从 false 改成 true，其他线程直接返回。
        if (!paused.compareAndSet(false, true)) {
            return;
        }
        // 日志写在提交停止任务之前，此时刚发起暂停。
        log.warn("判题消息消费已暂停，持久化结果尚未确认");
        // execute 把停止操作交给定时线程池，当前消费线程可以继续退出。
        judgeLeaseExecutor.execute(
                // 按 @RabbitListener 的 ID 找到判题消息的监听容器。
                /**
                 * 监听容器是 Spring 在你的 Java 应用里创建的一个对象，负责管理收消息的过程。 这里的 reliableJudge 是它在 Spring 注册表里的名字。
                 * 你写 consume()，Spring 负责启动消费者、向 RabbitMQ 登记「请把这个队列的消息发给我」，收到消息后再调用你的 consume()。
                 */
                () -> registry.getListenerContainer("reliableJudge")
                        // stop 先取消订阅，再等待旧消费者退出。
                        // 等待超时也会触发回调。本项目配置为超时后不强制关闭消费通道。
                        // 回调触发时才安排 10 秒后的恢复检查。
                        // this::resumeConsumption 等价于 () -> resumeConsumption()。
                        .stop(() -> judgeLeaseExecutor.schedule(this::resumeConsumption, 10, TimeUnit.SECONDS)));
    }

    /** 数据库和容器均可用、旧消费者全部退出后恢复；否则退避，避免故障期间反复重投。 */
    private void resumeConsumption() {
        try {
            // 执行 SELECT，最多查一个符合恢复条件的任务 ID。
            // 这里只确认查询能正常执行，结果直接丢弃。查到 0 条也继续。
            // 查询抛出运行时异常时，直接跳到下面的 catch。
            taskMapper.recoveryCandidates(1);
            // hasAvailableContainer() 只检查 !ready.isEmpty()。
            // 实际取用沙箱发生在消费消息时。
            if (!dockerPool.hasAvailableContainer()) {
                judgeLeaseExecutor.schedule(this::resumeConsumption, 10, TimeUnit.SECONDS);
                return; // 结束本轮检查，10 秒后从数据库查询重新开始。
            }
            MessageListenerContainer listener = registry.getListenerContainer("reliableJudge");
            // instanceof 先检查容器类型，左边为 true 才执行 && 右边的强转。
            // getActiveConsumerCount() 读取 Spring 仍在追踪的活动消费者数量。
            // 停止回调触发后，旧消费者仍可能在退出。数量大于 0 就继续等待。
            if (listener instanceof SimpleMessageListenerContainer && ((SimpleMessageListenerContainer) listener).getActiveConsumerCount() > 0) {
                judgeLeaseExecutor.schedule(this::resumeConsumption, 10, TimeUnit.SECONDS);
                return;
            }
            // 启动监听容器，让 Spring 重新启动消费者接收消息。
            listener.start();
            // start() 正常返回才清掉标志，下一次故障可以再次发起暂停。
            paused.set(false);
        } catch (RuntimeException unavailable) {
            // 查询数据库或启动监听器抛出运行时异常时，安排下一轮检查。
            judgeLeaseExecutor.schedule(this::resumeConsumption, 10, TimeUnit.SECONDS);
        }
    }
}
