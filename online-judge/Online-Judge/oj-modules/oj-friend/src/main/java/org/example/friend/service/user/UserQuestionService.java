package org.example.friend.service.user;

import org.example.api.domain.vo.UserQuestionResultVO;
import org.example.common.core.domain.Result;
import org.example.friend.domain.user.dto.UserSubmitDTO;
import org.springframework.stereotype.Service;

/** 旧结果查询兼容接口，新提交与任务查询由 JudgeSubmissionService 负责。 */
@Service
public interface UserQuestionService {
    /** 按比赛、题目和提交时间查询兼容的历史执行结果，不用于替代可靠任务的状态查询。 */
    UserQuestionResultVO exeResult(Long examId, Long questionId, String currentTime);
}
