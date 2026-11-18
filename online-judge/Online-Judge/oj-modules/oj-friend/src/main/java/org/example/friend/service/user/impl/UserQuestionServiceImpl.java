package org.example.friend.service.user.impl;

import org.example.api.domain.vo.UserQuestionResultVO;
import org.example.common.core.constants.Constants;
import org.example.common.core.enums.QuestionResType;
import org.example.common.core.enums.ResultCode;
import org.example.common.core.utils.ThreadLocalUtil;
import org.example.common.security.exception.ServiceException;
import org.example.friend.domain.user.UserSubmit;
import org.example.friend.mapper.user.UserSubmitMapper;
import org.example.friend.service.user.UserQuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 仅保留旧客户端的历史结果查询；不再通过同步 Feign 执行代码。 */
@Service
public class UserQuestionServiceImpl implements UserQuestionService {
    /** 用户提交历史访问对象，读取历史判题记录及热门题目统计。 */
    @Autowired
    private UserSubmitMapper userSubmitMapper;

    // 保留旧客户端查询兼容；新客户端按submitId查询可靠任务状态。
    @Override
    public UserQuestionResultVO exeResult(Long examId, Long questionId, String currentTime) {
        // 身份取自认证上下文，限制只能查询本人的历史记录，不能接受外部随意指定用户编号。
        Long userId = ThreadLocalUtil.get(Constants.USER_ID, Long.class);
        if (userId == null) {
            throw new ServiceException(ResultCode.FAILED_UNAUTHORIZED);
        }
        if (questionId == null) {
            throw new ServiceException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        // 旧接口按用户、比赛、题目和时间寻找记录，找不到沿用“判题中”提示。
        // 新的可靠任务接口按提交编号查状态，不依赖此处的历史查询方式。
        UserSubmit submission = userSubmitMapper.selectCurrentUserSubmit(userId, examId, questionId, currentTime);
        UserQuestionResultVO result = new UserQuestionResultVO();
        result.setPass(submission == null ? QuestionResType.IN_JUDGE.getValue() : submission.getPass());
        if (submission != null) {
            result.setExeMessage(submission.getExeMessage());
        }
        return result;
    }
}
