package org.example.system.controller.question;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.common.core.controller.BaseController;
import org.example.common.core.domain.Result;
import org.example.common.core.domain.TableDataInfo;
import org.example.system.domain.question.dto.QuestionAddDTO;
import org.example.system.domain.question.dto.QuestionEditDTO;
import org.example.system.domain.question.dto.QuestionQueryDTO;
import org.example.system.domain.question.vo.QuestionDetailVO;
import org.example.system.service.question.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/question")
@Tag(name = "题目管理接口")
public class QuestionController extends BaseController {
    @Autowired
    private QuestionService questionService;

    /**
     * 题目列表
     * @param questionQueryDTO
     * @return
     */
    @GetMapping("/list")
    public TableDataInfo list(QuestionQueryDTO questionQueryDTO) {
        return getDataTable(questionService.list(questionQueryDTO));
    }

    /**
     * 添加题目
     * @param questionAddDTO
     * @return
     */
    @PostMapping("/add")
    public Result add(@RequestBody QuestionAddDTO questionAddDTO) {
        return toResult(questionService.add(questionAddDTO));
    }

    /**
     * 题目详情
     * @param questionId
     * @return
     */
    @GetMapping("/detail")
    public Result<QuestionDetailVO> detail(Long questionId) {
        return questionService.detail(questionId);
    }

    /**
     * 编辑题目
     * @param questionEditDTO
     * @return
     */
    @PutMapping("/edit")
    public Result edit(@RequestBody QuestionEditDTO questionEditDTO) {
        return toResult(questionService.edit(questionEditDTO));
    }

    /**
     * 删除题目
     * @param questionId
     * @return
     */
    @DeleteMapping("/delete")
    public Result delete(Long questionId) {
        return toResult(questionService.delete(questionId));
    }

}
