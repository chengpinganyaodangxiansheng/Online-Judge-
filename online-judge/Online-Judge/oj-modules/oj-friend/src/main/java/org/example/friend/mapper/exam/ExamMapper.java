package org.example.friend.mapper.exam;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.friend.domain.exam.Exam;
import org.example.friend.domain.exam.dto.ExamQueryDTO;
import org.example.friend.domain.exam.vo.ExamVO;


import java.util.List;

@Mapper
public interface ExamMapper extends BaseMapper<Exam> {

    List<ExamVO> selectExamList(ExamQueryDTO examQueryDTO);
}
