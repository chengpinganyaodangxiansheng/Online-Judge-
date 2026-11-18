package org.example.friend.manager;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.common.core.constants.CacheConstants;
import org.example.common.core.enums.ResultCode;
import org.example.common.redis.service.RedisService;
import org.example.common.security.exception.ServiceException;
import org.example.friend.domain.question.Question;
import org.example.friend.mapper.question.QuestionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QuestionCacheManager {
    @Autowired
    private RedisService redisService;

    @Autowired
    private QuestionMapper questionMapper;

    public Long getListSize() {
        //通过QUESTION_LIST作为缓存在redis中的key，获取这个链表的value值
        return redisService.getListSize(CacheConstants.QUESTION_LIST);
    }

    public Long getHotListSize() {
        //返回热点数据的数量
        return redisService.getListSize(CacheConstants.QUESTION_HOST_LIST);
    }


    public void refreshCache() {
        //从数据库中获取题目列表信息，并存入缓存中
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .select(Question::getQuestionId).orderByDesc(Question::getCreateTime)
        );
        if (CollectionUtil.isEmpty(questions)) {
            return;
        }
        //获取QuestionIds，通过Stream流获取
        List<Long> questionIds = questions.stream().map(Question::getQuestionId).toList();
        //将数据与redis缓存进行同步
        redisService.rightPushAll(CacheConstants.QUESTION_LIST, questionIds);
    }

    public Long preQuestion(Long questionId) {
        //还有一种进行下标存储的方式是直接使用IDEA中的list进行存储数据，小数据使用list可以，但是大数据还是通过redis来进行缓存更好
//        List<Long> cacheListByRange = redisService.getCacheListByRange(CacheConstants.QUESTION_LIST, 0, -1, Long.class);
//        int index1 = cacheListByRange.indexOf(questionId);
//        if (index1 == 0) {
//            throw new ServiceException(ResultCode.FAILED_FIRST_QUESTION);
//        }
//        return cacheListByRange.get(index1 - 1);
        //获取当前题目的index
        Long index = redisService.indexOfForList(CacheConstants.QUESTION_LIST, questionId);
        //如果此时是第一题需要先进性判断
        if (index == 0) {
            throw new ServiceException(ResultCode.FAILED_FIRST_QUESTION);
        }
        //并且将获取上一道题的questionId
        return redisService.indexForList(CacheConstants.QUESTION_LIST,index - 1,Long.class);
    }

    public Long nextQuestion(Long questionId) {
        //获取当前题目的index
        Long index = redisService.indexOfForList(CacheConstants.QUESTION_LIST, questionId);
        //如果此时是最后一道题需要先进性判断
        if (index == getListSize() - 1) {
            throw new ServiceException(ResultCode.FAILED_LAST_QUESTION);
        }
        //并且将获取上一道题的questionId
        return redisService.indexForList(CacheConstants.QUESTION_LIST,index + 1,Long.class);
    }


    //从数据库中将热点数据Id列表刷新到redis
    public void refreshHotQuestionList(List<Long> questionHotIds) {
        if (questionHotIds == null || questionHotIds.isEmpty()) {
            return ;
        }

        redisService.rightPushAll(CacheConstants.QUESTION_HOST_LIST, questionHotIds);
    }

    //获取热点题目Id列表
    public List<Long> getHotList() {
        return redisService.getCacheListByRange(CacheConstants.QUESTION_HOST_LIST,CacheConstants.DEFAULT_START
                                               ,CacheConstants.DEFAULT_END,Long.class);
    }
}
