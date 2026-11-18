package org.example.judge.domain;

import lombok.Data;
import org.example.api.domain.vo.JudgeStageEvidenceVO;
import org.example.common.core.enums.CodeRunStatus;

import java.util.List;

@Data
public class SandBoxExecuteResult {
    /** 编译及各运行用例的有界执行证据，用于诊断判定原因和清理情况。 */
    private List<JudgeStageEvidenceVO> stageEvidence;

    /** 沙箱执行状态，供业务层映射为通过、答案错误及各类超限判定。 */
    private CodeRunStatus runStatus; // 执行结果

    /** 执行失败或超限的诊断摘要，向调用方解释判定原因。 */
    private String exeMessage; // 异常信息

    /** 按用例顺序保存用户程序的实际输出，后端与提交快照中的期望输出逐项比较。 */
    private List<String> outputList; // 执行结果

    /** 本次执行观测到的最大用例内存峰值，单位为千字节，不能单独作为内存超限证据。 */
    private Long useMemory; // 占用内存  kb

    /** 本次执行各用例中的最大处理器耗时，单位为毫秒，不包含编译与清理。 */
    private Long useTime; // 消耗时间   ms

    /** 构造失败执行结果，保存调用方提供的状态及可选诊断、输出和统计；不在此决定重试策略。 */
    public static SandBoxExecuteResult fail(CodeRunStatus runStatus, String errorMsg) {
        SandBoxExecuteResult result = new SandBoxExecuteResult();
        result.setRunStatus(runStatus);
        result.setExeMessage(errorMsg);
        return result;
    }

    /** 构造失败执行结果，保存调用方提供的状态及可选诊断、输出和统计；不在此决定重试策略。 */
    public static SandBoxExecuteResult fail(CodeRunStatus runStatus) {
        SandBoxExecuteResult result = new SandBoxExecuteResult();
        result.setRunStatus(runStatus);
        result.setExeMessage(runStatus.getMsg());
        return result;
    }

    /** 构造失败执行结果，保存调用方提供的状态及可选诊断、输出和统计；不在此决定重试策略。 */
    public static SandBoxExecuteResult fail(CodeRunStatus runStatus, List<String> outputList, Long useMemory, Long useTime) {
        SandBoxExecuteResult result = new SandBoxExecuteResult();
        result.setRunStatus(runStatus);
        result.setExeMessage(runStatus.getMsg());
        result.setOutputList(outputList);
        result.setUseMemory(useMemory);
        result.setUseTime(useTime);
        return result;
    }

    /** 构造成功执行结果，附带按序输出、内存峰值和处理器耗时，业务层仍须比较期望答案。 */
    public static SandBoxExecuteResult success(CodeRunStatus runStatus, List<String> outputList, Long useMemory, Long useTime) {
        SandBoxExecuteResult result = new SandBoxExecuteResult();
        result.setRunStatus(runStatus);
        result.setOutputList(outputList);
        result.setUseMemory(useMemory);
        result.setUseTime(useTime);
        return result;
    }
}
