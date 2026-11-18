package org.example.friend.controller.user;

import org.example.common.core.controller.BaseController;
import org.example.common.core.domain.PageQueryDTO;
import org.example.common.core.domain.TableDataInfo;
import org.example.friend.service.user.UserMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/message")
public class UserMessageController extends BaseController {

    @Autowired
    private UserMessageService userMessageService;

    @GetMapping("/list")
    public TableDataInfo list(PageQueryDTO pageQueryDTO) {
        return userMessageService.list(pageQueryDTO);
    }
}