package org.example.friend.service.question;

import org.example.common.core.domain.TableDataInfo;
import org.example.friend.domain.question.dto.QuestionQueryDTO;
import org.example.friend.domain.question.vo.QuestionDetailVO;
import org.example.friend.domain.question.vo.QuestionVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface QuestionService {
    /** 按查询条件分页获取公开题目列表，返回列表内容与总条数。 */
    TableDataInfo list(QuestionQueryDTO queryDTO);

    /** 读取公开题目详情；带比赛编号时由实现类读取冻结题目并校验报名资格。 */
    QuestionDetailVO detail(Long questionId);

    /** examId 为空读取当前题目，否则读取该比赛冻结的公开题目详情。 */
    QuestionDetailVO detail(Long questionId, Long examId);

    /** 返回当前题目的上一题编号，供答题页导航使用。 */
    String preQuestion(Long questionId);

    /** 返回当前题目的下一题编号，供答题页导航使用。 */
    String nextQuestion(Long questionId);

    /** 返回按提交热度组织的公开题目列表。 */
    List<QuestionVO> hotList();
}
