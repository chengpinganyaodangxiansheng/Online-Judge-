package org.example.system.controller.user;


import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.common.core.controller.BaseController;
import org.example.common.core.domain.Result;
import org.example.common.core.domain.TableDataInfo;
import org.example.system.domain.user.dto.UserDTO;
import org.example.system.domain.user.dto.UserQueryDTO;
import org.example.system.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Tag(name = "用户相关接口")
public class UserController extends BaseController {
    @Autowired
    private UserService userService;

    /**
     * 展示用户列表页面
     * @param userQueryDTO
     * @return
     */
    @GetMapping("/list")
    public TableDataInfo list(UserQueryDTO userQueryDTO) {
        return getDataTable(userService.list(userQueryDTO));
    }

    /**
     * 修改用户的状态(拉黑用户)
     * @param userDTO
     * @return
     */
    @PutMapping("/updateStatus")
    public Result updateStatus(@RequestBody UserDTO userDTO) {
        return toResult(userService.updateStatus(userDTO));
    }
}
