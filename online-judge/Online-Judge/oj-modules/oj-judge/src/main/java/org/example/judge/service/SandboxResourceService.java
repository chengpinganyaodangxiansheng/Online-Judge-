package org.example.judge.service;

import org.example.judge.domain.SandboxResource;

/** 执行环境容量预留边界；不在数据库事务中访问 Docker 或文件系统。 */
public interface SandboxResourceService {
    /** 按工作节点、启动代次、正常容量和总额度预留一份环境；在节点计账锁内检查缺口并保存唯一身份，无缺口或无额度时返回空值，未确认成功前不能创建外部资源。 */
    SandboxResource reserve(String worker, String generation, int normal, int hard);
}
