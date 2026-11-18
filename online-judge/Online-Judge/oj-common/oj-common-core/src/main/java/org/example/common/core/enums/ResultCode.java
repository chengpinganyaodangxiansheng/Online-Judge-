package org.example.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;

@Getter
@Service
@AllArgsConstructor
public enum ResultCode {
    /** 定义状态码 */

    // 操作唱功
    SUCCESS(1000, "操作成功"),

    // 服务器内部错误，友好提示
    ERROR(2000, "服务繁忙请稍后重试"),

    // 操作失败，但是服务器不存在异常
    FAILED(3000, "操作失败"),
    FAILED_UNAUTHORIZED(3001, "未授权"),
    FAILED_PARAMS_VALIDATE(3002, "参数校验失败"),
    FAILED_NOT_EXISTS(3003, "资源不存在"),
    FAILED_ALREADY_EXISTS(3004, "资源已存在"),

    AILED_USER_EXISTS(3101, "用户已存在"),
    FAILED_USER_NOT_EXISTS(3102, "用户不存在"),
    FAILED_LOGIN(3103, "账号或密码错误"),
    FAILED_USER_BANNED(3104, "您已被列入黑名单, 请联系管理员."),
    FAILED_USER_EMAIL(3105, "你输入的邮箱有误"),

    FAILED_FREQUENT(3106, "操作频繁，请稍后重试"),

    FAILED_TIME_LIMIT(3107, "当天请求次数已达到上限"),

    FAILED_SEND_CODE(3108, "验证码发送错误"),

    FAILED_INVALID_CODE(3109, "验证码无效"),

    FAILED_ERROR_CODE(3110, "验证码错误"),

    EXAM_START_TIME_BEFORE_CURRENT_TIME(3201, "竞赛开始时间不能早于当前时间"),

    EXAM_START_TIME_AFTER_END_TIME(3202, "竞赛开始时间不能晚于竞赛结束时间"),

    EXAM_NOT_EXISTS(3203, "竞赛不存在"),

    EXAM_QUESTION_NOT_EXISTS(3204, "为竞赛新增的题目不存在"),

    EXAM_STARTED(3205, "竞赛已经开始，无法进行操作"),

    EXAM_NOT_HAS_QUESTION(3206, "竞赛当中不包含题目"),

    EXAM_IS_FINISH(3207, "竞赛已经结束不能进行操作"),

    EXAM_IS_PUBLISH(3208, "竞赛已经发布不能进行编辑、删除操作"),

    USER_EXAM_HAS_ENTER(3301, "用户已经报过名，无需重复报名"),

    FAILED_FILE_UPLOAD(3401, "文件上传失败"),

    FAILED_FILE_UPLOAD_TIME_LIMIT(3402, "当天上传图片数量超过上限"),

    FAILED_FIRST_QUESTION(3501, "当前题目已经是第一题了哦"),

    FAILED_LAST_QUESTION(3502, "当前题目已经是最后一题了哦"),

    FAILED_NOT_SUPPORT_PROGRAM(3601, "当前不支持此语言"),

    FAILED_RABBIT_PRODUCE(3701, "mq生产消息异常"),
    // 同键不同代码/题目等内容不可复用原任务，客户端应明确发起新提交。
    SUBMIT_CONFLICT(409, "幂等键已用于不同的提交内容"),
    // 仅新受理检查当前版本；已成功受理的幂等重放仍返回原任务。
    QUESTION_VERSION_CONFLICT(409, "题目版本已变化，请刷新题目后新建提交"),
    // 用于比赛资格、时间窗口、任务可重试状态等不满足业务条件的情况。
    SUBMIT_NOT_ALLOWED(3702, "当前题目或比赛不允许提交");

    /** 接口结果的数值编码，用于区分成功、参数错误和业务失败。 */
    private int code;

    /** 状态对应的提示文字，用于向调用方解释处理结果。 */
    private String msg;
}
