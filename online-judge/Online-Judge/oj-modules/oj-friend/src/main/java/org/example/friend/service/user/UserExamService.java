package org.example.friend.service.user;


import org.example.common.core.domain.Result;
import org.example.common.core.domain.TableDataInfo;
import org.example.friend.domain.exam.dto.ExamDTO;
import org.example.friend.domain.exam.dto.ExamQueryDTO;
import org.springframework.stereotype.Service;

@Service
public interface UserExamService {
    int enter(String token, Long examId);

    TableDataInfo list(ExamQueryDTO examQueryDTO);
}
