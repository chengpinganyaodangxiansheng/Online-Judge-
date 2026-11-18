package org.example.friend.service.exam.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.example.common.core.constants.Constants;
import org.example.common.core.domain.TableDataInfo;
import org.example.common.core.enums.ResultCode;
import org.example.common.core.utils.ThreadLocalUtil;
import org.example.common.security.exception.ServiceException;
import org.example.friend.domain.exam.dto.ExamQueryDTO;
import org.example.friend.domain.exam.dto.ExamRankDTO;
import org.example.friend.domain.exam.vo.ExamRankVO;
import org.example.friend.domain.exam.vo.ExamVO;
import org.example.friend.domain.user.vo.UserVO;
import org.example.friend.manager.ExamCacheManager;
import org.example.friend.manager.UserCacheManager;
import org.example.friend.mapper.exam.ExamMapper;
import org.example.friend.mapper.user.UserExamMapper;
import org.example.friend.service.exam.ExamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExamServiceImpl implements ExamService {
    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private ExamCacheManager examCacheManager;

    @Autowired
    private UserExamMapper userExamMapper;

    @Autowired
    private UserCacheManager userCacheManager;

    /**
     * 获取竞赛列表
     * @param examQueryDTO
     * @return
     */
    public List<ExamVO> list(ExamQueryDTO examQueryDTO) {
        //分页查询
        PageHelper.startPage(examQueryDTO.getPageNum(), examQueryDTO.getPageSize());

        //查询列表是否存在
        return examMapper.selectExamList(examQueryDTO);
    }

    @Override
    public TableDataInfo redisList(ExamQueryDTO examQueryDTO) {
        //从redis中获取缓存数据,返回值是获取的数据总量
        Long total = examCacheManager.getListSize(examQueryDTO.getType(),null);
        List<ExamVO> examVOList;
        //如果数据不存在
        if (total == null || total <= 0) {
            //先从数据库进行查询
            examVOList = list(examQueryDTO);
            //再把数据进行刷新，刷新的过程中也会将数据放入redis的缓存中
            examCacheManager.refreshCache(examQueryDTO.getType(),null);
            //此时redis缓存中没有数据，total就是不存在的所以需要重新获取
            total = new PageInfo<>(examVOList).getTotal();
        }else {
            //如果数据存在，直接从redis缓存中获取信息
            examVOList = examCacheManager.getExamVOList(examQueryDTO,null);
            //在getExamVOList的方法中会进行refreshCache方法，此时可能total在redis获取的是异常情况，之前的total就不准确
            //因为total是异常时，会重新进入数据库进行查询数据，并且更新缓存
            total = examCacheManager.getListSize(examQueryDTO.getType(),null);
        }

        if (CollectionUtil.isEmpty(examVOList)) {
            return TableDataInfo.empty();
        }
        //判断当前用户是否参加竞赛
        assembleExamVOList(examVOList);
        return TableDataInfo.success(examVOList,total);
    }

    @Override
    public String getFirstQuestion(Long examId) {
        //先查询redis缓存中是否存在信息
        checkAndRefresh(examId);
        return examCacheManager.getFirstQuestion(examId).toString();
    }

    private void checkAndRefresh(Long examId) {
        Long examQuestionListSize = examCacheManager.getExamQuestionListSize(examId);
        if (examQuestionListSize == null || examQuestionListSize <= 0) {
            //刷新缓存中的数据
            examCacheManager.refreshExamQuestionCache(examId);
        }
    }

    @Override
    public String preQuestion(Long examId, Long questionId) {
        //先查询redis缓存中是否存在信息
        checkAndRefresh(examId);
        //主题逻辑在ExamCacheManager
        return examCacheManager.preQuestion(examId,questionId).toString();
    }

    @Override
    public String nextQuestion(Long examId, Long questionId) {
        //先查询redis缓存中是否存在信息
        checkAndRefresh(examId);
        //主题逻辑在ExamCacheManager
        return examCacheManager.nextQuestion(examId,questionId).toString();
    }

    @Override
    public TableDataInfo rankList(ExamRankDTO examRankDTO) {
        //从redis中获取缓存数据,返回值是获取的数据总量
        Long total = examCacheManager.getRankListSize(examRankDTO.getExamId());
        List<ExamRankVO> examRankVOList;
        //如果数据不存在
        if (total == null || total <= 0) {
            //先从数据库进行查询
            //分页查询
            PageHelper.startPage(examRankDTO.getPageNum(), examRankDTO.getPageSize());
            examRankVOList = userExamMapper.selectExamRankList(examRankDTO.getExamId());

            //再把数据进行刷新，刷新的过程中也会将数据放入redis的缓存中
            examCacheManager.refreshExamRankCache(examRankDTO.getExamId());
            //此时redis缓存中没有数据，total就是不存在的所以需要重新获取
            total = new PageInfo<>(examRankVOList).getTotal();
        }else {
            //如果数据存在，直接从redis缓存中获取信息
            examRankVOList = examCacheManager.getExamRankList(examRankDTO);
            //在getExamVOList的方法中会进行refreshCache方法，此时可能total在redis获取的是异常情况，之前的total就不准确
            //因为total是异常时，会重新进入数据库进行查询数据，并且更新缓存
            total = examCacheManager.getRankListSize(examRankDTO.getExamId());
        }

        if (CollectionUtil.isEmpty(examRankVOList)) {
            return TableDataInfo.empty();
        }
        //处理从userId获取对应的nickName
        assembleExamRankVOList(examRankVOList);
        return TableDataInfo.success(examRankVOList,total);
    }

    private void assembleExamRankVOList(List<ExamRankVO> examRankVOList) {
        if (CollectionUtil.isEmpty(examRankVOList)) {
            return;
        }
        //从用户redis缓存中获取对应的用户nickname
        for (ExamRankVO examRankVO : examRankVOList) {
            UserVO userVO = userCacheManager.getUserById(examRankVO.getUserId());
            examRankVO.setNickName(userVO.getNickName());
        }

    }


    private void assembleExamVOList(List<ExamVO> examVOList) {
        //获取当前用户的Id
        Long userId = ThreadLocalUtil.get(Constants.USER_ID, Long.class);
        //在通过redis来获取该用户报名的竞赛id列表
        List<Long> userExamList = examCacheManager.getAllUserExamList(userId);

        //如果竞赛id列表为空，则为报名，不做处理
        if (CollectionUtil.isEmpty(userExamList)) {
            return;
        }

        //不为空则遍历竞赛列表，判断用户参加的竞赛id列表是否存在于前端的竞赛列表页面
        for (ExamVO examVO : examVOList) {
            if (userExamList.contains(examVO.getExamId())) {
                //竞赛列表页和用户参加的竞赛id相重合代表用户报名了该竞赛,将属性值改为true即可
                examVO.setEnter(true);
            }
        }
    }

}
