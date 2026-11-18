package org.example.system.service.question.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import org.example.common.core.constants.Constants;
import org.example.common.core.domain.Result;
import org.example.common.core.enums.ResultCode;
import org.example.common.security.exception.ServiceException;
import org.example.system.domain.question.Question;
import org.example.system.domain.question.dto.QuestionAddDTO;
import org.example.system.domain.question.dto.QuestionEditDTO;
import org.example.system.domain.question.dto.QuestionQueryDTO;
import org.example.system.domain.question.es.QuestionES;
import org.example.system.domain.question.vo.QuestionDetailVO;
import org.example.system.domain.question.vo.QuestionVO;
import org.example.system.elasticsearch.QuestionRepository;
import org.example.system.manager.QuestionCacheManager;
import org.example.system.mapper.question.QuestionMapper;
import org.example.system.service.question.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class QuestionServiceImpl implements QuestionService {
    @Autowired
    private QuestionMapper questionMapper;

    //处理ES
    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionCacheManager questionCacheManager;

    @Override
    public  List<QuestionVO> list(QuestionQueryDTO questionQueryDTO) {
        //先获取分隔符;
        String excludeStr = questionQueryDTO.getExcludeIdStr();
        //判断excludesStr分隔符是否为空
        if (StrUtil.isNotEmpty(excludeStr)) {
            //不为空，则通过分隔符获取对应的集合
            String[] excludeIdArr = excludeStr.split(Constants.SPLIT_SEM);
            //再通过Stream流获取Set集合
            Set<Long> excludeSet = Arrays.stream(excludeIdArr)
                    .map(Long::valueOf)
                    .collect(Collectors.toSet());
            //再将值传给questionQueryDTO,再后续进行查询时会使用到这个参数
            questionQueryDTO.setExcludeIdSet(excludeSet);
        }

        //进行分页
        PageHelper.startPage(questionQueryDTO.getPageNum(),questionQueryDTO.getPageSize());
        return  questionMapper.selectQuestionList(questionQueryDTO);
    }

    @Override
    public boolean add(QuestionAddDTO questionAddDTO) {
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getTitle,questionAddDTO.getTitle()));
        if (!CollectionUtil.isEmpty(questions)) {
            throw new ServiceException(ResultCode.FAILED_ALREADY_EXISTS);
        }
        Question question = new Question();
        BeanUtil.copyProperties(questionAddDTO,question);
        int insert =  questionMapper.insert(question);
        if (insert <= 0) return false;
        //返回ES的数据类型，在进行插入数据到ES中
        QuestionES questionES = new QuestionES();
        BeanUtil.copyProperties(question,questionES);
        questionRepository.save(questionES);
        //对redis缓存中也要进行处理
        questionCacheManager.addCache(question.getQuestionId());
        return true;
    }

    @Override
    public Result<QuestionDetailVO> detail(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new ServiceException(ResultCode.FAILED_NOT_EXISTS);
        }
        QuestionDetailVO questionDetailVO = new QuestionDetailVO();
        BeanUtil.copyProperties(question,questionDetailVO);
        return Result.success(questionDetailVO);
    }

    @Override
    public int edit(QuestionEditDTO questionEditDTO) {
        Question question = questionMapper.selectById(questionEditDTO.getQuestionId());
        if (question == null) {
            throw new ServiceException(ResultCode.FAILED_NOT_EXISTS);
        }

        question.setTitle(questionEditDTO.getTitle());
        question.setContent(questionEditDTO.getContent());
        question.setDifficulty(questionEditDTO.getDifficulty());
        question.setQuestionCase(questionEditDTO.getQuestionCase());
        question.setMainFuc(questionEditDTO.getMainFuc());
        question.setDefaultCode(questionEditDTO.getDefaultCode());
        question.setTimeLimit(questionEditDTO.getTimeLimit());
        question.setSpaceLimit(questionEditDTO.getSpaceLimit());

        //在数据库进行编辑完成，也需要把数据传递给ES，因为C端用户看题目是通过ES来进行的(如果不进行刷新缓存就没办法看到新的数据)
        QuestionES questionES = new QuestionES();
        BeanUtil.copyProperties(question,questionES);
        questionRepository.save(questionES);
        return questionMapper.updateById(question);
    }

    @Override
    public int delete(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new ServiceException(ResultCode.FAILED_NOT_EXISTS);
        }
        //同时删除ES的数据
        questionRepository.deleteById(questionId);
        //同时删除redis中的缓存数据
        questionCacheManager.deleteCache(questionId);
        return questionMapper.deleteById(questionId);

    }
}
