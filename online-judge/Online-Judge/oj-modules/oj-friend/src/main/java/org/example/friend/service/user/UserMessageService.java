package org.example.friend.service.user;

import org.example.common.core.domain.PageQueryDTO;
import org.example.common.core.domain.TableDataInfo;
import org.springframework.stereotype.Service;

@Service
public interface UserMessageService {
    TableDataInfo list(PageQueryDTO pageQueryDTO);
}
