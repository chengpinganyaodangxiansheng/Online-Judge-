package org.example.judge.controller;

import groovy.util.logging.Slf4j;
import org.example.api.domain.dto.JudgeSubmitDTO;
import org.example.api.domain.vo.UserQuestionResultVO;
import org.example.common.core.controller.BaseController;
import org.example.common.core.domain.Result;
import org.example.judge.service.JudgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/judge")
@Slf4j
public class JudgeController extends BaseController {

    /** 判题业务服务，使用沙箱执行快照并将执行证据转换为业务结果。 */
    @Autowired private JudgeService judgeService;

    /**
     * 保留旧同步路径的明确迁移提示，避免绕过任务持久化和执行权检查直接运行代码。
     * @param judgeSubmitDTO 旧调用方提交的参数，此入口不再执行
     * @return 业务状态码 410 及新提交路径说明
     */
    @PostMapping("/doJudgeJavaCode")
    public Result<UserQuestionResultVO> doJudgeJavaCode(
            @RequestBody JudgeSubmitDTO judgeSubmitDTO) {
        return Result.fail(410, "同步判题入口已迁移，请通过 /user/question/submit 受理并使用 submitId 查询");
    }
}
