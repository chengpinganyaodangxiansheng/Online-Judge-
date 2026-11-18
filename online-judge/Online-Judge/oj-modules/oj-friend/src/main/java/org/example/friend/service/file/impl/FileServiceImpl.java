package org.example.friend.service.file.impl;


import lombok.extern.slf4j.Slf4j;
import org.example.common.core.enums.ResultCode;
import org.example.common.file.domain.OSSResult;
import org.example.common.file.service.OSSService;
import org.example.common.security.exception.ServiceException;
import org.example.friend.service.file.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class FileServiceImpl implements FileService {
    @Autowired
    private OSSService ossService;

    @Override
    public OSSResult upload(MultipartFile file) {
        try {
            return ossService.uploadFile(file);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new ServiceException(ResultCode.FAILED_FILE_UPLOAD);
        }
    }
}
