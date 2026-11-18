package org.example.common.core.domain;

import lombok.Data;

@Data
public class LoginUser {
    private String nickName;//用户昵称

    private Integer identity; // 1. 普通用户 2.管理员用户

    private String headImage;//头像
}
