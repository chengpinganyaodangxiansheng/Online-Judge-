package org.example.common.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
//将配置文件中的 file.oss 前缀下的属性值自动注入到 Java Bean 的对应字段中。
@ConfigurationProperties(prefix = "file.oss")
public class OSSProperties {

    private String endpoint;

    private String region;

    private String accessKeyId;

    private String accessKeySecret;

    private String bucketName;

    /**
     * 路径前缀，加在 endPoint 之后
     */
    private String pathPrefix;  //ojtest
}