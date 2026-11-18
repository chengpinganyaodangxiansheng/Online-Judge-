package org.example.friend.controller.question;

import org.example.common.core.controller.BaseController;
import org.example.common.core.domain.Result;
import org.example.common.core.domain.TableDataInfo;
import org.example.friend.domain.question.dto.QuestionQueryDTO;
import org.example.friend.domain.question.vo.QuestionDetailVO;
import org.example.friend.domain.question.vo.QuestionVO;
import org.example.friend.service.question.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/question")
public class QuestionController extends BaseController {
    /** 题目业务服务，负责列表、详情及相邻题目查询。 */
    @Autowired private QuestionService questionService;

    /**
     * 前端用户的题目列表
     * @param queryDTO
     * @return
     */
    @GetMapping("semiLogin/list")
    public TableDataInfo list(QuestionQueryDTO queryDTO) {
        return questionService.list(queryDTO);
    }

    /**
     * 前端用户页面获取题目详情信息(答题时)
     * @param questionId
     * @return
     */
    // 比赛详情需携带 examId，保证展示规则与后续提交采用同一冻结版本。
    @GetMapping("/detail")
    public Result<QuestionDetailVO> detail(Long questionId, Long examId) {
        return Result.success(questionService.detail(questionId, examId));
    }

    /**
     * 在用户答题界面点击上一题时的跳转
     * 方法是使用redis作为缓存，使用q:l作为key，使用list作为value，将每个题目的id保存到list集合中
     * 使用String来接收questionId，是因为questionId是使用的雪花算法生成的questionId，返回给前端可能会发生截断，因为生成的id会比较长
     * 所以在QuestionVO中的questionId使用了@JsonSerialize(using = ToStringSerializer.class)来变成字符串进行处理
     * @param questionId
     * @return
     */
    @GetMapping("/preQuestion")
    public Result<String> preQuestion(Long questionId) {
        return Result.success(questionService.preQuestion(questionId));
    }

    /**
     * 在用户答题界面点击下一题时的跳转
     * @param questionId
     * @return
     */
    @GetMapping("/nextQuestion")
    public Result<String> nextQuestion(Long questionId) {
        return Result.success(questionService.nextQuestion(questionId));
    }

    /** 调用题目服务取得热门题目列表，再包装为统一接口响应。 */
    @GetMapping("/semiLogin/hotList")
    public Result<List<QuestionVO>> hotList() {
        return Result.success(questionService.hotList());
    }
}
