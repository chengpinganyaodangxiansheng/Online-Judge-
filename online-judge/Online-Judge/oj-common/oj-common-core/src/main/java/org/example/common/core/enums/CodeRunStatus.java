package org.example.common.core.enums;

import lombok.Getter;

@Getter
public enum CodeRunStatus {
    RUNNING(1, "运行中"),

    SUCCEED(2, "运行成功"),

    FAILED(3, "运行失败"),

    NOT_ALL_PASSED(4, "未通过所有用例"),

    UNKNOWN_FAILED(5, "未知异常，请您稍后重试"),

    COMPILE_FAILED(6, "编译失败"),

    OUT_OF_MEMORY(7, "运行结果正确，但是超出空间限制"),

    OUT_OF_TIME(8, "超出时间限制"),
    // stdout 与 stderr 共用输出预算；达到超限条件映射为 OLE，不作为系统故障重试。
    OUTPUT_LIMIT(9, "输出超过限制");

    /** 执行状态对应的数值编码，供状态传递和比较使用。 */
    private Integer value;

    /** 状态对应的提示文字，用于向调用方解释处理结果。 */
    private String msg;

    /** 绑定执行状态的数值编码和提示文字，供结果分类及展示使用。 */
    CodeRunStatus(Integer value, String msg) {
        this.value = value;
        this.msg = msg;
    }
}
