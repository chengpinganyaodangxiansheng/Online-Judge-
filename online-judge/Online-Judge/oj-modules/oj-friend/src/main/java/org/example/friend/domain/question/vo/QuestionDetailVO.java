package org.example.friend.domain.question.vo;

import lombok.Data;

@Data
public class QuestionDetailVO extends QuestionVO {
    /** 页面展示对应的规则版本，练习提交时回传以检测编辑期间发生的版本变更。 */
    private Long questionVersion;

    /** 单个运行用例的任务组处理器时间上限，单位为毫秒；不是排队期限。 */
    private Long timeLimit;

    /** 单个运行用例的任务组内存上限，单位为千字节，发送执行请求时转换为字节。 */
    private Long spaceLimit;

    /** 题目的公开题面内容，供用户阅读和编写解法。 */
    private String content;

    /** 题目提供的初始代码模板，用于初始化用户编辑器。 */
    private String defaultCode;
}
