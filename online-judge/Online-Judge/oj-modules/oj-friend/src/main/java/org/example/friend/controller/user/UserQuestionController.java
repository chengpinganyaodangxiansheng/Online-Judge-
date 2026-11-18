package org.example.friend.controller.user;

import org.example.api.domain.vo.UserQuestionResultVO;
import org.example.api.domain.vo.JudgeTaskVO;
import org.example.friend.service.user.JudgeSubmissionService;
import org.example.friend.domain.user.dto.JudgeRetryDTO;
import org.example.friend.domain.user.vo.JudgeDeliveryVO;
import java.util.List;
import org.example.common.core.controller.BaseController;
import org.example.common.core.domain.Result;

import org.example.friend.domain.user.dto.UserSubmitDTO;
import org.example.friend.service.user.UserQuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/user/question")
@RestController
public class UserQuestionController extends BaseController {
    /** 可靠提交业务服务，负责幂等受理、进度查询和授权人工重试。 */
    @Autowired private JudgeSubmissionService judgeSubmissionService;

    /** 用户判题历史查询服务，提供兼容的历史执行结果入口。 */
    @Autowired private UserQuestionService userQuestionService;

    /**
     * 受理代码提交，真正的编译执行由 Judge 异步完成。
     * @param submitDTO 含幂等键的原始提交
     * @return 任务编号和当前状态，不是同步判题结果
     */
    @PostMapping("/submit")
    public Result<JudgeTaskVO> submit(@RequestBody UserSubmitDTO submitDTO) {
        return Result.success(judgeSubmissionService.submit(submitDTO));
    }

    /**
     * 兼容原 RabbitMQ 提交路径，与 /submit 使用相同的持久化受理流程。
     * @param submitDTO 含幂等键的原始提交
     * @return 可供轮询的任务视图
     */
    @PostMapping("/rabbit/submit")
    public Result<JudgeTaskVO> rabbitSubmit(@RequestBody UserSubmitDTO submitDTO) {
        return Result.success(judgeSubmissionService.submit(submitDTO));
    }

    /** 查询本人提交；服务层过滤隐藏用例，客户端遇到终态停止轮询。 */
    @GetMapping("/submissions/{submitId}")
    public Result<JudgeTaskVO> task(@PathVariable Long submitId) {
        return Result.success(judgeSubmissionService.find(submitId));
    }

    /** 管理员人工重试入口；权限、终态与比赛定榜检查均在业务层执行。 */
    @PostMapping("/submissions/{submitId}/retry")
    public Result<JudgeTaskVO> retry(@PathVariable Long submitId, @RequestBody JudgeRetryDTO request) {
        return Result.success(judgeSubmissionService.retry(submitId, request.getReason()));
    }

    /** 管理员查看系统失败任务，业务层限制单次查询数量。 */
    @GetMapping("/submissions/failed")
    public Result<List<JudgeTaskVO>> failed(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(judgeSubmissionService.failed(limit));
    }

    /** 管理员检查某次提交的发送代次及失败原因。 */
    @GetMapping("/submissions/{submitId}/deliveries")
    public Result<List<JudgeDeliveryVO>> deliveries(@PathVariable Long submitId) {
        return Result.success(judgeSubmissionService.deliveries(submitId));
    }

    /**
     * 旧客户端查询兼容入口；新客户端按 submitId 查询可靠任务状态。
     * @param examId
     * @param questionId
     * @param currentTime
     * @return
     */
    @GetMapping("/exe/result")
    public Result<UserQuestionResultVO> exeResult(Long examId, Long questionId, String currentTime) {
        return Result.success(userQuestionService.exeResult(examId, questionId, currentTime));
    }
}
