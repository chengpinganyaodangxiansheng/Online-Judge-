package org.example.common.file.service;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.ObjectId;
import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;

import lombok.extern.slf4j.Slf4j;
import org.example.common.core.constants.CacheConstants;
import org.example.common.core.constants.Constants;
import org.example.common.core.enums.ResultCode;
import org.example.common.core.utils.ThreadLocalUtil;
import org.example.common.file.config.OSSProperties;
import org.example.common.file.domain.OSSResult;
import org.example.common.redis.service.RedisService;
import org.example.common.security.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RefreshScope
public class OSSService {

    @Autowired
    private OSSProperties prop;

    @Autowired
    private OSS ossClient;

    @Autowired
    private RedisService redisService;

    @Value("${file.max-time}")
    private int maxTime;

    @Value("${file.test}")
    private boolean test;

    public OSSResult uploadFile(MultipartFile file) throws Exception {
        if (!test) {
            //限制头像上传图片的次数
            checkUploadCount();
        }
        InputStream inputStream = null;
        try {
            String fileName;
            if (file.getOriginalFilename() != null) {
                fileName = file.getOriginalFilename().toLowerCase();
            } else {
                fileName = "a.png";
            }
            String extName = fileName.substring(fileName.lastIndexOf(".") + 1);
            inputStream = file.getInputStream();
            return upload(extName, inputStream);
        } catch (Exception e) {
            log.error("OSS upload file error", e);
            throw new ServiceException(ResultCode.FAILED_FILE_UPLOAD);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }


    private void checkUploadCount() {
        Long userId = ThreadLocalUtil.get(Constants.USER_ID, Long.class);
        Long times = redisService.getCacheMapValue(CacheConstants.USER_UPLOAD_TIMES_KEY, String.valueOf(userId), Long.class);
        if (times != null && times >= maxTime) {
            throw new ServiceException(ResultCode.FAILED_FILE_UPLOAD_TIME_LIMIT);
        }
        redisService.incrementHashValue(CacheConstants.USER_UPLOAD_TIMES_KEY, String.valueOf(userId), 1);
        if (times == null || times == 0) {
            long seconds = ChronoUnit.SECONDS.between(LocalDateTime.now(),
                    LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0));
            redisService.expire(CacheConstants.USER_UPLOAD_TIMES_KEY, seconds, TimeUnit.SECONDS);
        }
    }

    private OSSResult upload(String fileType, InputStream inputStream) {
        // key pattern: file/id.xxx, cannot start with / prop.getPathPrefix()：从配置对象 prop 中获取路径前缀，例如 "images/" 或 "files/"。这个前缀用于在 Bucket 内模拟文件夹结构，便于管理。  ObjectId.next()：生成一个全局唯一的 ID
        String key = prop.getPathPrefix() + ObjectId.next() + "." + fileType;//提示生成的 key（对象在 OSS 中的唯一标识）的格式为 文件路径/唯一ID.扩展名，并且不能以斜杠 / 开头（OSS 的 key 如果以 / 开头，会被视为绝对路径，可能导致访问问题或不符合预期）。
        ObjectMetadata objectMetadata = new ObjectMetadata();//OSS 中对象的元数据，用于设置对象的属性，如内容类型、内容长度、访问权限、自定义头等。
        objectMetadata.setObjectAcl(CannedAccessControlList.PublicRead);//这里通过 CannedAccessControlList.PublicRead 将对象的权限设为公共读。这意味着任何人（无需认证）都可以通过 URL 直接访问该文件，适合用于图片、静态资源等需要公开访问的场景。
        /**
         * 封装上传请求的参数。构造参数依次为：
         * prop.getBucketName()：目标 Bucket 的名称（如 my-bucket），从配置对象中获取。
         * key：上面生成的对象唯一标识。
         * inputStream：待上传文件的输入流（通常是 InputStream 类型，比如从客户端上传的 MultipartFile 获取的流）。直接使用流可以避免将文件全部加载到内存，适合大文件上传。
         * objectMetadata：前面设置的元数据，包含了 ACL 信息。
         */
        PutObjectRequest request = new PutObjectRequest(prop.getBucketName(), key, inputStream, objectMetadata);
        PutObjectResult putObjectResult;
        try {
            putObjectResult = ossClient.putObject(request);
        } catch (Exception e) {
            log.error("OSS put object error: {}", ExceptionUtil.stacktraceToOneLineString(e, 500));
            throw new ServiceException(ResultCode.FAILED_FILE_UPLOAD);
        }
        return assembleOSSResult(key, putObjectResult);
    }

    private OSSResult assembleOSSResult(String key, PutObjectResult putObjectResult) {
        OSSResult ossResult = new OSSResult();
        if (putObjectResult == null || StrUtil.isBlank(putObjectResult.getRequestId())) {//StrUtil.isBlank 是 Hutool 工具类中 StrUtil 提供的一个静态方法，用于判断一个字符串是否为 null、空字符串或者只包含空白字符。
            ossResult.setSuccess(false);
        } else {
            ossResult.setSuccess(true);
            ossResult.setName(FileUtil.getName(key));
        }
        return ossResult;
    }
}