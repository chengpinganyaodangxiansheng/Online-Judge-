package org.example.judge.service;


import org.example.judge.domain.SandBoxExecuteResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface SandboxService {
    SandBoxExecuteResult exeJavaCode(Long userId, String userCode, List<String> inputList);
}
