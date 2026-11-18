package org.example.friend.domain.exam.dto;

import lombok.Data;
import org.example.common.core.domain.PageQueryDTO;

@Data
public class ExamRankDTO extends PageQueryDTO {

    private Long examId;
}