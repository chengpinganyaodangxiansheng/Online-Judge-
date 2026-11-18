package org.example.friend.controller.exam;

import org.example.common.core.controller.BaseController;
import org.example.common.core.domain.Result;
import org.example.common.core.domain.TableDataInfo;
import org.example.friend.domain.exam.dto.ExamQueryDTO;
import org.example.friend.domain.exam.dto.ExamRankDTO;
import org.example.friend.service.exam.ExamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/exam")
public class ExamController extends BaseController {

    @Autowired
    private ExamService examService;

    /**
     * 获取竞赛列表,用于展示有多少条竞赛
     * @param examQueryDTO
     * @return
     */
    @GetMapping("/semiLogin/list")
    public TableDataInfo list(ExamQueryDTO examQueryDTO) {
        return getDataTable(examService.list(examQueryDTO));
    }

    /**
     * 竞赛列表使用redis缓存实现
     * @param examQueryDTO
     * @return
     */
    @GetMapping("/semiLogin/redis/list")
    public TableDataInfo redisList(ExamQueryDTO examQueryDTO) {
        return  examService.redisList(examQueryDTO);
    }

    /**
     * 在历史竞赛中查询竞赛排名
     * @param examRankDTO
     * @return
     */
    @GetMapping("/rank/list")
    public TableDataInfo rankList(ExamRankDTO examRankDTO) {
        return examService.rankList(examRankDTO);
    }


    /**
     * 从竞赛列表中答题
     * @param examId
     * @return
     */
    @GetMapping("/getFirstQuestion")
    public Result<String> getFirstQuestion(Long examId) {
        // 代码逻辑： 获取竞赛中题目的顺序列表   先从redis  redis中没有数据查询数据库  list  数据类型  key: e:q:l:examId   value : questionId
        // 排在第一个的题目  返回给前端
        return Result.success(examService.getFirstQuestion(examId));
    }

    /**
     * 当前竞赛的题目列表的上一道题
     * @param examId
     * @param questionId
     * @return
     */
    @GetMapping("/preQuestion")
    public Result<String> preQuestion(Long examId, Long questionId) {
        return Result.success(examService.preQuestion(examId, questionId));
    }

    /**
     * 当前竞赛的题目列表的下一道题
     * @param examId
     * @param questionId
     * @return
     */
    @GetMapping("/nextQuestion")
    public Result<String> nextQuestion(Long examId, Long questionId) {
        return Result.success(examService.nextQuestion(examId, questionId));
    }

}