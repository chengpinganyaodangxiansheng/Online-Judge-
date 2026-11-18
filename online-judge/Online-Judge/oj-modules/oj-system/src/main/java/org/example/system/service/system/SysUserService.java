package org.example.system.service.system;

import org.example.common.core.domain.Result;
import org.example.common.core.domain.vo.LoginUserVO;
import org.example.system.domain.sysuser.dto.LoginDTO;
import org.example.system.domain.sysuser.dto.SysUserSaveDTO;
import org.springframework.stereotype.Service;

@Service
public interface SysUserService {

    Result<String> login(LoginDTO loginDTO);

    int add(SysUserSaveDTO sysUserSaveDTO);

    Result<LoginUserVO> info(String token);

    Result logout(String token);
}
