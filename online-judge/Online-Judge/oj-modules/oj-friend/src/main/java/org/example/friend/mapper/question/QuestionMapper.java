package org.example.friend.mapper.question;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.friend.domain.question.Question;


@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

}