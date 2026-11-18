package org.example.system.service.exam;

import org.example.common.core.domain.Result;
import org.example.common.core.domain.TableDataInfo;
import org.example.system.domain.exam.ExamQuestion;
import org.example.system.domain.exam.dto.ExamAddDTO;
import org.example.system.domain.exam.dto.ExamEditDTO;
import org.example.system.domain.exam.dto.ExamQueryDTO;
import org.example.system.domain.exam.dto.ExamQuestAddDTO;
import org.example.system.domain.exam.vo.ExamDetailVO;
import org.example.system.domain.exam.vo.ExamVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface ExamService {
    List<ExamVO> list(ExamQueryDTO examQueryDTO);

    String add(ExamAddDTO examAddDTO);

    boolean addQuestion(ExamQuestAddDTO examQuestAddDTO);

    ExamDetailVO detail(Long examId);

    int edit(ExamEditDTO examEditDTO);

    int deleteQuestion(Long examId, Long questionId);

    int delete(Long examId);

    int publish(Long examId);

    int cancelPublish(Long examId);
}
