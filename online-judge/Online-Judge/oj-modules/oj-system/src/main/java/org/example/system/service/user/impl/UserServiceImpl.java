package org.example.system.service.user.impl;

import com.github.pagehelper.PageHelper;
import org.example.common.core.enums.ResultCode;
import org.example.common.security.exception.ServiceException;
import org.example.system.domain.user.User;
import org.example.system.domain.user.dto.UserDTO;
import org.example.system.domain.user.dto.UserQueryDTO;
import org.example.system.domain.user.vo.UserVO;
import org.example.system.manager.UserCacheManager;
import org.example.system.mapper.user.UserMapper;
import org.example.system.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserCacheManager userCacheManager;

    /**
     * 查询用户列表页面，返回给前端用户列表数据
     * @param userQueryDTO
     * @return
     */
    public List<UserVO> list(UserQueryDTO userQueryDTO) {
        //先进行分页操作
        PageHelper.startPage(userQueryDTO.getPageNum(), userQueryDTO.getPageSize());

        return userMapper.selectUserList(userQueryDTO);
    }

    @Override
    public int updateStatus(UserDTO userDTO) {
        //判断用户是否存在
        User user = userMapper.selectById(userDTO.getUserId());
        if (user == null) {
            throw new ServiceException(ResultCode.FAILED_USER_NOT_EXISTS);
        }

        //修改用户状态
        user.setStatus(userDTO.getStatus());
        //改变缓存
        userCacheManager.updateStatus(user.getUserId(),userDTO.getStatus());
        return userMapper.updateById(user);
    }
}
