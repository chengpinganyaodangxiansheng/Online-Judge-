package org.example.friend.mapper.exam;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.friend.domain.exam.ExamQuestion;

@Mapper
public interface ExamQuestionMapper extends BaseMapper<ExamQuestion> {
}
