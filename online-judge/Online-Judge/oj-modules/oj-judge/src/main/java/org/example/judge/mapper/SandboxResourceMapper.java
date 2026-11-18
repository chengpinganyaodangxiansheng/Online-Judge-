package org.example.judge.mapper;

import org.apache.ibatis.annotations.*;
import org.example.judge.domain.SandboxResource;
import java.util.List;

/**
 * 执行环境的持久化状态入口，记录创建身份、独占占用和各资源项回收证据。
 * 条件更新返回受影响行数：调用方必须确认竞争成功，不能忽略零行结果继续创建或发布资源。
 * 数据库异常向上传播，不解释为不存在或零占用；只有全部回收证据齐全且无未决创建才关闭记录。
 */
@Mapper
public interface SandboxResourceMapper {
    /** 与资源预留同事务创建操作记录，旧资源缺记录时仍按未知处理。 */
    @Insert("INSERT INTO tb_sandbox_creation(resource_id) VALUES(#{id})")
    int initializeCreation(String id);

    /** 外部子树操作前记录请求；条件插入与退役行锁互斥，不能在撤销后迟到创建。 */
    @Insert("INSERT INTO tb_sandbox_cgroup_request(resource_id) SELECT resource_id FROM tb_sandbox_resource WHERE resource_id=#{id} AND state='RESERVED'")
    int requestCgroup(String id);

    /** 受控创建调用成功返回后记录子树确认，不能以空查询替代该证据。 */
    @Update("UPDATE tb_sandbox_creation SET cgroup_confirmed=TRUE WHERE resource_id=#{id}")
    int confirmCgroup(String id);

    /** 发起 Docker 请求前持久登记；回收抢先撤销后旧创建者不得再发请求。 */
    @Update("UPDATE tb_sandbox_creation c JOIN tb_sandbox_resource r ON r.resource_id=c.resource_id SET c.docker_requested=TRUE WHERE c.resource_id=#{id} AND c.cgroup_confirmed=TRUE AND c.docker_requested=FALSE AND r.state='RESERVED'")
    int requestDocker(String id);

    /** 已退役且明确尚未发起 Docker 创建时，子树可独立回收；未知记录返回零。 */
    @Select("SELECT COUNT(*) FROM tb_sandbox_creation c JOIN tb_sandbox_resource r ON r.resource_id=c.resource_id WHERE c.resource_id=#{id} AND (c.cgroup_confirmed=TRUE OR NOT EXISTS(SELECT 1 FROM tb_sandbox_cgroup_request q WHERE q.resource_id=c.resource_id)) AND c.docker_requested=FALSE AND r.state='RECYCLING'")
    int reclaimBeforeDocker(String id);

    /** 启动发现归属明确的孤儿容器时恢复计账，仅进入回收，绝不重新执行。 */
    @Insert("INSERT INTO tb_sandbox_resource(resource_id,worker_id,generation,container_name,container_id,state,create_pending) VALUES(#{resourceId},#{workerId},#{generation},#{containerName},#{containerId},'RECYCLING',FALSE) ON DUPLICATE KEY UPDATE state='RECYCLING',container_id=VALUES(container_id),container_done=FALSE,directory_done=FALSE,cgroup_done=FALSE,connection_done=FALSE,create_pending=FALSE")
    int adoptOrphan(SandboxResource resource);

    /** 固定创建使用的镜像内容身份，记录不得被后续标签变化覆盖。 */
    @Insert("INSERT INTO tb_sandbox_runtime_metadata(resource_id,image_digest,jdk_version) VALUES(#{id},#{digest},#{jdk})")
    int runtimeMetadata(@Param("id") String id, @Param("digest") String digest, @Param("jdk") String jdk);

    /** 迁移旧版固定名称容器，只补记已有资源，不创建新资源，也不抹去已有记录。 */
    @Insert("INSERT INTO tb_sandbox_resource(resource_id,worker_id,generation,container_name,container_id,state,create_pending) VALUES(#{resourceId},#{workerId},#{generation},#{containerName},#{containerId},'RECYCLING',FALSE) ON DUPLICATE KEY UPDATE container_name=VALUES(container_name)")
    int adoptLegacy(SandboxResource resource);

    /** 创建完成或按不可复用的唯一名称核对到容器后，保存其确切身份。 */
    @Update("UPDATE tb_sandbox_resource SET container_id=#{container},create_pending=FALSE WHERE resource_id=#{id} AND state<>'CLOSED'")
    int created(@Param("id") String id, @Param("container") String container);

    /** 按唯一资源编号读取权威状态，供恢复与测试核对。 */
    @Select("SELECT * FROM tb_sandbox_resource WHERE resource_id=#{id}")
    SandboxResource find(String id);

    /** 初始化或锁定 worker；冲突分支直接取得排他锁，避免 INSERT IGNORE 的共享锁升级死锁。 */
    @Insert("INSERT INTO tb_sandbox_worker VALUES(#{worker},#{normal},#{hard}) ON DUPLICATE KEY UPDATE worker_id=VALUES(worker_id)")
    int initialize(@Param("worker") String worker, @Param("normal") int normal, @Param("hard") int hard);

    /** 锁住本 worker 的计账入口，让容量判断与插入不可并发穿透。 */
    @Select("SELECT hard_capacity FROM tb_sandbox_worker WHERE worker_id=#{worker} FOR UPDATE")
    Integer lock(String worker);

    /** 读取全部未结清环境，包括创建未知和仅残留附属资源的环境。 */
    @Select("SELECT * FROM tb_sandbox_resource WHERE worker_id=#{worker} AND state<>'CLOSED'")
    List<SandboxResource> active(String worker);

    /** 持锁更新容量；降低容量不会撤销已有资源记录。 */
    @Update("UPDATE tb_sandbox_worker SET normal_capacity=#{normal},hard_capacity=#{hard} WHERE worker_id=#{worker}")
    int configure(@Param("worker") String worker, @Param("normal") int normal, @Param("hard") int hard);

    /** 在任何外部资源创建前持久记录身份和未决创建标记。 */
    @Insert("INSERT INTO tb_sandbox_resource(resource_id,worker_id,generation,container_name,state) VALUES(#{resourceId},#{workerId},#{generation},#{containerName},'RESERVED')")
    int insert(SandboxResource resource);

    /** 以资源编号、启动代次及预留状态竞争进入就绪；返回一表示本轮验证结果被接受，返回零表示资格已变化，不得发布到空闲队列。 */
    @Update("UPDATE tb_sandbox_resource SET container_id=#{container},create_pending=FALSE,state='READY' WHERE resource_id=#{id} AND generation=#{generation} AND state='RESERVED'")
    int ready(@Param("id") String id, @Param("generation") String generation, @Param("container") String container);

    /** 仅将本代次就绪资源改为独占；返回一表示取得资源占用权，仍须另行取得业务任务执行权，返回零不得使用该容器。 */
    @Update("UPDATE tb_sandbox_resource SET state='LEASED' WHERE resource_id=#{id} AND generation=#{generation} AND state='READY'")
    int acquire(@Param("id") String id, @Param("generation") String generation);

    /** 仅尚未运行的领取可以归还；业务入口须保证用户代码尚未启动。 */
    @Update("UPDATE tb_sandbox_resource SET state='READY' WHERE resource_id=#{id} AND generation=#{generation} AND state='LEASED'")
    int releaseUnused(@Param("id") String id, @Param("generation") String generation);

    /** 退役不释放总额度；创建是否已确定单独保留。 */
    @Update("UPDATE tb_sandbox_resource SET state='RECYCLING' WHERE resource_id=#{id} AND state<>'CLOSED'")
    int retire(String id);

    /** 对待回收记录单向合并容器、目录、控制组和连接的完成证据；已完成项不会回退，未决创建标记只有得到确认才可消除。 */
    @Update("UPDATE tb_sandbox_resource SET container_done=container_done OR #{containerDone},directory_done=directory_done OR #{directoryDone},cgroup_done=cgroup_done OR #{cgroupDone},connection_done=connection_done OR #{connectionDone},create_pending=create_pending AND #{createPending} WHERE resource_id=#{resourceId} AND state='RECYCLING'")
    int evidence(SandboxResource evidence);

    /** 全部项目确认完成且无迟到创建风险才结清，重复通知不能再次释放。 */
    @Update("UPDATE tb_sandbox_resource SET state='CLOSED' WHERE resource_id=#{id} AND state='RECYCLING' AND container_done=TRUE AND directory_done=TRUE AND cgroup_done=TRUE AND connection_done=TRUE AND create_pending=FALSE")
    int close(String id);
}
