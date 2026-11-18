package org.example.judge.service.impl;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.example.judge.domain.SandboxResource;
import org.example.judge.mapper.SandboxResourceMapper;
import org.example.judge.service.SandboxCgroupService;
import org.example.judge.service.SandboxEnvironmentService;
import org.example.judge.service.SandboxRuntimeService;
import org.example.judge.util.SandboxDirectoryCleaner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Docker 生命周期操作不持有数据库事务；超时不能被当作创建失败或回收完成。 */
@Service
@Slf4j
public class SandboxEnvironmentServiceImpl implements SandboxEnvironmentService {
    /** 容器引擎客户端，用于测试环境的创建、查询、故障注入和回收。 */
    @Autowired
    private DockerClient dockerClient;

    /** 执行环境记录访问对象，用于状态迁移、归属核对和分项回收证据保存。 */
    @Autowired
    private SandboxResourceMapper mapper;

    /** 受控控制组管理服务，建立和回收本环境专属的资源限制子树。 */
    @Autowired
    private SandboxCgroupService cgroups;

    /** 可信执行器通信服务，维护连接并发送提交请求、接收有界阶段证据。 */
    @Autowired
    private SandboxRuntimeService runtime;

    /** 可信执行器镜像配置，创建时解析并使用具体镜像标识。 */
    @Value("${sandbox.runtime.image:oj-sandbox-runtime:7}")
    private String image;

    /** 旧版挂载目录配置的保留字段，当前执行环境创建流程未使用。 */
    @Value("${sandbox.docker.volume:/usr/share/java}")
    private String volume;

    /** 整个容器的内存预算，单位为字节，需要覆盖用户任务和可信管理程序。 */
    @Value("${sandbox.runtime.memory-bytes:805306368}")
    private long memory;

    /** 外层容器允许的处理器核数，用于设置容器级计算资源配额。 */
    @Value("${sandbox.limit.cpu:1}")
    private long cpu;

    /** 启动即拒绝无法覆盖阶段预算与管理余量的配置，不把平台预算错误算作用户编译错误。 */
    @jakarta.annotation.PostConstruct
    public void validateConfiguration() {
        if (memory < 536870912L || memory > 4294967296L || cpu < 1 || cpu > 8) {
            throw new IllegalArgumentException("执行器外层资源预算无效");
        }
    }

    /** 身份必须已经持久化；目录及 Docker 创建失败均转入保守回收状态。 */
    @Override
    public SandboxResource create(SandboxResource resource) {
        // 创建分成几步：确认预留记录、准备控制组、创建容器、接通控制流、启动并验证就绪。
        // 任何一步失败都保留原资源身份进入回收，不能简单认为“创建报错就没有资源产生”。
        long started = System.nanoTime();
        SandboxResource persisted = mapper.find(resource.getResourceId());
        if (persisted == null || !"RESERVED".equals(persisted.getState())) {
            throw new IllegalStateException("缺少沙箱资源预留记录");
        }
        try {
            InspectImageResponse runtimeImage = dockerClient.inspectImageCmd(image).exec();
            String jdk = Arrays.stream(runtimeImage.getConfig().getEnv())
                            .filter(value -> value.startsWith("JAVA_VERSION="))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("缺少执行器的 Java 开发工具包元数据"));
            mapper.runtimeMetadata(resource.getResourceId(), runtimeImage.getId(), jdk.substring("JAVA_VERSION=".length()));
            // 为已经持久预留的身份创建专属控制组，返回仅供本环境挂载的受控路径。
            String group = cgroups.create(resource.getResourceId(), memory);
            // 返回的是引擎环境内的资源目录，例如 /sys/fs/cgroup/oj-runtime-资源编号。
            // 挂载进去的是 oj-runtime-资源编号 这个对应的就是/cg,也相当于是容器内部的父目录
            // 后面的父组配置让引擎在此目录下建立容器子组，挂载配置则让容器从 /cg 看到这棵子树。
            HostConfig host = HostConfig.newHostConfig()
                            .withMemory(memory)
                            .withMemorySwap(memory)
                            .withNanoCPUs(cpu * 1_000_000_000L)
                            .withPidsLimit(128L)
                            .withNetworkMode("none")
                            // 上一行指定引擎侧父资源组；容器内启动脚本选取 /cg 下的容器子组作为管理程序父路径。
                            .withCgroupParent("oj-runtime-" + resource.getResourceId())
                            .withCapDrop(Capability.ALL)
                            .withCapAdd(
                                    Capability.SETUID,
                                    Capability.SETGID,
                                    Capability.CHOWN,
                                    Capability.FOWNER,
                                    Capability.DAC_OVERRIDE)
                            .withSecurityOpts(List.of("no-new-privileges:true"))
                            .withLogConfig(new LogConfig(
                                    LogConfig.LoggingType.JSON_FILE,
                                    Map.of("max-size", "1m", "max-file", "1")))
                            .withReadonlyRootfs(true)
                            .withTmpFs(Map.of(
                                            "/control",
                                            "rw,nosuid,noexec,size=4m,nr_inodes=128,mode=700",
                                            "/work",
                                            "rw,nosuid,noexec,size=64m,nr_inodes=4096,mode=755",
                                            "/artifact",
                                            "rw,nosuid,noexec,size=16m,nr_inodes=2048,mode=755",
                                            "/tmp",
                                            "rw,nosuid,noexec,size=32m,nr_inodes=4096,mode=1777",
                                            "/dev/shm",
                                            "rw,nosuid,noexec,size=16m,nr_inodes=4096,mode=1777",
                                            "/dev/mqueue",
                                            "rw,nosuid,noexec,size=1m,nr_inodes=16,mode=000"))
                            .withBinds(new Bind(group, new Volume("/cg")));
            // 挂载把引擎路径映射为容器内路径，双方路径字符串不同，但访问的是同一资源控制子树。
            if (mapper.requestDocker(resource.getResourceId()) != 1) {
                throw new IllegalStateException("容器创建权限已撤销");
            }
            String id = dockerClient.createContainerCmd(runtimeImage.getId())
                            .withName(resource.getContainerName())
                            .withLabels(Map.of("oj.worker", resource.getWorkerId(),
                                            "oj.resource", resource.getResourceId()))
                            .withHostConfig(host)
                            .withTty(false)
                            .withStdinOpen(true)
                            .withStdInOnce(true)
                            .withAttachStdin(true)
                            .withAttachStdout(true)
                            .withAttachStderr(true)
                            .exec()
                            .getId();
            // 保存引擎返回的确切容器编号，后续删除按此身份执行，不能重新猜测容器归属。
            mapper.created(resource.getResourceId(), id);
            // 先建立控制连接再启动，避免错过执行器就绪消息；连接本身也属于需要回收的资源。
            runtime.connect(id);
            dockerClient.startContainerCmd(id).exec();
            // 等待可信执行器完成监督、权限和资源限制自检，不以容器运行状态代替就绪证据。
            runtime.awaitReady(id);
            if (!Boolean.TRUE.equals(dockerClient.inspectContainerCmd(id).exec().getState().getRunning())) {
                throw new IllegalStateException("沙箱启动校验失败");
            }
            if (mapper.ready(resource.getResourceId(), resource.getGeneration(), id) != 1) {
                throw new IllegalStateException("沙箱资源预留已撤销");
            }
            log.info("沙箱已就绪，资源编号={}，创建耗时（毫秒）={}", resource.getResourceId(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            return mapper.find(resource.getResourceId());
        } catch (RuntimeException failure) {
            mapper.retire(resource.getResourceId());
            throw new IllegalStateException("沙箱创建结果需要核对", failure);
        }
    }

    /** 仅已确认 Docker 身份消失后清理目录；没有身份且创建未决时绝不释放额度。 */
    @Override
    public void recycle(SandboxResource resource) {
        long started = System.nanoTime();
        // 先单向撤销使用资格，阻止创建或归还的迟到回调重新将环境发布为就绪。
        mapper.retire(resource.getResourceId());
        // reclaimBeforeDocker 查询数据库中的创建进度,能不能确认这条资源还没有走到 Docker 创建请求，
        if (resource.getContainerId() == null && mapper.reclaimBeforeDocker(resource.getResourceId()) != 1) {
            try {
                // 在容器内执行检查命令，使用容器名检查
                InspectContainerResponse found = dockerClient.inspectContainerCmd(resource.getContainerName()).exec();
                if (!resource.getResourceId().equals(found.getConfig().getLabels().get("oj.resource"))
                        || !resource.getWorkerId().equals(found.getConfig().getLabels().get("oj.worker"))) {
                    throw new IllegalStateException("沙箱归属不匹配");
                }
                resource.setContainerId(found.getId());
                mapper.created(resource.getResourceId(), found.getId());
            } catch (NotFoundException unknownCreation) {
                // 空查询不能证明超时创建不会迟到，保留额度等待后续核对。
                log.warn("沙箱创建结果未决且暂未查到容器，保留额度等待再次核对，资源编号={}，容器名称={}",
                        resource.getResourceId(), resource.getContainerName(), unknownCreation);
                return;
            }
        }
        try {
            if (resource.getContainerId() != null) {
                dockerClient.removeContainerCmd(resource.getContainerId()).withForce(true).exec();
            }
        } catch (NotFoundException alreadyRemoved) {
            // 已知 ID 不存在是容器删除的有效证据；仍须继续清理目录、连接及控制组才能释放额度。
            log.warn(
                    "回收沙箱时已知容器不存在，继续核对附属资源，资源编号={}，容器编号={}",
                    resource.getResourceId(),
                    resource.getContainerId(),
                    alreadyRemoved);
        }
        resource.setContainerDone(true);
        resource.setCreatePending(false);
        mapper.evidence(resource);
        try {
            Path directory = directory(resource);
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                // walk 默认不跟随软链接；根目录身份固定且只能属于本执行环境。
                Files.walkFileTree(directory, new SandboxDirectoryCleaner());
            }
            resource.setDirectoryDone(true);
            mapper.evidence(resource);
            if (resource.getContainerId() != null) {
                runtime.close(resource.getContainerId());
            }
            resource.setConnectionDone(true);
            mapper.evidence(resource);
            if (!"legacy".equals(resource.getGeneration())) {
                cgroups.remove(resource.getResourceId());
            }
            resource.setCgroupDone(true);
            mapper.evidence(resource);
            // 关闭条件由数据库再次核对：所有附属资源完成且无未决创建，才归还一次总额度。
            mapper.close(resource.getResourceId());
            log.info("沙箱清理完成，资源编号={}，清理耗时（毫秒）={}",
                    resource.getResourceId(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } catch (IOException failure) {
            throw new IllegalStateException("沙箱目录清理尚未完成", failure);
        }
    }

    /** 只从内部 UUID 生成项目专属路径，拒绝用户路径及路径穿越。 */
    private Path directory(SandboxResource resource) {
        // 资源名必须满足当前命名规则或明确的旧版规则，再组合到固定根目录下面。
        // 路径规范化后还检查父目录及根目录软链接，防止清理时越过项目专属范围。
        boolean currentName = resource.getContainerName().equals("oj-" + resource.getWorkerId() + "-" + resource.getResourceId());
        boolean legacyName = "legacy".equals(resource.getGeneration()) && resource.getContainerName()
                                .matches(Pattern.quote(resource.getWorkerId()) + "-[0-9]+");
        if (!resource.getResourceId().matches("[0-9a-f-]{36}") || (!currentName && !legacyName)) {
            throw new IllegalArgumentException("沙箱资源路径无效");
        }
        Path root = Path.of(System.getProperty("user.dir"), "user-code-pool")
                        .toAbsolutePath().normalize();
        Path result = root.resolve(resource.getContainerName()).normalize();
        // isSymbolicLink: 是否是符号链接
        if (!result.getParent().equals(root) || Files.isSymbolicLink(root)) {
            throw new IllegalStateException("沙箱目录超出项目根目录");
        }
        return result;
    }
}
