package org.example.friend.controller.user;


import org.example.common.core.constants.HttpConstants;
import org.example.common.core.controller.BaseController;
import org.example.common.core.domain.Result;
import org.example.common.core.domain.TableDataInfo;
import org.example.friend.aspect.CheckUserStatus;
import org.example.friend.domain.exam.dto.ExamDTO;
import org.example.friend.domain.exam.dto.ExamQueryDTO;
import org.example.friend.service.user.UserExamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/exam")
public class UserExamController extends BaseController {
    @Autowired
    private UserExamService userExamService;


    /**
     * 用户报名竞赛
     * @param token
     * @param examDTO
     * @return
     */
    @CheckUserStatus
    @PostMapping("/enter")
    public Result enter(@RequestHeader(HttpConstants.AUTHENTICATION) String token, @RequestBody ExamDTO examDTO) {
        return toResult(userExamService.enter(token,examDTO.getExamId()));
    }

    /**
     * 用户个人竞赛列表
     * @param examQueryDTO
     * @return
     */
    @GetMapping("/list")
    public TableDataInfo list(ExamQueryDTO examQueryDTO) {
        return  userExamService.list(examQueryDTO);
    }
}
