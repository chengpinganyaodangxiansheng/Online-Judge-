package org.example.common.redis.configuration;



import org.example.common.redis.JsonRedisSerializer;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@AutoConfigureBefore(RedisAutoConfiguration.class)
public class RedisConfig extends CachingConfigurerSupport {

    @Bean
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        JsonRedisSerializer serializer = new JsonRedisSerializer(Object.class);

        // 使⽤StringRedisSerializer来序列化和反序列化redis的key值
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);

        // Hash的key也采⽤StringRedisSerializer的序列化⽅式
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        //强制初始化模板，确保配置生效
        //验证配置完整性：检查必要组件（如连接工厂、序列化器）是否设置
        //初始化默认值：未显式设置的序列化器会初始化为 JDK 序列化器（可能导致二进制乱码）
        //避免运行时错误：提前暴露配置问题，防止第一次操作 Redis 时才报错
        template.afterPropertiesSet();

        return template;
    }
}