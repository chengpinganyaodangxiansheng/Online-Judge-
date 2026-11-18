package org.example.system.domain.exam.dto;


import lombok.Data;

import java.util.LinkedHashSet;

@Data
public class ExamQuestAddDTO {
    private Long examId;

    private LinkedHashSet<Long> questionIdSet;
}
