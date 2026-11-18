package org.example.job.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.job.domain.message.MessageText;
import org.example.job.mapper.message.MessageTextMapper;
import org.example.job.service.MessageTestService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageTestServiceImpl extends ServiceImpl<MessageTextMapper, MessageText> implements MessageTestService {

    /**
     * 批量插入
     * @param messageTextList
     * @return
     */
    public boolean batchInsert(List<MessageText> messageTextList) {
        return saveBatch(messageTextList);
    }
}
