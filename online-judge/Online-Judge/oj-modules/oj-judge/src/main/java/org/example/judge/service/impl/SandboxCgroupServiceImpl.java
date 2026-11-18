package org.example.judge.service.impl;

import org.example.judge.mapper.SandboxResourceMapper;
import org.example.judge.domain.SandboxResource;
import org.example.judge.service.SandboxCgroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/** 通过固定受控脚本管理项目子树，参数仅限内部资源 UUID 和有范围校验的数值。 */
@Service
public class SandboxCgroupServiceImpl implements SandboxCgroupService {
    /** 执行环境记录访问对象，用于状态迁移、归属核对和分项回收证据保存。 */
    @Autowired
    private SandboxResourceMapper mapper;

    /** 持久预留确认后才允许创建子树；任何不确定结果抛出，交由资源恢复流程核对。 */
    @Override
    public String create(String resourceId, long memoryBytes) {
        // 只接受内部生成的固定格式身份，避免将外部路径或命令片段带入受控管理入口。
        validate(resourceId);
        if (memoryBytes < 67108864L || memoryBytes > 4294967296L) {
            throw new IllegalArgumentException("控制组内存预算无效");
        }
        SandboxResource record = mapper.find(resourceId);
        if (record == null || !"RESERVED".equals(record.getState())) {
            throw new IllegalStateException("创建控制组前必须持久化资源预留记录");
        }
        // 先记录即将发起的外部操作，返回零表示资源已失去创建资格，不能继续调用管理程序。
        if (mapper.requestCgroup(resourceId) != 1) {
            throw new IllegalStateException("控制组创建权限已撤销");
        }
        // 管理调用在数据库短操作之外执行，成功返回后才保存创建已确认的证据。
        execute("create", resourceId, Long.toString(memoryBytes));
        if (mapper.confirmCgroup(resourceId) != 1) {
            throw new IllegalStateException("控制组创建凭据尚未持久化");
        }
        return "/sys/fs/cgroup/oj-runtime-" + resourceId;
    }

    /** 删除仅作用于该 UUID 的空子树；未确认容器回收时拒绝调用。 */
    @Override
    public void remove(String resourceId) {
        validate(resourceId);
        SandboxResource record = mapper.find(resourceId);
        if (record == null || !record.isContainerDone() || record.isCreatePending()) {
            throw new IllegalStateException("删除控制组前必须确认容器清理完成");
        }
        execute("remove", resourceId, "0");
    }

    /** 严格拒绝路径和 shell 元字符，参数不能来自用户源码。 */
    private void validate(String resourceId) {
        if (resourceId == null || !resourceId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("控制组资源标识无效");
        }
    }

    /** 固定程序与参数数组执行，标准流不在内存无界积累，30 秒超时视为结果未知。 */
    private void execute(String operation, String resourceId, String memory) {
        // 在引擎管理环境运行平台脚本，参数分别传递；输出不在内存里无限保存。
        // 超时只能说明结果未知，外部资源可能已经产生，不能因此归还额度。
        Process process = null;
        try {
            // 完整创建命令示例：
            // wsl.exe -d docker-desktop -u root --exec sh -s -- create 12345678-1234-1234-1234-123456789abc 268435456
            // 选择容器引擎所在的子系统，以管理员身份运行命令解释器；脚本正文稍后从标准输入传入。
            // 三个业务参数依次表示创建操作、内部资源编号和字节单位的内存预算。
            // 例子中的目录为 /sys/fs/cgroup/oj-runtime-12345678-1234-1234-1234-123456789abc，位于引擎环境内。
            // 环境服务会将该目录挂载到容器内 /cg；它不是本机项目目录，也不是学生指定的路径。
            process = new ProcessBuilder(
                                    "wsl.exe",
                                    "-d",
                                    "docker-desktop",
                                    "-u",
                                    "root",
                                    "--exec",
                                    "sh",
                                    "-s",
                                    "--",
                                    operation,
                                    resourceId,
                                    memory)
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD) // 弃外部进程的标准输出，也就是正常执行信息。
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start();
            // getInputStream()：获取该文件的输入流，用来读取文件内容。
            try (InputStream script = new ClassPathResource("sandbox/cgroup-manager.sh").getInputStream();
                 // process.getOutputStream()：Java 向外部进程写数据。
                 OutputStream input = process.getOutputStream()) {
                // 传入平台内置脚本，用户源码和输入不参与脚本构造；参数已在入口限定格式。sh -s会执行shell命令
                script.transferTo(input);
            }
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                // 三十秒内未退出，或退出码非零，均不能确认操作完成；创建可能已部分发生，需要回收流程核对。
                throw new IllegalStateException("控制组操作结果需要核对，操作：" + operation);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("控制组操作被中断", interrupted);
        } catch (IOException failure) {
            throw new IllegalStateException("控制组管理器不可用", failure);
        } finally {
            if (process != null && process.isAlive()) {
                // 强制终止进程
                process.destroyForcibly();
            }
        }
    }
}
