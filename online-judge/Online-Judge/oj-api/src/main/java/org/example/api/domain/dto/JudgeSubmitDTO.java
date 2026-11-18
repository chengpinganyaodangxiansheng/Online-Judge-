package org.example.api.domain.dto;

import lombok.Data;

import java.util.List;

/** 服务端生成的执行快照；包含隐藏用例，仅供内部判题使用，不能作为公开详情返回。 */
@Data
public class JudgeSubmitDTO {
    /** 首次受理时保存固定答案比较规则，后续执行不能隐式切换空白语义。 */
    private String outputComparison = "NORMALIZE_LINE_ENDINGS_IGNORE_TRAILING_NEWLINES_V1";

    /** 受理时固定的规则版本，后续重试不切换到最新版本。 */
    private Long questionVersion;

    /** 提交者或成绩归属用户的编号，用于权限校验和用户维度的数据关联。 */
    private Long userId;

    /** 比赛编号；练习提交不填写，比赛提交据此校验报名并更新成绩。 */
    private Long examId;

    // 编程语言类型（0 java 1 C++）
    private Integer programType;

    /** 提交对应的题目编号，用于读取题目及关联判题记录。 */
    private Long questionId;

    // 题目难度
    private Integer difficulty;

    // 时间限制 ms
    private Long timeLimit;

    // 空间限制 kb
    private Long spaceLimit;

    // 拼装完整的用户代码  用户提交的代码 + main函数
    private String userCode;

    // 输入数据
    private List<String> inputList;

    // 期望输出数据
    private List<String> outputList;
}
