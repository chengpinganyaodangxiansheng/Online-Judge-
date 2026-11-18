package org.example.judge.service.impl;

import org.example.api.domain.dto.JudgeSubmitDTO;
import org.example.common.core.enums.CodeRunStatus;
import org.example.judge.domain.*;
import org.example.judge.service.*;
import org.example.judge.config.DockerSandBoxPool;
import org.example.judge.util.JudgeOutputNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.example.api.domain.vo.JudgeStageEvidenceVO;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 一次发送提交，持续接收编译与用例证据；不再逐用例执行 Docker exec。 */
@Service
@lombok.extern.slf4j.Slf4j
public class SandboxPoolServiceImpl implements SandboxPoolService {
    /** 可信执行器通信服务，维护连接并发送提交请求、接收有界阶段证据。 */
    @Autowired private SandboxRuntimeService runtime;

    /** 阶段证据分类服务，按固定优先级区分用户错误和基础设施异常。 */
    @Autowired private SandboxVerdictService verdicts;

    /** 一次性容器池，用于在执行完成或中断时终止并退役本轮容器。 */
    @Autowired private DockerSandBoxPool pool;

    /**
     * 协调一次提交的执行：发送源码和用例输入，接收编译及运行结果，再逐例比较答案。
     * 第一个参数是提交受理时保存的固定题目规则，避免判题过程中题目更新影响本次结果。
     * 第二个参数保存已经领取的容器和本地截止时间，本方法不用重新申请容器。
     * 可以确定的编译失败、答案错误或资源超限会返回结果；通信故障、证据缺失等抛出异常，
     * 由上层任务服务决定是否重试。结果写入数据库和消息确认也由上层负责。
     */
    @Override
    public SandBoxExecuteResult exeJavaCode(JudgeSubmitDTO snapshot, JudgeExecution execution) {
        // 第一步：先确认本地执行没有被撤销，也没有超过保护期限。
        // 例如续租线程发现执行权丢失后会撤销此上下文，此时不能再发送用户代码。
        execution.checkActive();
        // 快照保存了当时使用的答案比较规则。遇到不认识的规则应报系统异常，
        // 不能擅自换一种比较方式，否则相同提交重试后可能得到不同判定。
        if (!"NORMALIZE_LINE_ENDINGS_IGNORE_TRAILING_NEWLINES_V1".equals(snapshot.getOutputComparison())) {
            throw new IllegalStateException("不支持该快照的输出比较规则");
        }
        // 为本次通信生成独立标识，收到响应时用它核对归属，防止混入其他执行的消息。
        // 它是通信协议的标识，不是数据库中用于提交判题结果的执行令牌。
        String attempt = UUID.randomUUID().toString();
        // 用固定截止时间减去当前单调时钟，得到剩余预算；准备阶段花掉的时间不能重新补回。
        long budget = TimeUnit.NANOSECONDS.toMillis(execution.getDeadline() - System.nanoTime());
        // 第二步：一次发送源码、全部用例输入和资源限制，剩余预算最多取十分钟。
        // 容器内的可信管理程序负责编译一次并顺序运行用例；期望答案留在后端，不发给用户容器。
        runtime.submit(execution.getContainerId(), attempt, snapshot, Math.min(600000, budget));
        // 下一条应该收到的阶段编号：负一是编译，零是第一个用例，然后依次递增。
        int expected = -1;
        // 最终展示每个用例中的最大处理器耗时和内存峰值，而不是把所有用例的消耗相加。
        long maxCpu = 0;
        Long peak = null;
        // 实际输出按用例顺序保存；执行证据额外保存退出码、超限原因和清理状态，便于排查。
        List<String> outputs = new ArrayList<>();
        List<JudgeStageEvidenceVO> evidence = new ArrayList<>();
        try {
            // 持续接收编译和用例消息；成功或失败由返回语句结束，执行权失效或超时由异常结束。
            while (true) {
                // 第三步：循环接收结果。每轮都重新检查执行资格，不能只在方法开始时检查一次。
                execution.checkActive();
                // 最多等待一百毫秒取得下一条消息，空值表示本轮暂未收到数据。
                SandboxRuntimeMessage message = runtime.receive(execution.getContainerId(), 100);
                // 一次最多等一百毫秒。暂时没有消息只代表还没收到结果，不能判用户代码失败。
                // 返回循环后重新检查截止时间，使长时间无响应也能被总保护期限终止。
                if (message == null) {
                    continue;
                }
                // 即使消息来自这个容器，也必须确认是本次提交级调用的响应。
                if (!attempt.equals(message.getAttempt())) {
                    throw new IllegalStateException("执行器返回的执行标识不匹配");
                }
                // 类型三表示执行器宣布本次提交结束；还要核对是否已经收到全部用例的结果。
                // 不能只看到“结束”就判成功，否则中途丢失结果也可能被当成全部通过。
                if (message.getType() == 3) {
                    // 全部用例处理后，下一编号应恰好等于用例总数，否则说明结果缺失。
                    if (expected != snapshot.getInputList().size()) {
                        throw new IllegalStateException("缺少测试用例执行凭据");
                    }
                    // 能走到这里，说明编译及所有用例都按顺序检查过，没有发现答案错误或超限。
                    SandBoxExecuteResult success = SandBoxExecuteResult.success(
                            CodeRunStatus.SUCCEED, outputs, peak, maxCpu);
                    success.setStageEvidence(evidence);
                    // 所有阶段和答案都已验证成功，返回结果并执行最终清理代码。
                    return success;
                }
                // 其余合法消息应是类型二的阶段证据，而且编号必须正好等于当前等待的编号。
                // 编号跳跃、重复或乱序都不能直接跳过，否则实际输出可能与错误的期望答案比较。
                if (message.getType() != 2 || message.getIndex() != expected) {
                    throw new IllegalStateException("执行器返回的用例顺序不匹配");
                }
                // 第四步：把通信对象里的证据复制到业务结果对象，供上层保存到任务结果中。
                // 这些字段记录的是实际执行情况，不能仅凭一段报错文字猜测内存超限或超时。
                JudgeStageEvidenceVO stage = new JudgeStageEvidenceVO();
                // 负一是编译阶段，非负数是用例顺序编号。
                stage.setIndex(expected);
                // 保存主进程退出码，供排查异常退出原因。
                stage.setExitCode(message.getEvidence().getExitCode());
                // 保存执行器确认的超时标志。
                stage.setTimedOut(message.getEvidence().isTimedOut());
                // 保存标准输出或错误输出超过限制的标志。
                stage.setOutputExceeded(message.getEvidence().isOutputExceeded());
                // 保存内核证据确认的内存超限标志。
                stage.setMemoryExceeded(message.getEvidence().isMemoryExceeded());
                // 保存是否已确认清理完成，不能仅凭主进程退出认定环境干净。
                stage.setCleanupComplete(message.getEvidence().isCleanupComplete());
                // 保存资源准备阶段耗时，单位为微秒。
                stage.setPreparationMicros(message.getPreparationMicros());
                // 保存从启动到退出实际经过的时间，其中也包含等待时间。
                stage.setWallMicros(message.getWallMicros());
                // 保存终止残留进程和清理阶段所用的时间。
                stage.setCleanupMicros(message.getCleanupMicros());
                // 分开记录准备、运行和清理时间，方便判断慢在用户程序还是平台准备与回收。
                log.info("沙箱阶段执行记录：容器编号={}，执行标识={}，用例序号={}，准备耗时（微秒）={}，实际经过时间（微秒）={}，处理器耗时（微秒）={}，清理耗时（微秒）={}，是否完成清理={}",
                        execution.getContainerId(),
                        attempt,
                        expected,
                        message.getPreparationMicros(),
                        message.getWallMicros(),
                        message.getCpuMicros(),
                        message.getCleanupMicros(),
                        stage.isCleanupComplete());
                stage.setCpuMicros(message.getCpuMicros());
                // 保存字节单位的内存峰值；没有测量值时保留空值。
                stage.setPeakBytes(message.getPeakBytes());
                // 按收到的顺序累计证据，最终随判题结果交给上层保存。
                evidence.add(stage);
                // 第五步：先判断运行本身是否有效，再比较答案。第二个参数说明当前是不是编译。
                // 分类器会区分编译失败、运行错误及资源超限；无错误且清理确认时返回空值。
                // 证据不完整等平台问题会抛异常，不能伪装成用户答案错误。
                SandBoxExecuteResult failure = verdicts.classify(message.getEvidence(), expected == -1);
                if (failure != null) {
                    // 已经有确定失败就返回，同时保留前面用例的输出和本阶段证据供上层追溯。
                    failure.setOutputList(outputs);
                    // 失败结果也携带证据，便于解释失败发生在哪一阶段。
                    failure.setStageEvidence(evidence);
                    return failure;
                }
                // 编译阶段没有需要比较的答案，只有编号从零开始的运行用例进入此分支。
                if (expected >= 0) {
                    // 读取当前运行用例的实际标准输出。
                    String output = message.getEvidence().getOutput();
                    // 按用例顺序保存输出，使结果与题目输入一一对应。
                    outputs.add(output);
                    // 微秒转毫秒时向上取整，避免不足一毫秒的消耗被记成零，再保留最大值。
                    maxCpu = Math.max(maxCpu, (message.getCpuMicros() + 999) / 1000);
                    if (message.getPeakBytes() != null) {
                        // 仅有真实测量值时更新峰值，缺失数据不能被当成零内存。
                        // 字节转千字节同样向上取整；峰值缺失时保留未知，不编造为零内存。
                        peak = Math.max(peak == null ? 0 : peak, (message.getPeakBytes() + 1023) / 1024);
                    }
                    // 双方都统一换行并忽略末尾换行，然后比较；空格等有效内容仍然保留。
                    // 第一个答案错误立即结束，不让后面用例的其他错误覆盖顺序上的首次失败。
                    if (!JudgeOutputNormalizer.normalize(output)
                            .equals(JudgeOutputNormalizer.normalize(snapshot.getOutputList().get(expected)))) {
                        SandBoxExecuteResult wrong = SandBoxExecuteResult.fail(CodeRunStatus.NOT_ALL_PASSED);
                        // 保留直到当前错误用例为止的实际输出。
                        wrong.setOutputList(outputs);
                        // 同时保留已经收到的阶段证据。
                        wrong.setStageEvidence(evidence);
                        // 首个答案错误就结束判题，最终清理代码会尝试终止容器。
                        return wrong;
                    }
                }
                // 当前阶段检查通过后，才允许接收下一阶段；编译通过后编号正好从负一变成零。
                expected++;
            }
        } finally {
            // 无论正常返回还是抛异常，进入接收流程后都要尝试停止容器中的剩余执行。
            // 后端可能在首个答案错误时提前返回，而容器尚在继续运行，因此不能省略终止。
            // 此处只是终止；完整退役和资源回收由调用方及后台回收流程继续处理。
            try {
                pool.terminate(execution.getContainerId());
            } catch (RuntimeException cleanupFailure) {
                // 终止失败只记录待处理情况，不覆盖已经得到的有效判定或原始执行异常。
                log.warn("执行器终止待处理，容器编号={}", execution.getContainerId());
            }
        }
    }
}
