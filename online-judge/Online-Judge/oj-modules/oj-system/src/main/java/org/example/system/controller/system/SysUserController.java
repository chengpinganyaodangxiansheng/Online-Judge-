package org.example.system.controller.system;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.common.core.constants.HttpConstants;
import org.example.common.core.controller.BaseController;
import org.example.common.core.domain.Result;
import org.example.common.core.domain.vo.LoginUserVO;
import org.example.system.domain.sysuser.dto.LoginDTO;

import org.example.system.domain.sysuser.dto.SysUserSaveDTO;
import org.example.system.domain.sysuser.vo.SysUserVO;
import org.example.system.service.system.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sysUser")
@Tag(name = "管理员相关接口")
public class SysUserController extends BaseController {
    @Autowired
    private SysUserService sysUserService;

    @Operation(summary = "管理员登录")
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginDTO loginDTO) {
        return sysUserService.login(loginDTO);
    }


    @Operation(summary = "新增管理员", description = "根据提供的信息新增管理员⽤户")
    @PostMapping("/add")
    @ApiResponse(responseCode = "1000", description = "操作成功")
    @ApiResponse(responseCode = "2000", description = "服务繁忙请稍后重试")
    @ApiResponse(responseCode = "3101", description = "⽤户已存在")
    public Result add(@RequestBody SysUserSaveDTO sysUserSaveDTO) {
        return toResult(sysUserService.add(sysUserSaveDTO));
    }


    @DeleteMapping("/{userId}")
    @Operation(summary = "删除⽤户", description = "通过⽤户id删除⽤户")
    @Parameters(value = {
            @Parameter(name = "userId", in = ParameterIn.PATH, description = "⽤户ID")
    })
    @ApiResponse(responseCode = "1000", description = "成功删除⽤户")
    @ApiResponse(responseCode = "2000", description = "服务繁忙请稍后重试")
    @ApiResponse(responseCode = "3101", description = "⽤户不存在")
    public Result delete(@PathVariable Long userId) {
        return null;
    }


    @Operation(summary = "⽤户详情", description = "根据查询条件查询⽤户详情")
    @GetMapping("/detail")
    @Parameters(value = {
            @Parameter(name = "userId", in = ParameterIn.QUERY, description = "⽤户ID"),
            @Parameter(name = "sex", in = ParameterIn.QUERY, description = "⽤户性别")
    })
    @ApiResponse(responseCode = "1000", description = "成功获取⽤户信息")
    @ApiResponse(responseCode = "2000", description = "服务繁忙请稍后重试")
    @ApiResponse(responseCode = "3101", description = "⽤户不存在")
    public Result<SysUserVO> detail(@RequestParam(required = true) Long userId, @RequestParam(required = false) String sex) {
        return null;
    }


    @RequestMapping("/info")
    public Result<LoginUserVO> info(@RequestHeader(HttpConstants.AUTHENTICATION) String token) {
        return sysUserService.info(token);
    }

    @DeleteMapping("/logout")
    public Result logout(@RequestHeader(HttpConstants.AUTHENTICATION) String token) {
        return sysUserService.logout(token);
    }




}
