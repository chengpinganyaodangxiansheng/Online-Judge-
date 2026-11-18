package org.example.friend.manager;

import com.github.pagehelper.PageHelper;
import org.example.common.core.constants.CacheConstants;
import org.example.common.core.constants.Constants;
import org.example.common.core.enums.ExamListType;
import org.example.common.core.enums.ResultCode;
import org.example.common.redis.service.RedisService;
import org.example.common.security.exception.ServiceException;
import org.example.friend.domain.exam.Exam;
import org.example.friend.domain.exam.ExamQuestion;
import org.example.friend.domain.exam.dto.ExamQueryDTO;
import org.example.friend.domain.exam.dto.ExamRankDTO;
import org.example.friend.domain.exam.vo.ExamRankVO;
import org.example.friend.domain.exam.vo.ExamVO;
import org.example.friend.domain.user.UserExam;
import org.example.friend.mapper.exam.ExamMapper;
import org.example.friend.mapper.exam.ExamQuestionMapper;
import org.example.friend.mapper.user.UserExamMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Component
public class ExamCacheManager {
    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private ExamQuestionMapper examQuestionMapper;

    @Autowired
    private UserExamMapper userExamMapper;

    @Autowired
    private RedisService redisService;

    /**
     * 获取竞赛列表的总量(传入userId时，查询的是该用户的竞赛列表)
     * @param examListType
     * @param userId
     * @return
     */
    public Long getListSize(Integer examListType, Long userId) {
        String examListKey = getExamListKey(examListType, userId);
        return redisService.getListSize(examListKey);
    }

    /**
     * 获取竞赛列表中题目的总量
     * @param examId
     * @return
     */
    public Long getExamQuestionListSize(Long examId) {
        String examQuestionListKey = getExamQuestionListKey(examId);
        return redisService.getListSize(examQuestionListKey);
    }

    public Long getRankListSize(Long examId) {
        return redisService.getListSize(getExamRankListKey(examId));
    }

    public List<ExamVO> getExamVOList(ExamQueryDTO examQueryDTO, Long userId) {
        int start = (examQueryDTO.getPageNum() - 1) * examQueryDTO.getPageSize();
        //下标需要 -1
        int end = start + examQueryDTO.getPageSize() - 1;
        //获取此次查询需要的key
        String examListKey = getExamListKey(examQueryDTO.getType(), userId);
        //按照范围查询此次key的列表value
        List<Long> examIdList = redisService.getCacheListByRange(examListKey, start, end, Long.class);
        //从redis缓存中获取数据
        List<ExamVO> examVOList = assembleExamVOList(examIdList);
        if (CollectionUtil.isEmpty(examVOList)) {
            //说明redis中数据可能有问题 从数据库中查数据并且重新刷新缓存
            examVOList = getExamListByDB(examQueryDTO, userId); //从数据库中获取数据
            refreshCache(examQueryDTO.getType(), userId);
        }
        return examVOList;
    }

    public List<ExamRankVO> getExamRankList(ExamRankDTO examRankDTO) {
        int start = (examRankDTO.getPageNum() - 1) * examRankDTO.getPageSize();
        int end = start + examRankDTO.getPageSize() - 1; //下标需要 -1
        return redisService.getCacheListByRange(getExamRankListKey(examRankDTO.getExamId()), start, end, ExamRankVO.class);
    }

    public List<Long> getAllUserExamList(Long userId) {
        //获取用户竞赛列表的key
        String examListKey = CacheConstants.USER_EXAM_LIST + userId;
        //在redis缓存中查询竞赛列表key中的全部value(也就是用户参加的所有竞赛的竞赛Id)
        List<Long> userExamIdList = redisService.getCacheListByRange(examListKey, 0, -1, Long.class);
        if (CollectionUtil.isNotEmpty(userExamIdList)) {
            //如果redis中的数据不为null，则直接返回，不然还需要从数据库中重新查询
            return userExamIdList;
        }

        //从数据库中查询用户参加的竞赛的所有竞赛Id
        List<UserExam> userExamList = userExamMapper.selectList(new LambdaQueryWrapper<UserExam>().eq(UserExam::getUserId, userId));
        if (CollectionUtil.isEmpty(userExamList)) {
            //如果数据库中都没有数据，直接返回null
            return null;
        }
        //数据库中查完数据还需要刷新缓存，让redis缓存中也有数据
        refreshCache(ExamListType.USER_EXAM_LIST.getValue(), userId);

        //在数据库中查询到的数据是UserExam的实体类列表，而不是所需要的所有竞赛Id列表，故通过stream流，来获取ExamId列表
        return userExamList.stream().map(UserExam::getExamId).collect(Collectors.toList());
    }

    public void addUserExamCache(Long userId, Long examId) {
        String userExamListKey = getUserExamListKey(userId);
        redisService.leftPushForList(userExamListKey, examId);
    }

    /**
     * 获取竞赛中题目的第一个
     * @param examId
     * @return
     */
    public Long getFirstQuestion(Long examId) {
        return redisService.indexForList(getExamQuestionListKey(examId), 0, Long.class);
    }


    public Long preQuestion(Long examId, Long questionId) {
        Long index = redisService.indexOfForList(getExamQuestionListKey(examId), questionId);
        if (index == 0) {
            throw new ServiceException(ResultCode.FAILED_FIRST_QUESTION);
        }
        return redisService.indexForList(getExamQuestionListKey(examId), index - 1, Long.class);
    }

    public Long nextQuestion(Long examId, Long questionId) {
        Long index = redisService.indexOfForList(getExamQuestionListKey(examId), questionId);
        long lastIndex = getExamQuestionListSize(examId) - 1;
        if (index == lastIndex) {
            throw new ServiceException(ResultCode.FAILED_LAST_QUESTION);
        }
        return redisService.indexForList(getExamQuestionListKey(examId), index + 1, Long.class);
    }

    //刷新缓存逻辑
    public void refreshCache(Integer examListType, Long userId) {
        List<Exam> examList = new ArrayList<>();
        if (ExamListType.EXAM_UN_FINISH_LIST.getValue().equals(examListType)) {
            //查询未完赛的竞赛列表
            examList = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                    .select(Exam::getExamId, Exam::getTitle, Exam::getStartTime, Exam::getEndTime)
                    .gt(Exam::getEndTime, LocalDateTime.now())
                    .eq(Exam::getStatus, Constants.TRUE)
                    .orderByDesc(Exam::getCreateTime));
        } else if (ExamListType.EXAM_HISTORY_LIST.getValue().equals(examListType)) {
            //查询历史竞赛
            examList = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                    .select(Exam::getExamId, Exam::getTitle, Exam::getStartTime, Exam::getEndTime)
                    .le(Exam::getEndTime, LocalDateTime.now())
                    .eq(Exam::getStatus, Constants.TRUE)
                    .orderByDesc(Exam::getCreateTime));
        } else if (ExamListType.USER_EXAM_LIST.getValue().equals(examListType)) {
            //查询用户的个人竞赛列表
            List<ExamVO> examVOList = userExamMapper.selectUserExamList(userId);
            //数据库中查询得到的数据是ExamVO，通过hutool工具的BeanUtil来将VO转换成实体类
            examList = BeanUtil.copyToList(examVOList, Exam.class);
        }

        //如果数据库中也没有数据，直接进行返回
        if (CollectionUtil.isEmpty(examList)) {
            return;
        }

        //封装数据，把数据批量放入到redis的缓存中
        Map<String, Exam> examMap = new HashMap<>();
        //将redis中需要的examId进行获取list集合
        List<Long> examIdList = new ArrayList<>();
        for (Exam exam : examList) {
            //在Map中放入redis中真正存储信息的key和value
            examMap.put(getDetailKey(exam.getExamId()), exam);
            examIdList.add(exam.getExamId());
        }
        redisService.multiSet(examMap);
        //刷新详情缓存
        redisService.deleteObject(getExamListKey(examListType, userId));
        //将列表的value尾插到redis中,也就是e:t:l or e:h:lkey的value   ->刷新列表缓存
        redisService.rightPushAll(getExamListKey(examListType, userId), examIdList);
    }

    /**
     * 刷新竞赛的题目列表缓存
     * @param examId
     */
    public void refreshExamQuestionCache(Long examId) {
        //先从数据库中查询该竞赛的题目列表信息
        List<ExamQuestion> examQuestionList = examQuestionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .select(ExamQuestion::getQuestionId)
                .eq(ExamQuestion::getExamId, examId)
                .orderByAsc(ExamQuestion::getQuestionOrder));
        if (CollectionUtil.isEmpty(examQuestionList)) {
            return;
        }
        //获取该竞赛的题目列表的Ids,并尾插到redis缓存中
        List<Long> examQuestionIdList = examQuestionList.stream().map(ExamQuestion::getQuestionId).toList();
        redisService.rightPushAll(getExamQuestionListKey(examId), examQuestionIdList);
        //节省 redis缓存资源
        long seconds = ChronoUnit.SECONDS.between(LocalDateTime.now(),
                LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0));
        redisService.expire(getExamQuestionListKey(examId), seconds, TimeUnit.SECONDS);
    }

    public void refreshExamRankCache(Long examId) {
        List<ExamRankVO> examRankVOList = userExamMapper.selectExamRankList(examId);
        if (CollectionUtil.isEmpty(examRankVOList)) {
            return;
        }
        redisService.rightPushAll(getExamRankListKey(examId), examRankVOList);
    }

    private List<ExamVO> getExamListByDB(ExamQueryDTO examQueryDTO, Long userId) {
        PageHelper.startPage(examQueryDTO.getPageNum(), examQueryDTO.getPageSize());
        if (ExamListType.USER_EXAM_LIST.getValue().equals(examQueryDTO.getType())) {
            //查询我的竞赛列表
            return userExamMapper.selectUserExamList(userId);
        } else {
            //查询C端的竞赛列表
            return examMapper.selectExamList(examQueryDTO);
        }
    }

    private List<ExamVO> assembleExamVOList(List<Long> examIdList) {
        if (CollectionUtil.isEmpty(examIdList)) {
            //说明redis当中没数据 从数据库中查数据并且重新刷新缓存
            return null;
        }
        //将每个examId和前缀拼接成key放入到detailKeyList中，通过redis的方法查询每个竞赛的详情信息
        List<String> detailKeyList = new ArrayList<>();
        for (Long examId : examIdList) {
            detailKeyList.add(getDetailKey(examId));
        }
        //通过redis批量处理的方式获取全部value
        List<ExamVO> examVOList = redisService.multiGet(detailKeyList, ExamVO.class);
        //去除集合中为NULL的值
        CollUtil.removeNull(examVOList);
        if (CollectionUtil.isEmpty(examVOList) || examVOList.size() != examIdList.size()) {
            //说明redis中数据有问题 从数据库中查数据并且重新刷新缓存
            return null;
        }
        return examVOList;
    }

    private String getExamListKey(Integer examListType, Long userId) {
        if (ExamListType.EXAM_UN_FINISH_LIST.getValue().equals(examListType)) {
            return CacheConstants.EXAM_UNFINISHED_LIST;
        } else if (ExamListType.EXAM_HISTORY_LIST.getValue().equals(examListType)) {
            return CacheConstants.EXAM_HISTORY_LIST;
        } else {
            return CacheConstants.USER_EXAM_LIST + userId;
        }
    }

    private String getDetailKey(Long examId) {
        return CacheConstants.EXAM_DETAIL + examId;
    }

    private String getUserExamListKey(Long userId) {
        return CacheConstants.USER_EXAM_LIST + userId;
    }

    private String getExamQuestionListKey(Long examId) {
        return CacheConstants.EXAM_QUESTION_LIST + examId;
    }

    private String getExamRankListKey(Long examId) {
        return CacheConstants.EXAM_RANK_LIST + examId;
    }
}
