package org.example.judge.service;

import org.example.judge.domain.SandboxStageResult;
import org.example.judge.domain.SandBoxExecuteResult;

/** 将可信阶段证据映射为单一判定，不执行 Docker 操作或持久化任务。 */
public interface SandboxVerdictService {
    /** 根据可信阶段证据分类：编译错误统一为编译未通过，运行阶段依次优先内存、输出、时间超限和普通运行错误；通过且清理确认时返回空值，平台故障抛出异常。 */
    SandBoxExecuteResult classify(SandboxStageResult evidence, boolean compilation);
}
