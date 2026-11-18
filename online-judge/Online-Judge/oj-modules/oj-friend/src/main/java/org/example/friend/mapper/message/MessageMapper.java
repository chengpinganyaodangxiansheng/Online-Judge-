package org.example.friend.mapper.message;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.friend.domain.message.Message;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

}