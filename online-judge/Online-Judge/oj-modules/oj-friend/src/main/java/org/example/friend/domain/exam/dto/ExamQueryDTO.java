package org.example.friend.domain.exam.dto;

import lombok.Data;
import org.example.common.core.domain.PageQueryDTO;

@Data
public class ExamQueryDTO extends PageQueryDTO {

    private String title;

    private String startTime;

    private String endTime;

    private Integer type; //0 未完善  1 历史竞赛
}
