package org.example.system.manager;

import org.example.common.core.constants.CacheConstants;
import org.example.common.redis.service.RedisService;

import org.example.system.domain.exam.Exam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExamCacheManager {
    @Autowired
    private RedisService redisService;

    public void addCache(Exam exam) {
        //在链表中存储未发布的竞赛，key是固定的，使用list是因为前端会进行分页，value中不能存储竞赛的详细信息
        //因为链表中有两个key，一个是未发布的key，一个是历史记录的key，这两个key中可能会存储相同的数据，如果都存储相同的竞赛信息，就会冗余
        //于是value都保存竞赛的id，之后再使用String的数据结构创建一个竞赛id的key，value就是竞赛的详情信息。
        redisService.leftPushForList(getExamListKey(),exam.getExamId());
        //此处就是使用String的数据结构创建一个竞赛id的key，value就是竞赛的详情信息。
        redisService.setCacheObject(getDetailKey(exam.getExamId()), exam);
    }

    public void deleteCache(Exam exam) {
        //删除缓存时，需要将链表的key删除，也需要将竞赛详情的key删除
        redisService.removeForList(getExamListKey(),exam.getExamId());
        redisService.deleteObject(getDetailKey(exam.getExamId()));
        //当竞赛取消发布时，直接删除竞赛中题目列表的redis缓存，这样在竞赛的题目列表中就不需要在添加题目、删除题目和编辑题目中进行增加缓存和删除的缓存操作
        //这是因为当竞赛发布时，用户通过竞赛的方式来进行答题操作，此时就会对竞赛的题目列表进行查询，此时缓存已经被删除了(如果此时题目列表是通过撤销发布添加了题目后再进行了发布)
        //所以，此时redis中是没有竞赛的题目列表缓存，进行答题操作就会直接触发缓存操作
        redisService.deleteObject(getExamQuestionListKey(exam.getExamId()));
    }


    public String getExamListKey() {
        //e:t:l  exam:time:list
        return CacheConstants.EXAM_UNFINISHED_LIST;
    }

    public String getDetailKey(Long examId) {
        //e:d:examId   exam:detail:examId
        return CacheConstants.EXAM_DETAIL + examId;
    }

    private String getExamQuestionListKey(Long examId) {
        //e:q:l:examId exam:question:list:examId
        return CacheConstants.EXAM_QUESTION_LIST + examId;
    }
}
