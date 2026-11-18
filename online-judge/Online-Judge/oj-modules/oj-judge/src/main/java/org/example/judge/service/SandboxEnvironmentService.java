package org.example.judge.service;

import org.example.judge.domain.SandboxResource;

/** 外部执行环境的生命周期边界，与数据库容量预留分离。 */
public interface SandboxEnvironmentService {
    /** 输入已持久化预留的环境记录，建立专属控制组和容器并验证可信执行器；仅返回确认就绪的资源，失败或结果未知时保留记录用于回收。 */
    SandboxResource create(SandboxResource resource);

    /** 按原资源身份核对容器、目录、控制组和连接并分别保存回收证据；方法返回不必然表示全部完成，调用方应查询记录是否已关闭。 */
    void recycle(SandboxResource resource);
}
