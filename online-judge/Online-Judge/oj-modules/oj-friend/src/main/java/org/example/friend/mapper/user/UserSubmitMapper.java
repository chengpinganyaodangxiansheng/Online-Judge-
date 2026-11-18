package org.example.friend.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.friend.domain.user.UserSubmit;

import java.util.List;


@Mapper
public interface UserSubmitMapper extends BaseMapper<UserSubmit> {
    UserSubmit selectCurrentUserSubmit(Long userId, Long examId, Long questionId, String currentTime);

    List<Long> selectHostQuestionList();

}