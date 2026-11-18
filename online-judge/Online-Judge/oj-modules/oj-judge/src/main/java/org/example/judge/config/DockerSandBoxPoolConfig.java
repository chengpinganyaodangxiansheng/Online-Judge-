package org.example.judge.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import java.time.Duration;

import org.example.judge.mapper.SandboxResourceMapper;
import org.example.judge.service.SandboxEnvironmentService;
import org.example.judge.service.SandboxResourceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DockerSandBoxPoolConfig {
    /** 容器引擎的连接地址，用于建立客户端传输连接。 */
    @Value("${sandbox.docker.host:tcp://localhost:2375}")
    private String dockerHost;

    /** 旧版镜像配置的保留字段；本配置类当前不使用它创建执行环境。 */
    @Value("${sandbox.docker.image:openjdk:8-jdk-alpine}")
    private String sandboxImage;

    /** 旧版容器挂载点配置的保留字段，当前一次性资源池构造过程不使用此值。 */
    @Value("${sandbox.docker.volume:/usr/share/java}")
    private String volumeDir;

    /** 旧版内存限制的保留配置；当前执行环境由独立环境服务设置实际预算。 */
    @Value("${sandbox.limit.memory:100000000}")
    private Long memoryLimit;

    /** 旧版内存与交换区总量的保留配置；此字段当前不参与池创建。 */
    @Value("${sandbox.limit.memory-swap:100000000}")
    private Long memorySwapLimit;

    /** 旧版处理器限制的保留配置；实际环境预算由环境服务负责设置。 */
    @Value("${sandbox.limit.cpu:1}")
    private Long cpuLimit;

    /** 正常执行容量目标，与固定消费者并发配置使用同一配置项。 */
    @Value("${judge.task.concurrency:1}")
    private int poolSize;

    /** 回收衔接所允许的额外环境名额，与正常容量相加得到总额度上限。 */
    @Value("${sandbox.docker.pool.recovery-reserve:2}")
    private int recoveryReserve;

    /** 稳定工作节点名称前缀，用于识别本节点的容器和恢复范围。 */
    @Value("${sandbox.docker.name-prefix:oj-sandbox-jdk}")
    private String containerNamePrefix;

    /** 按引擎地址创建客户端，设置连接与响应超时及连接池上限，避免容器调用无限等待。 */
    @Bean
    public DockerClient createDockerClient() {
        // 创建默认配置构建器
        DefaultDockerClientConfig clientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                        // 设置docker主机配置
                        .withDockerHost(dockerHost)
                        // 完成连接配置构建，供后面的传输客户端使用。
                        .build();

        // Apache 传输支持 Docker Desktop 的 Windows 命名管道，设置超时避免连接永久阻塞。
        return DockerClientImpl.getInstance(clientConfig, new ApacheDockerHttpClient.Builder()
                        // 将配置中的引擎地址交给实际负责网络通信的客户端。
                        .dockerHost(clientConfig.getDockerHost())
                        // 沿用配置中的加密连接设置，使传输方式与引擎配置一致。
                        .sslConfig(clientConfig.getSSLConfig())
                        // 建立连接最多等待五秒，超过后向调用方报告连接失败。
                        .connectionTimeout(Duration.ofSeconds(5))
                        // 等待引擎响应最多四十秒，避免操作永久占住线程。
                        .responseTimeout(Duration.ofSeconds(40))
                        // 连接上限取八和正常池容量四倍中的较大值，兼顾小池最低连接数和并发调用需求。
                        .maxConnections(Math.max(8, poolSize * 4))
                        // 构建传输客户端，再交给外层调用组合成容器引擎客户端。
                        .build());
    }

    /** 注入持久化、额度和环境服务，按正常容量加回收余量构造池，完成启动核对后交给容器管理。 */
    @Bean
    public DockerSandBoxPool createDockerSandBoxPool(DockerClient dockerClient, SandboxResourceMapper mapper, SandboxResourceService resources, SandboxEnvironmentService environments) {
        // 创建docker容器池 dockerClient是上面的Bean方法获取
        DockerSandBoxPool dockerSandBoxPool = new DockerSandBoxPool(
                        dockerClient,
                        mapper,
                        resources,
                        environments,
                        containerNamePrefix,
                        poolSize,
                        // 正常容量加上回收余量就是硬上限，回收尚未完成的环境仍占用其中的额度。
                        poolSize + recoveryReserve);

        // 开始初始化docker容器池
        dockerSandBoxPool.initDockerPool();

        // 返回docker容器池对象
        return dockerSandBoxPool;
    }
}
