package org.example.friend.controller.file;

import org.example.common.core.controller.BaseController;
import org.example.common.core.domain.Result;
import org.example.common.file.domain.OSSResult;
import org.example.friend.service.file.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

;

@RestController
@RequestMapping("/file")
public class FileController extends BaseController {
    @Autowired
    private FileService fileService;

    @PostMapping("/upload")
    public Result<OSSResult> uploadFile(@RequestBody MultipartFile file) {
        return Result.success(fileService.upload(file));
    }
}
