package org.example.system.manager;

import cn.hutool.cache.Cache;
import org.example.common.core.constants.CacheConstants;
import org.example.common.redis.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class QuestionCacheManager {
    @Autowired
    private RedisService redisService;

    //当管理员添加题目时，需要对redis缓存进行添加操作，不然缓存没办法进行更新
    public void addCache(Long questionId) {
        redisService.leftPushForList(CacheConstants.QUESTION_LIST,questionId);
    }
    //当管理员删除题目时，需要对redis缓存进行删除操作，不然缓存没办法进行更新
    public void deleteCache(Long questionId) {
        redisService.removeForList(CacheConstants.QUESTION_LIST,questionId);
    }
}
