package org.example.friend.domain.user.dto;

import lombok.Data;

@Data
public class UserSubmitDTO {

    /** 同一次网络重试必须复用；用户主动新提交时生成新键。 */
    private String idempotencyKey;

    /** 练习页面看到的题目版本；比赛由服务端读取冻结版本。 */
    private Long questionVersion;

    /** 比赛编号；练习提交不填写，比赛提交据此校验报名并更新成绩。 */
    private Long examId; // 可选

    /** 提交对应的题目编号，用于读取题目及关联判题记录。 */
    private Long questionId;

    /** 编程语言的数值标识；当前可靠判题受理路径只接受零值对应的语言。 */
    private Integer programType; // (0: java  1:cpp 2: golang)

    /** 用户提交的原始源码；受理时服务端另行组装执行快照，幂等内容校验不修改此原文。 */
    private String userCode;
}
