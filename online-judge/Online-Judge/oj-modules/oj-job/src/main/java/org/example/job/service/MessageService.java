package org.example.job.service;

import org.example.job.domain.message.Message;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface MessageService {
    boolean batchInsert(List<Message> messageList);
}
