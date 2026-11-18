package org.example.system.service.system.impl;


import cn.hutool.core.collection.CollectionUtil;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.common.core.constants.HttpConstants;
import org.example.common.core.domain.LoginUser;
import org.example.common.core.domain.Result;
import org.example.common.core.domain.vo.LoginUserVO;
import org.example.common.core.enums.ResultCode;

import org.example.common.core.enums.UserIdentity;
import org.example.common.security.exception.ServiceException;
import org.example.common.security.service.TokenService;
import org.example.system.domain.sysuser.dto.LoginDTO;
import org.example.system.domain.sysuser.SysUser;
import org.example.system.domain.sysuser.dto.SysUserSaveDTO;
import org.example.system.mapper.system.SysUserMapper;
import org.example.system.service.system.SysUserService;
import org.example.system.utils.BCryptUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;


@Service
public class SysUserServiceImpl implements SysUserService {
    @Autowired
    private SysUserMapper sysUserMapper;

    @Value("${jwt.secret}")
    private String secret;

    @Autowired
    private TokenService tokenService;

    @Override
    public Result<String> login(LoginDTO loginDTO) {
        String username = loginDTO.getUserAccount();
        String password = loginDTO.getPassword();
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(SysUser::getPassword,SysUser::getUserId,SysUser::getNickName)
                .eq(SysUser::getUserAccount,username);
        SysUser sysUser = sysUserMapper.selectOne(wrapper);

        //Result result = new Result();
        if (sysUser == null) {
            /*result.setCode(ResultCode.FAILED_USER_NOT_EXISTS.getCode());
            result.setMsg(ResultCode.FAILED_USER_NOT_EXISTS.getMsg());*/
            return Result.fail(ResultCode.FAILED_USER_NOT_EXISTS);
        }

        //用户输入的密码和加密后使用的密码使用加密工具中的比对方法来判断
        if (BCryptUtils.matchesPassword(password, sysUser.getPassword())) {
            /*result.setCode(ResultCode.SUCCESS.getCode());
            result.setMsg(ResultCode.SUCCESS.getMsg());*/

            //再使用Redis来保存用户的敏感信息
            return Result.success(tokenService.createToken(sysUser.getUserId(),
                    secret, UserIdentity.ADMIN.getValue(), sysUser.getNickName(),null));
        }

        /*result.setCode(ResultCode.FAILED_LOGIN.getCode());
        result.setMsg(ResultCode.FAILED_LOGIN.getMsg());*/

        return Result.fail(ResultCode.FAILED_LOGIN);
    }

    @Override
    public int add(SysUserSaveDTO sysUserSaveDTO) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("user_account",sysUserSaveDTO.getUserAccount());
        List<SysUser> sysUserList = sysUserMapper.selectList(wrapper);
        if (CollectionUtil.isNotEmpty(sysUserList)) {
            //用户已存在
            throw new ServiceException(ResultCode.AILED_USER_EXISTS);
        }

        SysUser sysUser = new SysUser();
        sysUser.setUserAccount(sysUserSaveDTO.getUserAccount());
        sysUser.setPassword(BCryptUtils.encryptPassword(sysUserSaveDTO.getPassword()));
        return sysUserMapper.insert(sysUser);
    }

    @Override
    public Result<LoginUserVO> info(String token) {
        // 如果前端设置了令牌前缀，则裁剪掉前缀
        if (StrUtil.isNotEmpty(token) && token.startsWith(HttpConstants.PREFIX)) {
            token = token.replaceFirst(HttpConstants.PREFIX, StrUtil.EMPTY);
        }

        LoginUser loginUser = tokenService.getLoginUser(token,secret);
        if (loginUser == null) {
            return Result.fail();
        }

        LoginUserVO loginUserVO = new LoginUserVO();
        loginUserVO.setNickName(loginUser.getNickName());
        return Result.success(loginUserVO);
    }

    @Override
    public Result logout(String token) {
        // 如果前端设置了令牌前缀，则裁剪掉前缀
        if (StrUtil.isNotEmpty(token) && token.startsWith(HttpConstants.PREFIX)) {
            token = token.replaceFirst(HttpConstants.PREFIX, StrUtil.EMPTY);
        }

        return tokenService.deleteLoginUser(token,secret) ? Result.success() : Result.fail();
    }
}
