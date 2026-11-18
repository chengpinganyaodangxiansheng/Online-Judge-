package org.example.judge.service;

import org.example.api.domain.dto.JudgeSubmitDTO;
import org.example.judge.domain.JudgeExecution;
import org.example.judge.domain.SandBoxExecuteResult;
import org.springframework.stereotype.Service;

import java.util.List;

/** 沙箱执行接口：按固定快照编译、运行，返回原始执行证据供 JudgeService 判定。 */
@Service
public interface SandboxPoolService {
    /** 使用不可变快照和调用方持有的执行上下文发送一次提交，按顺序收集编译及用例证据；租约失效或保护超时后停止推进，返回原始执行结果供业务层判定。 */
    SandBoxExecuteResult exeJavaCode(JudgeSubmitDTO snapshot, JudgeExecution execution);
}
