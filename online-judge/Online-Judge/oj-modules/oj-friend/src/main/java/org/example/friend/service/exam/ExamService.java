package org.example.friend.service.exam;

import org.example.common.core.domain.TableDataInfo;
import org.example.friend.domain.exam.dto.ExamQueryDTO;
import org.example.friend.domain.exam.dto.ExamRankDTO;
import org.example.friend.domain.exam.vo.ExamVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface ExamService {
    List<ExamVO> list(ExamQueryDTO examQueryDTO);

    TableDataInfo redisList(ExamQueryDTO examQueryDTO);

    String getFirstQuestion(Long examId);

    String preQuestion(Long examId, Long questionId);

    String nextQuestion(Long examId, Long questionId);

    TableDataInfo rankList(ExamRankDTO examRankDTO);
}
