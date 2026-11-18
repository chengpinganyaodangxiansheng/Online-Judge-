package org.example.system.service.user;


import org.example.system.domain.user.dto.UserDTO;
import org.example.system.domain.user.dto.UserQueryDTO;
import org.example.system.domain.user.vo.UserVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface UserService {
    List<UserVO> list(UserQueryDTO userQueryDTO);

    int updateStatus(UserDTO userDTO);
}
