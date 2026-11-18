package org.example.friend.mapper.message;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.friend.domain.message.MessageText;
import org.example.friend.domain.message.vo.MessageTextVO;

import java.util.List;

@Mapper
public interface MessageTextMapper extends BaseMapper<MessageText> {

    List<MessageTextVO> selectUserMsgList(Long userId);

}