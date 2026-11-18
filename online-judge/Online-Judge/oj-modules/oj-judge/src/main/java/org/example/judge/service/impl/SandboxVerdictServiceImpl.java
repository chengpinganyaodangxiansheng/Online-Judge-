package org.example.judge.service.impl;

import org.example.common.core.enums.CodeRunStatus;
import org.example.judge.domain.SandboxStageResult;
import org.example.judge.domain.SandBoxExecuteResult;
import org.example.judge.service.SandboxVerdictService;
import org.springframework.stereotype.Service;

/** 固定分类优先级，不依赖输出文本或异步回调顺序。 */
@Service
public class SandboxVerdictServiceImpl implements SandboxVerdictService {
    /** 平台故障抛给有限重试；仅使用当前阶段已确认的资源超限原因形成用户判定。 */
    @Override
    public SandBoxExecuteResult classify(SandboxStageResult evidence, boolean compilation) {
        // 证据缺失或平台故障交由任务层恢复，不能降格为用户编译或运行错误。
        if (evidence == null || evidence.isInfrastructureFailure()) {
            throw new IllegalStateException("沙箱阶段执行凭据不可用");
        }
        String reason = null;
        // 空值表示暂未发现可确定的用户程序错误，后面按固定顺序检查各类证据。
        CodeRunStatus verdict = null;
        // 固定优先级仅比较同一阶段的可信原因，不依赖回调先后，也不跨用例重新排序。
        if (evidence.isMemoryExceeded()) {
            // 优先使用已确认的内存超限证据；即使同时超时，也先归类为内存超限。
            reason = "MEMORY_LIMIT";
            // 保存面向判题结果的状态枚举。
            verdict = CodeRunStatus.OUT_OF_MEMORY;
        } else if (evidence.isOutputExceeded()) {
            // 没有内存超限时，再判断输出是否超过限制。
            reason = "OUTPUT_LIMIT";
            // 将结果归类为输出超限。
            verdict = CodeRunStatus.OUTPUT_LIMIT;
        } else if (evidence.isTimedOut()) {
            // 前两项未命中时，再依据执行器的时间超限标志选择超时原因。
            reason = "TIMEOUT";
            // 将结果归类为执行超时。
            verdict = CodeRunStatus.OUT_OF_TIME;
        } else if (evidence.getExitCode() != 0) {
            // 没有已确认超限，但进程以非零退出码结束，说明编译或运行本身失败。
            reason = "ERROR";
            // 暂存普通失败，编译阶段将在构造结果时统一转换为编译失败。
            verdict = CodeRunStatus.FAILED;
        }
        // 尚未发现用户失败时，需要进一步确认清理状态，才能允许调用方继续处理。
        if (verdict == null) {
            // 正常退出但残留清理未确认时，后续用例可能受污染，作为平台异常抛出。
            if (!evidence.isCleanupComplete()) {
                throw new IllegalStateException("沙箱清理结果尚未确认");
            }
            return null;
        }
        // 编译阶段使用编译失败状态，运行阶段使用刚选出的具体状态，并拼接内部原因编号。
        SandBoxExecuteResult result = SandBoxExecuteResult.fail(
                        compilation ? CodeRunStatus.COMPILE_FAILED : verdict,
                        (compilation ? "COMPILE_" : "RUN_") + reason
        );
        // 公开诊断只保留有限片段，输出截断显式标记，不能再次保存完整超限日志。
        String diagnostic = evidence.getError();
        // 有非空错误文本才截取并附加，避免对空值调用字符串方法。
        if (diagnostic != null && !diagnostic.isEmpty()) {
            // 最多保留4096个字符，防止错误诊断占据过多存储空间。
            int limit = Math.min(diagnostic.length(), 4096);
            // 追加限长诊断；内容被截短或已确认输出超限时，另附截断标记。
            result.setExeMessage(result.getExeMessage()
                            + ": " + diagnostic.substring(0, limit)
                            + (diagnostic.length() > limit || evidence.isOutputExceeded() ? " [truncated]" : ""));
        } else if (evidence.isOutputExceeded()) {
            // 即使没有错误文本，已确认输出超限时也必须标明截断。
            result.setExeMessage(result.getExeMessage() + " [truncated]");
        }
        return result;
    }
}
