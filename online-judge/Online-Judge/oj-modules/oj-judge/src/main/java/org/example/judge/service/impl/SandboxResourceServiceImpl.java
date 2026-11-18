package org.example.judge.service.impl;

import org.example.judge.domain.SandboxResource;
import org.example.judge.mapper.SandboxResourceMapper;
import org.example.judge.service.SandboxResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/** 以 worker 行锁串行化短暂容量预留，外部创建由事务提交后的调用方负责。 */
@Service
public class SandboxResourceServiceImpl implements SandboxResourceService {
    /** 执行环境记录访问对象，用于状态迁移、归属核对和分项回收证据保存。 */
    @Autowired private SandboxResourceMapper mapper;

    /** 预留和持久身份同事务提交；正常容量含预留、就绪及独占资源，回收资源只占硬额度。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SandboxResource reserve(String worker, String generation, int normal, int hard) {
        // 节点名必须是一至六十四位字母、数字或短横线，启动代次必须存在且不超过三十六个字符。
        // 正常容量至少为一，总额度不能小于正常容量，也不能超过一百二十八；任一不满足都拒绝预留。
        if (worker == null || !worker.matches("[a-zA-Z0-9-]{1,64}") || generation == null
                || generation.length() > 36 || normal < 1 || hard < normal || hard > 128) {
            throw new IllegalArgumentException("沙箱容量或标识无效");
        }
        // 建立节点计账行，已存在时通过冲突更新取得锁，避免并发初始化绕过容量约束。
        mapper.initialize(worker, normal, hard);
        if (mapper.lock(worker) == null) {
            // 找不到节点计账行就无法可靠地锁住容量检查，中止事务，避免并发创建突破上限。
            throw new IllegalStateException("工作进程资源计账不可用");
        }
        // 在同一事务锁内同步当前容量配置，既有待回收记录仍继续计入总占用。
        mapper.configure(worker, normal, hard);
        List<SandboxResource> resources = mapper.active(worker);
        // 回收中的环境不计入正常容量，但仍在全部未关闭记录中占据总额度。
        long normalCount = resources.stream().filter(resource -> !"RECYCLING".equals(resource.getState())).count();
        if (resources.size() >= hard || normalCount >= normal) {
            // 全部未关闭资源已达总上限，或者非回收资源已达正常容量时，均无需创建新环境。
            // 例如正常容量为二、总额度为四，已有两个就绪环境时，即使总额度还有空位也不补池。
            return null;
        }
        // 创建待持久化的预留对象，此时仅申请额度，实际容器由事务提交后的调用方创建。
        SandboxResource resource = new SandboxResource();
        // 独立资源编号用于关联容器、工作目录和后续回收记录。
        resource.setResourceId(UUID.randomUUID().toString());
        // 指定所属节点，重启后才能按节点恢复自己的资源。
        resource.setWorkerId(worker);
        // 保存本次启动代次，防止旧进程的迟到操作影响当前资源。
        resource.setGeneration(generation);
        // 按固定规则生成名称，便于用容器标签和名称交叉核对归属。
        resource.setContainerName("oj-" + worker + "-" + resource.getResourceId());
        // 已预留记录立即占额度，但在创建和自检结束前不能领取。
        resource.setState("RESERVED");
        // 标记创建操作尚待确认，异常中断后仍能找到需要核对的环境。
        resource.setCreatePending(true);
        // 唯一身份和创建操作记录共同提交；其中任一步失败都会回滚，不允许先创建外部资源。
        mapper.insert(resource);
        // 在同一事务中登记创建操作，两条记录必须一起成功或一起回滚。
        mapper.initializeCreation(resource.getResourceId());
        // 将预留身份交给补池调用方，供下一步创建使用。
        return resource;
    }
}
