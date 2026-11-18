package org.example.judge.service.impl;


import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.netty.NettyDockerCmdExecFactory;
import org.example.common.core.constants.Constants;
import org.example.common.core.constants.JudgeConstants;
import org.example.common.core.enums.CodeRunStatus;
import org.example.judge.callback.DockerStartResultCallback;
import org.example.judge.callback.StatisticsCallback;
import org.example.judge.domain.CompileResult;
import org.example.judge.domain.SandBoxExecuteResult;
import org.example.judge.service.SandboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SandboxServiceImpl implements SandboxService {

    //Docker主机配置：从配置文件中(微服务是从nacos)获取Docker守护进程地址，默认使用本地的2375端口（Docker API端口）
    @Value("${sandbox.docker.host:tcp://localhost:2375}")
    private String dockerHost;

    //内存限制（默认100MB）
    @Value("${sandbox.limit.memory:100000000}")
    private Long memoryLimit;

    //内存+Swap限制（默认100MB）
    @Value("${sandbox.limit.memory-swap:100000000}")
    private Long memorySwapLimit;

    //CPU核心数（默认1核）
    @Value("${sandbox.limit.cpu:1}")
    private Long cpuLimit;

    //执行时间限制（默认5秒）
    @Value("${sandbox.limit.time:5}")
    private Long timeLimit;

    //Docker API客户端实例
    private DockerClient dockerClient;

    //当前创建的容器ID
    private String containerId;

    //用户代码目录路径
    private String userCodeDir;

    //用户代码文件路径
    private String userCodeFileName;



    @Override
    public SandBoxExecuteResult exeJavaCode(Long userId, String userCode, List<String> inputList) {
        createUserCodeFile(userId, userCode);
        //初始化docker容器(将用户代码文件实现挂载目录 绑定本地和容器的用户代码文件的地址)
        initDockerSanBox();
        //编译代码
        CompileResult compileResult = compileCodeByDocker();
        if (!compileResult.isCompiled()) {
            deleteContainer();
            deleteUserCodeFile();
            return SandBoxExecuteResult.fail(CodeRunStatus.COMPILE_FAILED, compileResult.getExeMessage());
        }
        //执行代码
        return executeJavaCodeByDocker(inputList);
    }


    //创建并返回用户代码的文件
    private void createUserCodeFile(Long userId, String userCode) {
        //System.getProperty("user.dir")：获取当前JVM工作目录（项目根目录）
        //File.separator：使用系统文件分隔符（Windows为\，Linux为/）
        //JudgeConstants.EXAM_CODE_DIR：常量（如"user-code"），表示代码存储的主目录
        //构建形如/project_root/user-code/的路径
        String examCodeDir = System.getProperty("user.dir") + File.separator + JudgeConstants.EXAM_CODE_DIR;
        //使用工具类FileUtil.exist()判断主目录是否存在
        if (!FileUtil.exist(examCodeDir)) {
            //创建存放用户代码的目录
            FileUtil.mkdir(examCodeDir);
        }
        //生成时间戳
        //LocalDateTime.now()：获取当前精确时间（包含年月日时分秒）
        //DateTimeFormatter：定义时间格式为yyyyMMddHHmmss（如20230805143015）
        //目的：确保每次提交生成唯一目录，避免覆盖历史记录
        String time = LocalDateTimeUtil.format(LocalDateTime.now(), DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        //拼接用户代码文件格式
        //组合路径：examCodeDir/userId_time
        //示例：/user-code/12345_20230805143015/
        //使用用户ID和时间戳保证目录唯一性
        userCodeDir = examCodeDir + File.separator + userId + Constants.UNDERLINE_SEPARATOR + time;

        //构建代码文件路径：
        //JudgeConstants.USER_CODE_JAVA_CLASS_NAME：常量（如"Solution.java"）
        //最终路径：/user-code/12345_20230805143015/Solution.java
        //目的：固定文件名便于后续编译/执行
        userCodeFileName = userCodeDir + File.separator + JudgeConstants.USER_CODE_JAVA_CLASS_NAME;

        //写入代码文件：
        //FileUtil.writeString()：工具方法将字符串写入文件
        //参数：
        //userCode：用户提交的源代码字符串
        //userCodeFileName：上一步构建的完整文件路径
        //Constants.UTF8：指定UTF-8编码（避免中文乱码）
        //效果：在目标路径创建.java文件并写入代码
        FileUtil.writeString(userCode, userCodeFileName, Constants.UTF8);
    }

    private void initDockerSanBox() {
        //Docker客户端初始化：
        //使用docker-java库创建Docker客户端

        //createDefaultConfigBuilder()：创建默认配置构建器
        DefaultDockerClientConfig clientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                //withDockerHost(dockerHost)：指定Docker守护进程的地址（例如：tcp://localhost:2375）
                .withDockerHost(dockerHost)
                //build()：生成最终配置对象
                .build();

        //通过配置实例化Docker客户端
        dockerClient = DockerClientBuilder
                //getInstance(clientConfig)：传入上一步的配置
                .getInstance(clientConfig)
                //NettyDockerCmdExecFactory：使用Netty框架进行网络通信（高性能、异步IO）
                .withDockerCmdExecFactory(new NettyDockerCmdExecFactory())
                //build()：最终构建DockerClient实例
                .build();

        //拉取镜像
        pullJavaEnvImage();

        //创建容器  限制资源   控制权限
        HostConfig hostConfig = getHostConfig();

        //容器创建：
        CreateContainerCmd containerCmd = dockerClient
                //createContainerCmd()：创建容器命令
                .createContainerCmd(JudgeConstants.JAVA_ENV_IMAGE)
                //withName()：设置容器名称
                .withName(JudgeConstants.JAVA_CONTAINER_NAME);

        //使用 Docker Java API 创建 并配置一个容器实例的核心操作
        CreateContainerResponse createContainerResponse = containerCmd
                //withHostConfig()：设置主机配置（资源限制）
                .withHostConfig(hostConfig)
                //withAttachXxx()：附加标准输出/错误流
                .withAttachStderr(true)
                .withAttachStdout(true)
                //withTty(true)：分配伪终端
                .withTty(true)
                .exec();

        //记录容器id
        containerId = createContainerResponse.getId();

        //容器启动：
        //startContainerCmd()：启动创建的容器
        dockerClient.startContainerCmd(containerId).exec();
    }

    //拉取java执行环境镜像 需要控制只拉取一次
    private void pullJavaEnvImage() {
        //获取本地Docker环境中的所有镜像列表
        ListImagesCmd listImagesCmd = dockerClient.listImagesCmd();
        List<Image> imageList = listImagesCmd.exec();
        //遍历检查目标镜像是否存在
        for (Image image : imageList) {
            //getRepoTags()：获取镜像的标签数组（如["openjdk:11", "java:latest"]）
            String[] repoTags = image.getRepoTags();
            if (repoTags != null && repoTags.length > 0 && JudgeConstants.JAVA_ENV_IMAGE.equals(repoTags[0])) {
                return;
            }
        }
        //拉取镜像（如果不存在）  pullImageCmd()：创建拉取镜像的命令
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(JudgeConstants.JAVA_ENV_IMAGE);
        try {
            //PullImageResultCallback：处理拉取过程的回调（继承自ResultCallback.Adapter）
            //awaitCompletion()：阻塞直到拉取完成
            pullImageCmd.exec(new PullImageResultCallback()).awaitCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    //限制资源   控制权限
    //创建并配置 Docker 容器的运行时参数，实现 资源限制 和 安全隔离。
    private HostConfig getHostConfig() {
        HostConfig hostConfig = new HostConfig();
        //设置挂载目录，指定用户代码路径  将宿主机目录 (userCodeDir) 挂载到容器内部 (DOCKER_USER_CODE_DIR)。
        //userCodeDir：宿主机上的用户代码目录(本地用户代码文件地址)（如 /user-code/123_20230801）。
        //JudgeConstants.DOCKER_USER_CODE_DIR：容器内挂载点(也就是在docker容器中用户代码文件所处的位置)（如 /usr/share/java）。
        hostConfig.setBinds(new Bind(userCodeDir, new Volume(JudgeConstants.DOCKER_USER_CODE_DIR)));
        //限制docker容器使用资源
        hostConfig.withMemory(memoryLimit);
        //限制容器 内存 + Swap 的总使用量
        hostConfig.withMemorySwap(memorySwapLimit);
        //限制容器可用的 CPU 核心数。
        hostConfig.withCpuCount(cpuLimit);
        //禁用网络
        hostConfig.withNetworkMode("none");
        //禁止在root目录写文件
        hostConfig.withReadonlyRootfs(true);
        return hostConfig;
    }

    //编译

    private CompileResult compileCodeByDocker() {
        //createExecCmd:封装的方法，用于构造容器内执行的命令。(此处是进行编译指令)
        String cmdId = createExecCmd(JudgeConstants.DOCKER_JAVAC_CMD, null, containerId);
        //初始化回调处理器,捕获命令执行的输出和错误流。
        DockerStartResultCallback resultCallback = new DockerStartResultCallback();
        //准备编译结果容器
        CompileResult compileResult = new CompileResult();
        try {
            //执行编译命令
            //execStartCmd(cmdId)：通过 execId 启动之前创建的命令
            dockerClient.execStartCmd(cmdId)
                    //exec(resultCallback)：绑定回调处理器，实时接收输出
                    .exec(resultCallback)
                    //awaitCompletion()：阻塞当前线程直到命令执行完成
                    .awaitCompletion();
            if (CodeRunStatus.FAILED.equals(resultCallback.getCodeRunStatus())) {
                //设置 编译失败
                //记录错误信息（如语法错误）
                compileResult.setCompiled(false);
                compileResult.setExeMessage(resultCallback.getErrorMessage());
            } else {
                compileResult.setCompiled(true);
            }
            return compileResult;
        } catch (InterruptedException e) {
            //此处可以直接抛出 已做统一异常处理  也可再做定制化处理
            throw new RuntimeException(e);
        }
    }

    //在 Docker 容器中执行用户代码，并处理多组输入用例
    private SandBoxExecuteResult executeJavaCodeByDocker(List<String> inputList) {
        //记录输出结果
        List<String> outList = new ArrayList<>();
        //最大占用内存
        long maxMemory = 0L;
        //最大运行时间
        long maxUseTime = 0L;
        //执行用户代码
        for (String inputArgs : inputList) {
            //此处是使用的运行代码指令
            String cmdId = createExecCmd(JudgeConstants.DOCKER_JAVA_EXEC_CMD, inputArgs, containerId);
            //执行代码,执行代码后开始计时
            StopWatch stopWatch = new StopWatch();
            //执行情况监控,启动监控
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            //监控统计指令所占用的最大内存统计
            StatisticsCallback statisticsCallback = statsCmd.exec(new StatisticsCallback());
            //执行计时
            stopWatch.start();
            //监控指令的执行结果
            DockerStartResultCallback resultCallback = new DockerStartResultCallback();
            try {
                // 执行用户代码
                dockerClient.execStartCmd(cmdId)
                        // resultCallback：捕获程序输出（stdout/stderr）
                        .exec(resultCallback)
                        //timeLimit：单个用例的超时时间（如 5 秒）
                        .awaitCompletion(timeLimit, TimeUnit.SECONDS);
                if (CodeRunStatus.FAILED.equals(resultCallback.getCodeRunStatus())) {
                    //未通过所有用例返回结果
                    return SandBoxExecuteResult.fail(CodeRunStatus.NOT_ALL_PASSED);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            //结束时间统计
            stopWatch.stop();
            //结束docker容器执行统计
            statsCmd.close();
            //执行耗时
            long userTime = stopWatch.getLastTaskTimeMillis();
            //记录最大的执行用例耗时
            maxUseTime = Math.max(userTime, maxUseTime);

            Long memory = statisticsCallback.getMaxMemory();

            if (memory != null) {
                //记录最大的执行用例占用内存
                maxMemory = Math.max(maxMemory, statisticsCallback.getMaxMemory());
            }
            //记录正确的输出结果
            outList.add(resultCallback.getMessage().trim());
        }
        //删除容器
        deleteContainer();
        //清理文件
        deleteUserCodeFile();
        //封装结果
        return getSanBoxResult(inputList, outList, maxMemory, maxUseTime);
    }

    //构造一个可在 Docker 容器内执行的命令，返回 execId 用于后续执行
    private String createExecCmd(String[] javaCmdArr, String inputArgs, String containerId) {
        //无参数时：直接使用原始 javaCmdArr
        //有参数时：
        //inputArgs.split(" ")：将字符串参数拆分为数组（如 "1 2" → ["1", "2"]）
        //ArrayUtil.append()：将参数追加到基础命令后
        if (!StrUtil.isEmpty(inputArgs)) {
            //当入参不为空时拼接入参
            String[] inputArray = inputArgs.split(" ");
            //入参
            javaCmdArr = ArrayUtil.append(JudgeConstants.DOCKER_JAVA_EXEC_CMD, inputArray);
        }

        //创建 Docker 执行命令
        ExecCreateCmdResponse cmdResponse = dockerClient.execCreateCmd(containerId)
                //withCmd()	设置实际执行的命令数组
                .withCmd(javaCmdArr)
                //withAttachStderr()	捕获 stderr（编译/运行时错误）
                .withAttachStderr(true)
                //withAttachStdout()	捕获 stdout（程序输出）
                .withAttachStdin(true)
                //withAttachStdin()	允许通过 stdin 输入数据
                .withAttachStdout(true)
                .exec();
        // 返回执行 ID
        return cmdResponse.getId();
    }

    private SandBoxExecuteResult getSanBoxResult(List<String> inputList,
                                                 List<String> outList,
                                                 long maxMemory,
                                                 long maxUseTime) {
        if (inputList.size() != outList.size()) {
            //输入用例数量 不等于 输出用例数量  属于执行异常
            return SandBoxExecuteResult.fail(CodeRunStatus.NOT_ALL_PASSED, outList, maxMemory, maxUseTime);
        }
        return SandBoxExecuteResult.success(CodeRunStatus.SUCCEED, outList, maxMemory, maxUseTime);
    }

    private void deleteContainer() {
        //执行完成之后删除容器
        dockerClient.stopContainerCmd(containerId).exec();
        dockerClient.removeContainerCmd(containerId).exec();
        //断开和docker连接
        try {
            dockerClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteUserCodeFile() {
        FileUtil.del(userCodeDir);
    }
}