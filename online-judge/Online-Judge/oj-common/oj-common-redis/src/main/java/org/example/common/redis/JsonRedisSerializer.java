package org.example.common.redis;

import com.alibaba.fastjson2.JSON;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import java.nio.charset.Charset;


public class JsonRedisSerializer <T> implements RedisSerializer<T>{

    public static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");


    //clazz 字段：存储目标类型的 Class 对象（如 User.class, Order.class 等）
    private Class<T> clazz;

    //构造函数：初始化时接收具体的类类型，为反序列化提供类型信息
    public JsonRedisSerializer(Class<T> clazz) {
        super();
        this.clazz = clazz;
    }

    @Override
    public byte[] serialize(T t) throws SerializationException {
        if (t == null) {
            return new byte[0];
        }
        return JSON.toJSONString(t).getBytes(DEFAULT_CHARSET);
    }

    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length <= 0) {
            return null;
        }
        String str = new String(bytes, DEFAULT_CHARSET);
        //在 deserialize() 方法中，clazz 用于确定 JSON 数据转换的目标类型：
        return JSON.parseObject(str, clazz);
    }

}
