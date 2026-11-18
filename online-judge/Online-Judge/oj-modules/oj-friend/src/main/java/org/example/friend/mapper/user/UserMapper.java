package org.example.friend.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.friend.domain.user.User;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
