package org.example.judge.service;

import org.example.api.domain.dto.JudgeSubmitDTO;
import org.example.judge.domain.SandboxRuntimeMessage;

/** 每容器一条 attach 连接，提交级发送，逐阶段接收；不逐用例调用 Docker exec。 */
public interface SandboxRuntimeService {
    /** 按确切容器编号建立专属输入输出连接，必须在启动前完成；重复连接或半初始化失败不能发布为可用连接。 */
    void connect(String containerId);

    /** 有期限地等待执行器就绪消息；容器进程存在不能替代监督、权限和控制组自检完成。 */
    void awaitReady(String containerId);

    /** 发送容器编号对应的提交级请求，携带执行身份、源码、有序输入和毫秒制总预算；期望答案不发送到用户容器，所有数据均受长度限制。 */
    void submit(String containerId, String attempt, JudgeSubmitDTO snapshot, long budgetMillis);

    /** 在指定毫秒数内等待并解析下一条完整协议消息；暂无消息返回空值，连接故障或证据格式错误抛出异常，不能猜测执行结果。 */
    SandboxRuntimeMessage receive(String containerId, long milliseconds);

    /** 关闭指定容器的专属通信资源，重复关闭允许安全结束；关闭失败必须保留追踪，使回收器继续核对而非提前记录成功。 */
    void close(String containerId);
}
