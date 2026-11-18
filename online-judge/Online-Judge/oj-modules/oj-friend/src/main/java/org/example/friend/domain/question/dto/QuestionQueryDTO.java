package org.example.friend.domain.question.dto;

import lombok.Data;
import org.example.common.core.domain.PageQueryDTO;

@Data
public class QuestionQueryDTO extends PageQueryDTO {

    private String keyword;

    private Integer difficulty;
}