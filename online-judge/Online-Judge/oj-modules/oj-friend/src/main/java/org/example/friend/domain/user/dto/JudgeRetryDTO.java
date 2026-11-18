package org.example.friend.domain.user.dto;

import lombok.Data;

/** 人工重试请求；操作人由登录身份获取，客户端只填写需要持久化审计的原因。 */
@Data
public class JudgeRetryDTO {
    /** 管理员请求人工重试时填写的原因，必须写入审计记录。 */
    private String reason;
}
