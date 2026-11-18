package org.example.system.domain.sysuser;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.example.common.core.domain.BaseEntity;


@Data
@TableName("tb_sys_user")
public class SysUser extends BaseEntity {
    //标识主键，并且是使用雪花算法来分配ID
    @TableId(value = "USER_ID", type = IdType.ASSIGN_ID)
    private Long userId;

    private String userAccount;

    private String password;

    private String nickName;

}
