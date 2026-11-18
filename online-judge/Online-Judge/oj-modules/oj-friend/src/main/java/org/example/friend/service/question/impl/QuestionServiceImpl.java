package org.example.friend.service.question.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import org.example.common.core.constants.Constants;
import org.example.common.core.domain.TableDataInfo;
import org.example.friend.domain.question.Question;
import org.example.friend.domain.question.dto.QuestionQueryDTO;
import org.example.friend.domain.question.es.QuestionES;
import org.example.friend.domain.question.vo.QuestionDetailVO;
import org.example.friend.domain.question.vo.QuestionVO;
import org.example.friend.elasticsearch.QuestionRepository;
import org.example.friend.manager.QuestionCacheManager;
import org.example.friend.mapper.question.QuestionMapper;
import org.example.friend.mapper.user.UserSubmitMapper;
import org.example.friend.service.question.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuestionServiceImpl implements QuestionService {
    /** 题目数据库访问对象，用于读取当前题目及其版本信息。 */
    @Autowired private QuestionMapper questionMapper;

    /** 题目搜索索引访问对象，用于按关键词和难度查询题目列表。 */
    @Autowired private QuestionRepository questionRepository;

    /** 题目导航和热门题目缓存管理器，用于查询或刷新缓存。 */
    @Autowired private QuestionCacheManager questionCacheManager;

    /** 用户提交历史访问对象，读取历史判题记录及热门题目统计。 */
    @Autowired private UserSubmitMapper userSubmitMapper;

    /** 受理校验的数据访问对象，用于锁定业务行、读取冻结题目及检查报名关系。 */
    @Autowired private org.example.friend.mapper.user.JudgeAdmissionMapper admissionMapper;

    /** 按关键词和难度分页查询搜索索引，索引为空时先从数据库装载题目；返回公开列表和总数。 */
    @Override
    public TableDataInfo list(QuestionQueryDTO queryDTO) {
        // 先判断此时ElasticSearch中是否存在数据
        long count = questionRepository.count();
        if (count <= 0) {
            // 从数据库中获取数据，并刷新ES的缓存(也就是让数据库的数据和ES进行同步)
            refreshQuestion();
        }

        Integer difficulty = queryDTO.getDifficulty();
        String keyword = queryDTO.getKeyword();
        // 构造pageable，使用PageRequest.of()进行，是因为，在findAll方法中会使用到分页
        // Sort.by是规定分页后的排序方式,此处是按照创建时间进行降序排序
        Pageable pageable =
                PageRequest.of(
                        queryDTO.getPageNum() - 1,
                        queryDTO.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createTime"));
        Page<QuestionES> questionES;
        if (difficulty == null && StrUtil.isEmpty(keyword)) {
            questionES = questionRepository.findAll(pageable);
        } else if (StrUtil.isEmpty(keyword)) {
            // 此时difficulty is not NULL
            questionES = questionRepository.findQuestionByDifficulty(difficulty, pageable);
        } else if (difficulty == null) {
            // keyword is not NULL
            questionES = questionRepository.findByTitleOrContent(keyword, keyword, pageable);
        } else {
            // 此时都不为NULL
            questionES =
                    questionRepository.findByTitleOrContentAndDifficulty(
                            keyword, keyword, difficulty, pageable);
        }

        // 获取当前Page的总数，如果是0，就是没有数据,getTotalElements是获取全部元素
        long totalElements = questionES.getTotalElements();
        if (totalElements <= 0) {
            TableDataInfo.empty();
        }

        // 返回的数据是VO类型需要进行类型转换    questionES.getContent()获取List<questionES> .
        // 此处定义的questionES是Page<QuestionES>,是分页类型
        List<QuestionVO> questionVOS =
                BeanUtil.copyToList(questionES.getContent(), QuestionVO.class);
        return TableDataInfo.success(questionVOS, totalElements);
    }

    /** 不传比赛编号时委托详情重载读取当前练习题目，避免维护两套详情转换逻辑。 */
    @Override
    public QuestionDetailVO detail(Long questionId) {
        return detail(questionId, null);
    }

    /** 练习读数据库当前版本、比赛读冻结快照；详情查询不依赖 ES 可用性。 */
    @Override
    public QuestionDetailVO detail(Long questionId, Long examId) {
        Question question;
        if (examId == null) {
            question = questionMapper.selectById(questionId);
        } else {
            Long userId =
                    org.example.common.core.utils.ThreadLocalUtil.get(
                            Constants.USER_ID, Long.class);
            // 用户身份缺失或报名记录数不为 1，任一情况都不返回比赛题目；未登录时短路，不再查询报名表。
            if (userId == null || admissionMapper.registered(examId, userId) != 1) {
                throw new org.example.common.security.exception.ServiceException(
                        org.example.common.core.enums.ResultCode.FAILED_UNAUTHORIZED);
            }
            question =
                    com.alibaba.fastjson2.JSON.parseObject(
                            admissionMapper.frozenQuestion(examId, questionId), Question.class);
        }
        if (question == null) {
            throw new org.example.common.security.exception.ServiceException(
                    org.example.common.core.enums.ResultCode.FAILED_NOT_EXISTS);
        }
        // 映射到公开详情字段，不返回内部 mainFuc 或隐藏 questionCase。
        return BeanUtil.copyProperties(question, QuestionDetailVO.class);
    }

    /** 检查导航缓存并在需要时刷新，再返回当前题目的上一题编号。 */
    @Override
    public String preQuestion(Long questionId) {
        // 先从redis缓存中获取题目的列表
        Long listSize = questionCacheManager.getListSize();
        if (listSize <= 0 || listSize == null) {
            // 不存在，则需要通过刷新缓存的方式从数据库中获取信息，并存入缓存中
            questionCacheManager.refreshCache();
        }
        // 获取当前题目在redis缓存中的下标
        return questionCacheManager.preQuestion(questionId).toString();
    }

    /** 检查导航缓存并在需要时刷新，再返回当前题目的下一题编号。 */
    @Override
    public String nextQuestion(Long questionId) {
        // 先从redis缓存中获取题目的列表
        Long listSize = questionCacheManager.getListSize();
        if (listSize <= 0 || listSize == null) {
            // 不存在，则需要通过刷新缓存的方式从数据库中获取信息，并存入缓存中
            questionCacheManager.refreshCache();
        }
        // 获取当前题目在redis缓存中的下标
        return questionCacheManager.nextQuestion(questionId).toString();
    }

    /** 优先读取热门题目编号缓存，缺失时从提交历史统计并刷新，再组装展示列表。 */
    @Override
    public List<QuestionVO> hotList() {
        // 先从redis缓存中查询是否存在热点题目Id列表
        Long total = questionCacheManager.getHotListSize();
        List<Long> questionHotIds;
        if (total <= 0 || total == null) {
            // 缓存不存在热点题目Id列表，从数据库中进行获取，并且进行缓存更新
            PageHelper.startPage(
                    Constants.HOST_QUESTION_LIST_START, Constants.HOST_QUESTION_LIST_END);
            questionHotIds = userSubmitMapper.selectHostQuestionList();
            questionCacheManager.refreshHotQuestionList(questionHotIds);
        } else {
            questionHotIds = questionCacheManager.getHotList();
        }
        return assembleQuestionVOList(questionHotIds);
    }

    /** 按热门编号顺序查询题目详情并提取展示标题；空编号列表返回空集合。 */
    private List<QuestionVO> assembleQuestionVOList(List<Long> questionHotIds) {
        // 为空返回空列表
        if (CollectionUtil.isEmpty(questionHotIds)) {
            return new ArrayList<>();
        }

        // 不为空，就使用Id来获取题目
        List<QuestionVO> questionVOList = new ArrayList<>();
        for (Long questionId : questionHotIds) {
            QuestionVO questionVO = new QuestionVO();
            // 通过Id获取题目详情信息 => 目的是获取题目
            QuestionDetailVO questionDetailVO = detail(questionId);
            questionVO.setTitle(questionDetailVO.getTitle());
            questionVOList.add(questionVO);
        }
        return questionVOList;
    }

    /** 从数据库读取题目并转换为搜索文档，批量写入索引；没有题目时直接结束。 */
    private void refreshQuestion() {
        // 从数据库查询题目的全部信息
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<>());
        if (CollectionUtil.isEmpty(questions)) {
            // 没有数据直接返回
            return;
        }
        // 如果有数据，让ES进行同步,因为QuestionRepository中存放的是QuestionES，所以还需要进行类型转化
        List<QuestionES> questionES = BeanUtil.copyToList(questions, QuestionES.class);
        questionRepository.saveAll(questionES);
    }
}
