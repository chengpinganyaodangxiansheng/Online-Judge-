package org.example.system.domain.user.dto;

import lombok.Data;
import org.example.common.core.domain.PageQueryDTO;

@Data
public class UserQueryDTO extends PageQueryDTO {
    private Long userId;

    private String nickName;
}
