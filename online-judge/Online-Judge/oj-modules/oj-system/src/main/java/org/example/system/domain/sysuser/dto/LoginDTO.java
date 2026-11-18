package org.example.system.domain.sysuser.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class LoginDTO {
    @Schema(description = "管理员登录账号")
    private String userAccount;
    @Schema(description = "管理员登录密码")
    private String password;
}
