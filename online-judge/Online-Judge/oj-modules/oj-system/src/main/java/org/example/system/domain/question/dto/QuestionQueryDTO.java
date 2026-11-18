package org.example.system.domain.question.dto;

import lombok.Data;
import org.example.common.core.domain.PageQueryDTO;

import java.util.Set;


@Data
public class QuestionQueryDTO extends PageQueryDTO {

    private Integer difficulty;

    private String title;

    private String excludeIdStr;//通过;将集合的元素拼成字符串,并且通过;可以在获取集合的id时，作为分隔符使用

    private Set<Long> excludeIdSet;//获取在竞赛中被选择的题目的id集合
}
