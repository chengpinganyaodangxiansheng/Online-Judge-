package org.example.system.domain.exam.dto;


import lombok.Data;
import org.example.common.core.domain.PageQueryDTO;

@Data
public class ExamQueryDTO extends PageQueryDTO {

    private String title;

    private String startTime;

    private String endTime;
}
