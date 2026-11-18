package org.example.friend.service.user.impl;


import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.example.common.core.constants.CacheConstants;
import org.example.common.core.constants.Constants;
import org.example.common.core.domain.TableDataInfo;
import org.example.common.core.enums.ExamListType;
import org.example.common.core.enums.ResultCode;
import org.example.common.core.utils.ThreadLocalUtil;
import org.example.common.security.exception.ServiceException;
import org.example.common.security.service.TokenService;
import org.example.friend.domain.exam.Exam;
import org.example.friend.domain.exam.dto.ExamQueryDTO;
import org.example.friend.domain.exam.vo.ExamVO;
import org.example.friend.domain.user.User;
import org.example.friend.domain.user.UserExam;
import org.example.friend.manager.ExamCacheManager;
import org.example.friend.mapper.exam.ExamMapper;
import org.example.friend.mapper.user.UserExamMapper;
import org.example.friend.service.user.UserExamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Service
public class UserExamServiceImpl implements UserExamService {
    @Autowired
    private UserExamMapper userExamMapper;

    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private ExamCacheManager  examCacheManager;

    @Autowired
    private TokenService tokenService;

    @Value("${jwt.secret}")
    private String secret;

    @Override
    public int enter(String token, Long examId) {
        //先判断竞赛是否存在
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            //不存在返回异常
            throw new ServiceException(ResultCode.EXAM_NOT_EXISTS);
        }

        //判断竞赛是否已经开始，开始后则不能报名
        if (exam.getStartTime().isBefore(LocalDateTime.now())) {
            throw new ServiceException(ResultCode.EXAM_STARTED);
        }


        //从token中获取userId
        //Long userId = tokenService.getUserId(token, secret);

        //从线程本地变量中获取userId
        Long userId = ThreadLocalUtil.get(Constants.USER_ID, Long.class);

        //判断该用户是否已经报名(先从数据库中查询数据)
        UserExam userExam = userExamMapper.selectOne(new LambdaQueryWrapper<UserExam>()
                .eq(UserExam::getExamId, examId)
                .eq(UserExam::getUserId, userId));

        if (userExam != null) {
            //则已经报名
            throw new ServiceException(ResultCode.USER_EXAM_HAS_ENTER);
        }


        //如果没进行报名则开始报名，并且对redis和数据库中都进行数据的插入
        userExam = new UserExam();
        userExam.setExamId(examId);
        userExam.setUserId(userId);

        //往redis中插入数据
        examCacheManager.addUserExamCache(userId,examId);

        //往MySQL中插入数据
        return userExamMapper.insert(userExam);
    }

    @Override
    public TableDataInfo list(ExamQueryDTO examQueryDTO) {
        //从线程本地变量获取userId
        Long userId = ThreadLocalUtil.get(Constants.USER_ID, Long.class);
        //设置ExamQueryDTO的type为用户身份类型
        examQueryDTO.setType(ExamListType.USER_EXAM_LIST.getValue());
        //从redis中获取缓存数据,返回值是获取的数据总量
        Long total = examCacheManager.getListSize(examQueryDTO.getType(),userId);
        List<ExamVO> examVOList;
        //如果数据不存在
        if (total == null || total <= 0) {
            //还需要进行分页操作(之前的方法中不用试因为调用的list方法里面存在分页操作)
            PageHelper.startPage(examQueryDTO.getPageNum(), examQueryDTO.getPageSize());
            //先从数据库进行查询
            examVOList = userExamMapper.selectUserExamList(userId);
            //再把数据进行刷新，刷新的过程中也会将数据放入redis的缓存中
            examCacheManager.refreshCache(examQueryDTO.getType(),userId);
            //此时redis缓存中没有数据，total就是不存在的所以需要重新获取
            total = new PageInfo<>(examVOList).getTotal();
        }else {
            //如果数据存在，直接从redis缓存中获取信息
            examVOList = examCacheManager.getExamVOList(examQueryDTO,userId);
            //在getExamVOList的方法中会进行refreshCache方法，此时可能total在redis获取的是异常情况，之前的total就不准确
            //因为total是异常时，会重新进入数据库进行查询数据，并且更新缓存
            total = examCacheManager.getListSize(examQueryDTO.getType(),userId);
        }

        if (CollectionUtil.isEmpty(examVOList)) {
            return TableDataInfo.empty();
        }

        return TableDataInfo.success(examVOList,total);
    }
}
