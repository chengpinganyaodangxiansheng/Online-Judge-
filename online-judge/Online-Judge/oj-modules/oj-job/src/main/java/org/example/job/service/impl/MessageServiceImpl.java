package org.example.job.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.job.domain.message.Message;
import org.example.job.mapper.message.MessageMapper;
import org.example.job.service.MessageService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper,Message> implements MessageService {

    /**
     * 批量插入
     * @param messageList
     * @return
     */
    public boolean batchInsert(List<Message> messageList) {
        return saveBatch(messageList);
    }
}
