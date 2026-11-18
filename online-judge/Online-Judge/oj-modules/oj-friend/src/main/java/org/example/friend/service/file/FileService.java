package org.example.friend.service.file;


import org.example.common.file.domain.OSSResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public interface FileService {
    OSSResult upload(MultipartFile file);
}
