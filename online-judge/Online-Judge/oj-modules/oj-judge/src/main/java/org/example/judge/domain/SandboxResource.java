package org.example.judge.domain;

import lombok.Data;

/**
 * 一份执行环境的持久身份与分项回收证据，未关闭记录始终占用一个额度。
 * 容器删除完成不能替代目录、控制组和连接的回收；创建结果未知也必须继续计账。
 * 本对象只承载记录，状态迁移由持久化访问对象的条件更新保证，不靠内存字段赋值取得执行资格。
 */
@Data
public class SandboxResource {
    /** 执行环境的唯一编号，关联持久化记录、容器标签与专属资源，重启核对时保持不变。 */
    private String resourceId;

    /** 环境所属工作节点的稳定标识，用于限定启动恢复和回收的归属范围。 */
    private String workerId;

    /** 创建环境时的进程启动代次，阻止旧进程资源重新进入当前空闲池。 */
    private String generation;

    /** 创建前确定的容器名称；创建响应丢失时据此查找原容器，避免重复创建。 */
    private String containerName;

    /** 已确认的容器唯一编号，删除及删除结果核对均针对这一确切容器。 */
    private String containerId;

    /** 环境生命周期状态，区分预留、就绪、占用、回收及关闭；未关闭环境继续占用总额度。 */
    private String state;

    /** 是否已确认容器回收完成，不能仅凭删除请求超时设置为已完成。 */
    private boolean containerDone;

    /** 是否已确认专属宿主目录清理完成，是归还环境额度的必要证据之一。 */
    private boolean directoryDone;

    /** 是否已确认专属控制组子树回收完成，残留时继续保留环境额度。 */
    private boolean cgroupDone;

    /** 是否已确认专属执行器连接关闭，防止释放额度后仍遗留通信资源。 */
    private boolean connectionDone;

    /** 是否仍存在结果未确认的创建操作；未排除迟到创建之前不能关闭资源记录。 */
    private boolean createPending;
}
