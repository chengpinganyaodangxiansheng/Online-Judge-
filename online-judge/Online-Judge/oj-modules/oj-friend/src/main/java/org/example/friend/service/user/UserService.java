package org.example.friend.service.user;

import org.example.common.core.domain.Result;
import org.example.common.core.domain.vo.LoginUserVO;
import org.example.friend.domain.user.dto.UserDTO;
import org.example.friend.domain.user.dto.UserUpdateDTO;
import org.example.friend.domain.user.vo.UserVO;
import org.springframework.stereotype.Service;

@Service
public interface UserService {
    boolean sendCode(UserDTO userDTO);

    String codeLogin(UserDTO userDTO);

    Result logout(String token);

    Result<LoginUserVO> info(String token);

    UserVO detail();

    int edit(UserUpdateDTO userUpdateDTO);

    int updateHeadImage(String headImage);
}
