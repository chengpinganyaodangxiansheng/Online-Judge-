package org.example.system.controller.exam;


import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.common.core.controller.BaseController;
import org.example.common.core.domain.Result;
import org.example.common.core.domain.TableDataInfo;
import org.example.system.domain.exam.dto.ExamAddDTO;
import org.example.system.domain.exam.dto.ExamEditDTO;
import org.example.system.domain.exam.dto.ExamQueryDTO;
import org.example.system.domain.exam.dto.ExamQuestAddDTO;
import org.example.system.domain.exam.vo.ExamDetailVO;
import org.example.system.service.exam.ExamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exam")
@Tag(name = "竞赛功能相关接口")
public class ExamController extends BaseController {
    @Autowired
    private ExamService examService;

    /**
     * 获取竞赛列表,用于展示有多少条竞赛
     * @param examQueryDTO
     * @return
     */
    @GetMapping("/list")
    public TableDataInfo list(ExamQueryDTO examQueryDTO) {
        return getDataTable(examService.list(examQueryDTO));
    }

    /**
     * 添加竞赛
     * @param examAddDTO
     * @return
     */
    @PostMapping("/add")
    public Result<String> add(@RequestBody ExamAddDTO examAddDTO) {
        return Result.success(examService.add(examAddDTO));
    }

    /**
     * 对已经存在的竞赛添加家题目
     * @param examQuestAddDTO
     * @return
     */
    @PostMapping("/question/add")
    public Result addQuestion(@RequestBody ExamQuestAddDTO examQuestAddDTO) {
        return toResult(examService.addQuestion(examQuestAddDTO));
    }

    /**
     * 在已有的竞赛中删除已经添加了的题目
     * @param examId
     * @param questionId
     * @return
     */
    @DeleteMapping("/question/delete")
    public Result deleteQuestion(Long examId, Long questionId) {
        return toResult(examService.deleteQuestion(examId,questionId));
    }

    /**
     * 获取指定的竞赛详情信息
     * @param examId
     * @return
     */
    @GetMapping("/detail")
    public Result<ExamDetailVO> detail(Long examId) {
        return Result.success(examService.detail(examId));
    }

    /**
     * 编辑竞赛
     * @param examEditDTO
     * @return
     */
    @PutMapping("/edit")
    public Result edit(@RequestBody ExamEditDTO examEditDTO) {
        return toResult(examService.edit(examEditDTO));
    }

    /**
     * 删除竞赛
     * @param examId
     * @return
     */
    @DeleteMapping("/delete")
    public Result delete(Long examId) {
        return toResult(examService.delete(examId));
    }

    /**
     * 发布竞赛(无论是编辑页面，竞赛列表页面，还是添加竞赛页面都行)
     * @param examId
     * @return
     */
    @PutMapping("/publish")
    public Result publish(Long examId) {
        return toResult(examService.publish(examId));
    }

    /**
     * 撤销发布
     * @param examId
     * @return
     */
    @PutMapping("/cancelPublish")
    public Result cancelPublish(Long examId) {
        return toResult(examService.cancelPublish(examId));
    }

}
