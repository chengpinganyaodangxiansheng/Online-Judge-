package org.example.common.core.utils;

import cn.hutool.core.util.StrUtil;
import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ThreadLocalUtil {
    private static final TransmittableThreadLocal<Map<String, Object>> THREAD_LOCAL = new TransmittableThreadLocal<>();

    /**
     * 在线程本地变量中存放数据
     * @param key
     * @param value
     */
    public static void set(String key, Object value) {
        //通过Hash的方式来存储数据
        Map<String, Object> map = getLocalMap();
        map.put(key, value == null ? StrUtil.EMPTY : value);
    }

    /**
     * 从线程本地变量中获取数据
     * @param key
     * @param clazz
     * @return
     * @param <T>
     */
    public static <T> T get(String key, Class<T> clazz) {
        Map<String, Object> map = getLocalMap();
        //map.getOrDefault(key, null)：若key不存在返回 null。
        return (T) map.getOrDefault(key, null);
    }

    /**
     * 获取当前线程的Map（懒初始化）。
     * @return
     */
    public static Map<String, Object> getLocalMap() {
        Map<String, Object> map = THREAD_LOCAL.get();
        if (map == null) {
            //若当前线程无Map（THREAD_LOCAL.get() 返回 null），则初始化。
            //使用 ConcurrentHashMap 确保同一线程内的并发操作安全（如：多任务共享线程时）。
            map = new ConcurrentHashMap<String, Object>();
            THREAD_LOCAL.set(map);
        }
        return map;
    }

    /**
     * 删除线程本地变量
     */
    public static void remove() {
        THREAD_LOCAL.remove();
    }
}