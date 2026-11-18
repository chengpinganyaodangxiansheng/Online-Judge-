package org.example.friend.controller.user;


import org.example.common.core.constants.HttpConstants;
import org.example.common.core.controller.BaseController;
import org.example.common.core.domain.Result;
import org.example.common.core.domain.vo.LoginUserVO;
import org.example.friend.domain.user.dto.UserDTO;
import org.example.friend.domain.user.dto.UserUpdateDTO;
import org.example.friend.domain.user.vo.UserVO;
import org.example.friend.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController extends BaseController {
    @Autowired
    private UserService userService;

    /**
     * 获取验证码
     * @param userDTO
     * @return
     */
    @PostMapping("sendCode")
    public Result sendCode(@RequestBody UserDTO userDTO) {
        return toResult(userService.sendCode(userDTO));
    }

    /**
     * 用户登录
     * @param userDTO
     * @return
     */
    @PostMapping("/code/login")
    public Result<String> codeLogin(@RequestBody UserDTO userDTO) {
        return Result.success(userService.codeLogin(userDTO));
    }

    /**
     * 用户退出登录
     * @param token
     * @return
     */
    @DeleteMapping("/logout")
    public Result logout(@RequestHeader(HttpConstants.AUTHENTICATION) String token) {
        return userService.logout(token);
    }

    /**
     * 登录成功后获取用户昵称和头像
     * @param token
     * @return
     */
    @RequestMapping("/info")
    public Result<LoginUserVO> info(@RequestHeader(HttpConstants.AUTHENTICATION) String token) {
        return userService.info(token);
    }

    /**
     * 用户在个人中心时获取用户的详情
     * @return
     */
    @GetMapping("/detail")
    public Result<UserVO> detail() {
        return Result.success(userService.detail());
    }


    /**
     * 用户在个人中心修改用户信息
     * @param userUpdateDTO
     * @return
     */
    @PutMapping("/edit")
    public Result<Void> edit(@RequestBody UserUpdateDTO userUpdateDTO) {
        return toResult(userService.edit(userUpdateDTO));
    }

    /**
     *上传头像
     * @param userUpdateDTO
     * @return
     */
    @PutMapping("/head-image/update")
    public Result updateHeadImage(@RequestBody UserUpdateDTO userUpdateDTO) {
        return toResult(userService.updateHeadImage(userUpdateDTO.getHeadImage()));
    }


}
