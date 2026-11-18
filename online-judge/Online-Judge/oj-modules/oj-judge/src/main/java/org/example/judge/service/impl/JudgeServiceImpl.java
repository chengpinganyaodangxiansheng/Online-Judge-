package org.example.judge.service.impl;

import org.example.api.domain.UserExeResult;
import org.example.api.domain.dto.JudgeSubmitDTO;
import org.example.api.domain.vo.UserQuestionResultVO;
import org.example.common.core.constants.Constants;
import org.example.common.core.constants.JudgeConstants;
import org.example.common.core.enums.CodeRunStatus;
import org.example.judge.config.DockerSandBoxPool;
import org.example.judge.domain.JudgeExecution;
import org.example.judge.domain.SandBoxExecuteResult;
import org.example.judge.service.JudgeService;
import org.example.judge.service.SandboxPoolService;
import org.example.judge.util.JudgeOutputNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 将沙箱执行证据转为业务判题结果；任务持久化及比赛计分由 JudgeTaskService 负责。 */
@Service
public class JudgeServiceImpl implements JudgeService {

    /** 提交级沙箱执行服务，负责运行固定快照并返回有界阶段证据，不是线程池。 */
    @Autowired
    private
    SandboxPoolService sandboxPoolService;

    /** 一次性就绪容器池，提供领取、退役、回收和可用资源探测。 */
    @Autowired
    private DockerSandBoxPool dockerPool;

    /**
     * 为直接调用方领取容器并执行，异步消费者使用携带 JudgeExecution 的重载。
     * @param judgeSubmitDTO 已组装的执行快照
     * @return 判题结果；沙箱无法执行时抛出系统异常
     */
    public UserQuestionResultVO doJudgeJavaCode(JudgeSubmitDTO judgeSubmitDTO) {
        // 直接调用入口先等待容器，拿不到就报系统异常；可靠消费者使用传入已有上下文的重载。
        // 使用后退役，不把执行过用户程序的容器重新给另一份提交使用。
        String container;
        try {
            container = dockerPool.pollContainer(5000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        if (container == null) {
            throw new IllegalStateException("沙箱执行容量不可用");
        }
        try {
            return doJudgeJavaCode(judgeSubmitDTO, new JudgeExecution(container, 600));
        } finally {
            dockerPool.retire(container);
        }
    }

    /** 使用消费者已领取的容器执行；可判定的用户代码错误返回结果，基础设施异常向上抛出。 */
    @Override
    public UserQuestionResultVO doJudgeJavaCode(JudgeSubmitDTO judgeSubmitDTO, JudgeExecution execution) {
        // 从沙盒中获取运行结果
        //        SandBoxExecuteResult sandBoxExecuteResult =
        //                sandboxService.exeJavaCode(judgeSubmitDTO.getUserId(),
        //                judgeSubmitDTO.getUserCode(),
        //                judgeSubmitDTO.getInputList());

        // 使用docker池，来运行用户提交的代码
        SandBoxExecuteResult sandBoxExecuteResult = sandboxPoolService.exeJavaCode(judgeSubmitDTO, execution);

        // 跨用例按顺序取首次失败：先前已确认的 WA 不被后面的资源超限覆盖。
        if (sandBoxExecuteResult != null && sandBoxExecuteResult.getOutputList() != null) {
            List<String> completed = sandBoxExecuteResult.getOutputList();
            for (int index = 0; index < completed.size(); index++) {
                if (!JudgeOutputNormalizer.normalize(completed.get(index))
                        .equals(JudgeOutputNormalizer.normalize(judgeSubmitDTO.getOutputList().get(index)))) {
                    UserQuestionResultVO wrongAnswer = new UserQuestionResultVO();
                    wrongAnswer.setStageEvidence(sandBoxExecuteResult.getStageEvidence());
                    wrongAnswer.setPass(Constants.FALSE);
                    wrongAnswer.setScore(JudgeConstants.ERROR_SCORE);
                    wrongAnswer.setVerdict("WA");
                    wrongAnswer.setExeMessage(CodeRunStatus.NOT_ALL_PASSED.getMsg());
                    return wrongAnswer;
                }
            }
        }

        UserQuestionResultVO userQuestionResultVO = new UserQuestionResultVO();
        if (sandBoxExecuteResult != null) {
            userQuestionResultVO.setStageEvidence(sandBoxExecuteResult.getStageEvidence());
        }
        // 判断运行结果
        if (sandBoxExecuteResult != null && CodeRunStatus.SUCCEED.equals(sandBoxExecuteResult.getRunStatus())) {
            // 说明此时代码运行成功，并没有其他问题 接下来进行代码实际结果和预期结果的比对  是否超出时间限制和空间限制的比对
            userQuestionResultVO = doJudge(judgeSubmitDTO, sandBoxExecuteResult, userQuestionResultVO);
        } else {
            // 此时代码运行失败(代码有异常)，需要把信息返回  并需要把属性赋值给UserQuestionResultVO
            userQuestionResultVO.setPass(Constants.FALSE);
            if (sandBoxExecuteResult != null) {
                userQuestionResultVO.setExeMessage(sandBoxExecuteResult.getExeMessage());
            } else {
                userQuestionResultVO.setExeMessage(CodeRunStatus.UNKNOWN_FAILED.getMsg());
            }
            userQuestionResultVO.setScore(JudgeConstants.ERROR_SCORE);
        }
        // 无可靠执行结果时交给任务层重试，不能误记为用户代码 RE 并永久终结。
        if (sandBoxExecuteResult == null || CodeRunStatus.UNKNOWN_FAILED.equals(sandBoxExecuteResult.getRunStatus())) {
            throw new IllegalStateException("沙箱未生成判题结果");
        }
        // 按顺序映射最终结果：全部检查通过为 AC，编译失败为 CE，之后识别时间/内存/输出超限，
        // 其余执行失败为 RE，正常执行但答案不符为 WA；命中前一分支后不再进入后续分类。
        // TLE/MLE 的两个 OR 条件分别兼容“沙箱直接判定”和“运行成功后比较指标发现超限”两条路径。
        if (Constants.TRUE.equals(userQuestionResultVO.getPass())) {
            userQuestionResultVO.setVerdict("AC");
        } else if (CodeRunStatus.COMPILE_FAILED.equals(sandBoxExecuteResult.getRunStatus())) {
            userQuestionResultVO.setVerdict("CE");
        } else if (CodeRunStatus.OUT_OF_TIME.equals(sandBoxExecuteResult.getRunStatus())
                || CodeRunStatus.OUT_OF_TIME.getMsg().equals(userQuestionResultVO.getExeMessage())) {
            userQuestionResultVO.setVerdict("TLE");
        } else if (CodeRunStatus.OUT_OF_MEMORY.equals(sandBoxExecuteResult.getRunStatus())
                || CodeRunStatus.OUT_OF_MEMORY.getMsg().equals(userQuestionResultVO.getExeMessage())) {
            userQuestionResultVO.setVerdict("MLE");
        } else if (CodeRunStatus.OUTPUT_LIMIT.equals(sandBoxExecuteResult.getRunStatus())) {
            userQuestionResultVO.setVerdict("OLE");
        } else if (!CodeRunStatus.SUCCEED.equals(sandBoxExecuteResult.getRunStatus())) {
            userQuestionResultVO.setVerdict("RE");
        } else {
            userQuestionResultVO.setVerdict("WA");
        }
        return userQuestionResultVO;
    }

    /** 先检查实际输出数量，再逐例比较期望答案，最后组装通过标志、得分和诊断。 */
    private static UserQuestionResultVO doJudge(
            JudgeSubmitDTO judgeSubmitDTO,
            SandBoxExecuteResult sandBoxExecuteResult,
            UserQuestionResultVO userQuestionResultVO) {
        // 先判断代码运行的结果是否符合预期结果
        List<String> exeOutputList = sandBoxExecuteResult.getOutputList();
        List<String> outputList = judgeSubmitDTO.getOutputList();
        if (exeOutputList.size() != outputList.size()) {
            // 说明实际运行的代码结果和预期运行的代码结果不同,   对分数 是否通过 异常情况进行设置
            userQuestionResultVO.setScore(JudgeConstants.ERROR_SCORE);
            userQuestionResultVO.setPass(Constants.FALSE);
            userQuestionResultVO.setExeMessage(CodeRunStatus.NOT_ALL_PASSED.getMsg());
            return userQuestionResultVO;
        }
        // 数量符合预期的代码数量，此时就需要判断代码每个实际输出的结果和预期的结果是否相同
        // 并且还需要劲这个UserExeResult的列表记录下来，如果实际结果出现问题还能够给用户进行提示效果
        List<UserExeResult> userExeResultList = new ArrayList<>();
        boolean pass = resultCompare(judgeSubmitDTO, exeOutputList, outputList, userExeResultList);
        return assembleUserQuestionResultVO(
                judgeSubmitDTO,
                sandBoxExecuteResult,
                userQuestionResultVO,
                userExeResultList,
                pass);
    }

    /** 保存内部用例诊断；答案或时间检查失败时记零分，全部通过时按难度计算得分。 */
    private static UserQuestionResultVO assembleUserQuestionResultVO(
            JudgeSubmitDTO judgeSubmitDTO,
            SandBoxExecuteResult sandBoxExecuteResult,
            UserQuestionResultVO userQuestionResultVO,
            List<UserExeResult> userExeResultList,
            boolean pass) {
        // 保存内部用例诊断；公开查询由 Friend 去除该字段，避免泄露隐藏用例。
        userQuestionResultVO.setUserExeResultList(userExeResultList);
        // 判断此时pass是否为false，为false则说明实际输出结果和预期输出的结果不同
        if (!pass) {
            userQuestionResultVO.setScore(JudgeConstants.ERROR_SCORE);
            userQuestionResultVO.setPass(Constants.FALSE);
            userQuestionResultVO.setExeMessage(CodeRunStatus.NOT_ALL_PASSED.getMsg());
            return userQuestionResultVO;
        }
        // 此时通过了结果的比对，还需要判断此时是否超出空间限制和时间限制
        // 峰值仅用于观测；MLE 必须来自当前任务组的可靠 OOM 证据，不能凭接近或超过限额猜测。
        if (sandBoxExecuteResult.getUseTime() > judgeSubmitDTO.getTimeLimit()) {
            userQuestionResultVO.setScore(JudgeConstants.ERROR_SCORE);
            userQuestionResultVO.setPass(Constants.FALSE);
            userQuestionResultVO.setExeMessage(CodeRunStatus.OUT_OF_TIME.getMsg());
            return userQuestionResultVO;
        }

        // 到这里就说明，代码运行全部通过了
        userQuestionResultVO.setPass(Constants.TRUE);
        // 分数是通过困难度和基础分数来计算出来的
        int score = judgeSubmitDTO.getDifficulty() * JudgeConstants.DEFAULT_SCORE;
        userQuestionResultVO.setScore(score);
        return userQuestionResultVO;
    }

    /** 按固定用例顺序保存输入及输出诊断，并使用统一换行规则比较答案；任意不符则返回未通过。 */
    private static boolean resultCompare(
            JudgeSubmitDTO judgeSubmitDTO,
            List<String> exeOutputList,
            List<String> outputList,
            List<UserExeResult> userExeResultList) {
        boolean pass = true;
        for (int index = 0; index < exeOutputList.size(); index++) {
            // 实际输出结果
            String exeOutput = exeOutputList.get(index);
            // 预期输出结果
            String output = outputList.get(index);
            // 输入
            String input = judgeSubmitDTO.getInputList().get(index);
            // 将上述的信息加入到UserExeResult
            UserExeResult userExeResult = new UserExeResult();
            userExeResult.setExeOutput(exeOutput);
            userExeResult.setInput(input);
            userExeResult.setOutput(output);
            userExeResultList.add(userExeResult);
            // 此处进行判断是否相同(预期结果和实际结果)
            if (!JudgeOutputNormalizer.normalize(output).equals(JudgeOutputNormalizer.normalize(exeOutput))) {
                pass = false;
            }
        }
        return pass;
    }
}
