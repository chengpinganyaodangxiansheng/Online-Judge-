package org.example.api.domain.dto;

import lombok.Data;

/** 完成事务生成的排名变更事件，供后续榜单投影使用。 */
@Data
public class RankChangedDTO {
    /** 比赛编号；练习提交不填写，比赛提交据此校验报名并更新成绩。 */
    private Long examId;

    /** 提交者或成绩归属用户的编号，用于权限校验和用户维度的数据关联。 */
    private Long userId;

    /** 最新绝对总分而非增量，重复消费不能再次累加得分。 */
    private Long totalScore;
}
