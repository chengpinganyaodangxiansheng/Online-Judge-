package org.example.judge.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.*;

/** 可靠判题的队列、手动确认消费者和有限并发线程池配置。 */
@Configuration
// 显式启用 @RabbitListener 基础设施，由 Spring 注册并管理 RabbitListenerEndpointRegistry。
// 消费者注入该注册器以暂停/恢复实际监听容器，不另建一个与监听器脱离的注册器实例。
@EnableRabbit
@EnableScheduling
public class JudgeTaskConfig {
    /** 可靠判题请求的持久化主队列名称，生产者和消费者据此使用同一入口。 */
    public static final String QUEUE = "oj.judge.requested.v1";

    /** 为固定数量的周期任务提供独立调度余量，Docker 慢回收不能阻塞补池和租约恢复扫描。 */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler(); // 创建 Spring 调度器。
        scheduler.setPoolSize(5); // 配置5个调度线程。
        scheduler.setThreadNamePrefix("judge-maintenance-"); // 设置线程名前缀，便于识别日志和线程。
        scheduler.setRemoveOnCancelPolicy(true); // 取消的定时任务从队列移除。
        scheduler.setWaitForTasksToCompleteOnShutdown(false); // 关闭时不等待全部任务自然执行完。
        return scheduler;
    }

    /** 主队列持久化，拒绝且不重入队的非法消息进入独立死信队列。 */
    @Bean
    public Queue reliableJudgeQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(QUEUE + ".dead")
                .build();
    }

    /** 声明持久化死信队列，保留被拒绝且不重新入队的非法消息供排查。 */
    @Bean
    public Queue judgeDeadQueue() {
        return new Queue(QUEUE + ".dead", true);
    }

    /** 创建固定并发的手动确认监听器，每个消费者只预取一条消息；暂停时不强制关闭其他执行通道。 */
    @Bean
    public SimpleRabbitListenerContainerFactory judgeListenerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            @Value("${judge.task.concurrency:1}") int concurrency) {
        if (concurrency < 1 || concurrency > 32) {
            throw new IllegalArgumentException("判题并发数必须在 1 到 32 之间");
        }
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);// MANUAL 由业务代码明确确认消息，方法返回不等于业务主动调用了 ACK。
        // 固定消费并发，每个消费者最多持有一条未确认消息。
        factory.setConcurrentConsumers(concurrency); // 配置消费者数量。
        factory.setMaxConcurrentConsumers(concurrency);// 最大数量设置成相同值，固定并发，不自动扩展到更多消费者。
        factory.setPrefetchCount(1); // 每个消费者的预取额度设为 1，避免提前拿一大批尚未确认的消息。
        factory.setDefaultRequeueRejected(false);
        // 暂停入口时让其他正在处理的消费者完成，不强制关闭其执行通道。
        factory.setContainerCustomizer(container -> container.setForceCloseChannel(false));
        return factory;
    }

    /** 续租与固定期限看门狗独立于判题线程，业务阻塞时仍可撤销执行权。 */
    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService judgeLeaseExecutor() {
        return Executors.newScheduledThreadPool(2);
    }

    /** 发布确认可能等待网络；独立有界线程池避免阻塞补偿扫描或无限堆积发送任务。
     * destroyMethod: Spring 销毁这个对象时调用 shutdownNow，请求中断正在执行的工作，并停止处理排队工作；不等于强制保证所有工作立即结束。
     * 两个参数分别读取发送并发数和扫描批大小。
     * */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService outboxPublishExecutor(@Value("${judge.outbox.concurrency:1}") int concurrency, @Value("${judge.task.scan-batch:20}") int batch) {
        if (concurrency < 1 || concurrency > 8) {
            throw new IllegalArgumentException("本地消息表投递并发数必须在 1 到 8 之间");
        }
        if (batch < 1 || batch > 1000) {
            throw new IllegalArgumentException("判题扫描批大小必须在 1 到 1000 之间");
        }
        // 队列可接纳一批候选，容量不随发送线程数缩小，降低同一批尾部事件反复被拒绝的风险。
        return new ThreadPoolExecutor(
                concurrency,
                concurrency,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(batch),
                new ThreadPoolExecutor.AbortPolicy());// 满载时采用抛异常的拒绝策略。
    }
}
