package org.example.judge.service.impl;

import com.alibaba.fastjson2.JSON;
import org.example.api.domain.dto.JudgeRequestedDTO;
import org.example.common.mybatis.domain.JudgeOutbox;
import org.example.common.mybatis.mapper.JudgeOutboxMapper;
import org.example.common.mybatis.mapper.JudgeTaskMapper;
import org.example.judge.config.JudgeTaskConfig;
import org.example.judge.service.OutboxDeliveryService;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/** 将持久化事件投递至 RabbitMQ；允许重复发送，由任务领取条件保证执行幂等。 */
@Slf4j
@Service
public class OutboxDeliveryServiceImpl implements OutboxDeliveryService {
    /** 本地消息表访问对象，用于核对事件领取、重发及发布状态。 */
    @Autowired
    private JudgeOutboxMapper mapper;

    /** 任务数据库访问对象，用于查询和条件推进可靠判题任务。 */
    @Autowired
    private JudgeTaskMapper taskMapper;

    /** 消息发布客户端，发送持久化事件并关联发布确认及不可路由退回。 */
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /** 同一事件允许领取发送的最大次数，耗尽后保留失败记录。 */
    @Value("${judge.outbox.max-sends:5}")
    private int maxSends;

    /** 一次事件发送领取的有效秒数，领取过期后可由扫描器回收。 */
    @Value("${judge.outbox.claim-seconds:30}")
    private int claimSeconds;

    /** 等待消息发布确认的最长秒数，超时按投递结果未确认处理。 */
    @Value("${judge.outbox.confirm-seconds:5}")
    private int confirmSeconds;

    /** 处理一次扫描候选；候选可能已过时，所有状态修改都必须再次通过数据库条件校验。 */
    @Override
    public void deliver(JudgeOutbox candidate) {
        // 发送预算耗尽只终结仍等待该代次消息的任务，不覆盖已开始执行或已经完成的任务。
        if ("DEAD".equals(candidate.getState())) {
            // 已耗尽发送机会的事件不再发送；数据库条件更新只终结仍等待此事件的任务。
            taskMapper.deliveryFailed(candidate.getEventId());
            return;
        }
        // 上一个发布者失联后先回收过期领取，下次扫描再按退避时间重新发送。
        if ("INFLIGHT".equals(candidate.getState())) {
            // 候选处于发送中时，尝试回收过期领取；是否真正过期由数据库条件再次判断。
            mapper.recover(candidate.getEventId(), maxSends);
            return;
        }
        String token = UUID.randomUUID().toString();
        // 本次领取令牌与事件编号不同：事件重发时编号不变，每次领取生成新令牌以拒绝旧发送者回写。
        // 候选必须仍为 PENDING、退避结束且发送次数未耗尽；返回 0 可能是别的发布者先领取，直接跳过。
        // 如果成功修改，outbox的状态变成INFLIGHT
        if (mapper.claim(candidate.getEventId(), token, claimSeconds, maxSends) != 1) {
            return;
        }
        // 记录单调时间起点，只用于计算发布耗时，避免系统日期调整影响日志中的耗时。
        long started = System.nanoTime();

        CorrelationData correlation = new CorrelationData(token);
        try {
            // 消息无法到达队列时要求代理退回消息，让调用方能区分“接收了消息”和“成功路由到队列”。
            rabbitTemplate.setMandatory(true); // 消息无法路由时要求退回。
            // 使用默认交换机和判题队列名称作为路由键；负载由数据库中保存的事件内容反序列化得到。
            // 例如同一提交因确认丢失而重发，仍发送同一事件，由消费端的条件领取防止重复执行。
            rabbitTemplate.convertAndSend(
                    "", // 使用 RabbitMQ 默认交换机。
                    JudgeTaskConfig.QUEUE, // 路由键为业务队列名。
                    JSON.parseObject(candidate.getPayload(), JudgeRequestedDTO.class), // 把数据库载荷解析回 JudgeRequestedDTO，作为发送内容。
                     message -> {
                        // 发布前把消息标为持久化，配合持久化队列提高代理重启后的恢复能力。
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT); // 请求按持久化消息处理。
                        message.getMessageProperties().setMessageId(candidate.getEventId()); // 使用事件编号；重发同一个事件仍使用同一编号。
                        // 消息编号沿用事件编号，便于把队列消息与本地消息表记录关联排查。
                        return message;
                    }, // 在消息转换后补充属性。
                    correlation); // 最后传入本轮发布关联对象。
            // 最多等待配置秒数取得发布确认；超时只表示确认未知，不能据此断定消息没有发送。
            CorrelationData.Confirm confirm = correlation.getFuture().get(confirmSeconds, TimeUnit.SECONDS); // 取得异步确认结果。
            // Spring AMQP 在完成 confirm future 前已关联 returned message。
            // Broker 确认且消息没有因无法路由被退回，才记录 SENT；这不代表判题完成。
            // 两项必须同时成立：ACK 表示 Broker 接收，returned 为 null 表示没有路由退回。
            // NACK 或 mandatory 退回均进入失败退避；不能只看 ACK 就认为队列已收到。
            if (confirm.isAck() && correlation.getReturned() == null) {
                int updated = mapper.sent(candidate.getEventId(), token);
                // updated=0 说明旧令牌已失效；只记录观察结果，不能覆盖新发布者的状态。
                log.info("判题消息发布已确认，提交编号={}，事件编号={}，派发代次={}，领取令牌={}，状态更新行数={}，耗时（毫秒）={}",
                        candidate.getSubmitId(),
                        candidate.getEventId(),
                        candidate.getDispatchSeq(),
                        token,
                        updated,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            } else {
                // 代理拒绝或消息被退回时，按当前领取令牌登记失败，由数据库决定退避重试或耗尽机会。
                mapper.failed(candidate.getEventId(), token, maxSends, "PUBLISH_REJECTED_OR_RETURNED");
            }
        } catch (Exception exception) {
            // 超时可能发生在 Broker 已收消息之后，不能断言未发送；按原事件重试而非创建新任务。
            if (exception instanceof InterruptedException) {
                // 等待确认被中断时恢复中断标志，让外层取消和关闭流程仍能感知取消请求。
                Thread.currentThread().interrupt();
            }
            mapper.failed(candidate.getEventId(), token, maxSends, "PUBLISH_UNCONFIRMED");
            // 未确认事件继续保留原身份；下一次扫描可以重试，避免生成新的业务提交。
            log.warn("判题消息发布尚未确认，提交编号={}，事件编号={}，派发代次={}，领取令牌={}，耗时（毫秒）={}",
                    candidate.getSubmitId(),
                    candidate.getEventId(),
                    candidate.getDispatchSeq(),
                    token,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }
}
