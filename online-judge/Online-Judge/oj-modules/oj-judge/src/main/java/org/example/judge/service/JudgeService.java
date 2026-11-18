package org.example.judge.service;

import org.example.api.domain.dto.JudgeSubmitDTO;
import org.example.api.domain.vo.UserQuestionResultVO;
import org.springframework.stereotype.Service;

/** 执行并判定用户代码的业务接口，不承担任务状态或历史成绩落库。 */
@Service
public interface JudgeService {
    /** 直接调用入口，内部有期限地申请容器并在结束后退役；返回答案比较后的业务结果，但不负责提交任务终态或比赛成绩。 */
    UserQuestionResultVO doJudgeJavaCode(JudgeSubmitDTO judgeSubmitDTO);

    /** 可靠消费者传入已领取容器的执行上下文和固定快照；可确认的用户错误返回判定，执行证据不确定时抛出异常交由任务层安排恢复。 */
    UserQuestionResultVO doJudgeJavaCode(JudgeSubmitDTO judgeSubmitDTO, org.example.judge.domain.JudgeExecution execution);
}
