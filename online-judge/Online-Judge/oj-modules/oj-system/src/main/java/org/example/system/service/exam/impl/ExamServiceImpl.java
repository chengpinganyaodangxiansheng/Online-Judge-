package org.example.system.service.exam.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import org.example.common.core.constants.Constants;
import org.example.common.core.enums.ResultCode;
import org.example.common.security.exception.ServiceException;
import org.example.system.manager.ExamCacheManager;
import org.example.system.domain.exam.Exam;
import org.example.system.domain.exam.ExamQuestion;
import org.example.system.domain.exam.dto.ExamAddDTO;
import org.example.system.domain.exam.dto.ExamEditDTO;
import org.example.system.domain.exam.dto.ExamQueryDTO;
import org.example.system.domain.exam.dto.ExamQuestAddDTO;
import org.example.system.domain.exam.vo.ExamDetailVO;
import org.example.system.domain.exam.vo.ExamVO;
import org.example.system.domain.question.Question;
import org.example.system.domain.question.vo.QuestionVO;
import org.example.system.mapper.exam.ExamMapper;
import org.example.system.mapper.exam.ExamQuestionMapper;
import org.example.system.mapper.question.QuestionMapper;
import org.example.system.service.exam.ExamService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ExamServiceImpl extends ServiceImpl<ExamQuestionMapper, ExamQuestion>
        implements ExamService {
    /** 比赛数据库访问对象，用于读取、锁定、发布比赛以及冻结题目。 */
    @Autowired private ExamMapper examMapper;

    /** 比赛与题目关联数据访问对象，用于校验和维护比赛题目集合。 */
    @Autowired private ExamQuestionMapper examQuestionMapper;

    /** 题目数据库访问对象，用于读取当前题目及其版本信息。 */
    @Autowired private QuestionMapper questionMapper;

    /** 比赛缓存管理器，在数据库操作完成后更新展示缓存。 */
    @Autowired private ExamCacheManager examCacheManager;

    /** 短事务执行模板，使一次业务操作中的多项数据库写入共同提交或回滚。 */
    @Autowired private TransactionTemplate transactionTemplate;

    /**
     * 获取竞赛列表
     * @param examQueryDTO
     * @return
     */
    public List<ExamVO> list(ExamQueryDTO examQueryDTO) {
        // 分页查询
        PageHelper.startPage(examQueryDTO.getPageNum(), examQueryDTO.getPageSize());

        // 查询列表是否存在
        return examMapper.selectExamList(examQueryDTO);
    }

    /**
     * 添加竞赛
     * @param examAddDTO
     * @return
     */
    public String add(ExamAddDTO examAddDTO) {
        // 首先需要校验数据的起始时间，结束时间，标题是否合法(检验的是前端传过来的数据)
        checkExamSaveParams(examAddDTO, null);

        // 对竞赛进行增加
        Exam exam = new Exam();
        BeanUtils.copyProperties(examAddDTO, exam);
        examMapper.insert(exam);
        return exam.getExamId().toString();
    }

    /**
     * 为竞赛中添加题目
     * @param examQuestAddDTO
     * @return
     */
    public boolean addQuestion(ExamQuestAddDTO examQuestAddDTO) {
        // 判断竞赛是否存在
        Exam exam = getExam(examQuestAddDTO.getExamId());
        // 竞赛已经发布了，就不能进行编辑操作
        if (Constants.TRUE.equals(exam.getStatus())) {
            throw new ServiceException(ResultCode.EXAM_IS_PUBLISH);
        }
        // 判断此时竞赛是否已经开始，如果开始了就不能进行操作
        checkExam(exam);
        // 判断竞赛中是否存在题目
        LinkedHashSet<Long> questionIdSet = examQuestAddDTO.getQuestionIdSet(); // 有序的不重复Set集合
        if (CollectionUtil.isEmpty(questionIdSet)) {
            return true;
        }
        // 判断题目是否为空
        List<Question> questions = questionMapper.selectBatchIds(questionIdSet);
        if (CollectionUtil.isEmpty(questions) || questions.size() < questionIdSet.size()) {
            throw new ServiceException(ResultCode.EXAM_QUESTION_NOT_EXISTS);
        }
        return saveExamQuestion(questionIdSet, exam);
    }

    /**
     * 获取竞赛详情
     * @param examId
     * @return
     */
    public ExamDetailVO detail(Long examId) {
        // 先获取竞赛实体类，再将属性赋值给VO
        ExamDetailVO examDetailVO = new ExamDetailVO();
        Exam exam = getExam(examId);
        BeanUtils.copyProperties(exam, examDetailVO);

        // 再通过竞赛的id获取对应的竞赛列表实体类,并且只获取题目的id，不获取其他的成员属性
        List<ExamQuestion> examQuestionList =
                examQuestionMapper.selectList(
                        new LambdaQueryWrapper<ExamQuestion>()
                                .select(ExamQuestion::getQuestionId)
                                .eq(ExamQuestion::getExamId, examId));

        // 判断这个竞赛中是否有题目
        if (CollectionUtil.isEmpty(examQuestionList)) {
            return examDetailVO;
        }

        // 通过steam流获取题目id列表
        List<Long> questionIdList =
                examQuestionList.stream().map(ExamQuestion::getQuestionId).toList();

        // 通过获取的竞赛id，查询具体的竞赛详情
        List<Question> questions =
                questionMapper.selectList(
                        new LambdaQueryWrapper<Question>()
                                .select(
                                        Question::getQuestionId,
                                        Question::getTitle,
                                        Question::getDifficulty)
                                .in(Question::getQuestionId, questionIdList));

        // 因为竞赛详情的VO类中存放的是题目的VO类，而不是实体类，所以需要进行转换
        List<QuestionVO> list = BeanUtil.copyToList(questions, QuestionVO.class);
        examDetailVO.setExamQuestionList(list);
        return examDetailVO;
    }

    /**
     * 编辑竞赛
     * @param examEditDTO
     * @return
     */
    public int edit(ExamEditDTO examEditDTO) {
        // 首先需要校验数据的起始时间，结束时间，标题是否合法
        checkExamSaveParams(examEditDTO, examEditDTO.getExamId());
        // 获取竞赛列表，保证列表存在
        Exam exam = getExam(examEditDTO.getExamId());
        // 竞赛已经发布了，就不能进行编辑操作
        if (Constants.TRUE.equals(exam.getStatus())) {
            throw new ServiceException(ResultCode.EXAM_IS_PUBLISH);
        }
        // 判断此时竞赛是否已经开始，如果开始了就不能进行操作
        checkExam(exam);
        // 通过传入的参数对实体类进行复制，再将实体类传给SQL语句进行修改
        exam.setTitle(examEditDTO.getTitle());
        exam.setStartTime(examEditDTO.getStartTime());
        exam.setEndTime(examEditDTO.getEndTime());
        return examMapper.updateById(exam);
    }

    /**
     * 删除竞赛中的题目
     * @param examId
     * @param questionId
     * @return
     */
    public int deleteQuestion(Long examId, Long questionId) {
        // 先获取exam实体类，也就是获取竞赛信息
        Exam exam = getExam(examId);
        // 竞赛已经发布了，就不能进行编辑操作
        if (Constants.TRUE.equals(exam.getStatus())) {
            throw new ServiceException(ResultCode.EXAM_IS_PUBLISH);
        }
        // 判断此时竞赛是否已经开赛了，开赛则不允许删除
        checkExam(exam);
        // 此时通过examId和questionId查询对应的题目，并进行删除
        return examQuestionMapper.delete(
                new LambdaQueryWrapper<ExamQuestion>()
                        .eq(ExamQuestion::getExamId, examId)
                        .eq(ExamQuestion::getQuestionId, questionId));
    }

    /**
     * 删除竞赛
     * @param examId
     * @return
     */
    public int delete(Long examId) {
        // 先获取竞赛信息,判断竞赛是否存在
        Exam exam = getExam(examId);

        // 竞赛已经发布了，就不能进行编辑操作
        if (Constants.TRUE.equals(exam.getStatus())) {
            throw new ServiceException(ResultCode.EXAM_IS_PUBLISH);
        }

        // 判断竞赛是否开赛
        checkExam(exam);

        // 删除竞赛对应的题目
        examQuestionMapper.delete(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId));
        // 删除竞赛
        return examMapper.deleteById(examId);
    }

    /**
     * 发布竞赛
     * @param examId
     * @return
     */
    public int publish(Long examId) {
        Exam published = transactionTemplate.execute(status -> publishInTransaction(examId));
        // 数据库提交并释放业务锁之后再访问 Redis。
        // 缓存失败不会回滚已经提交的发布结果；缓存是展示加速，数据库状态和冻结规则才是权威。
        examCacheManager.addCache(published);
        return 1;
    }

    /** 由 publish 的 TransactionTemplate 包裹；规则冻结或状态写入任一失败都回滚。 */
    private Exam publishInTransaction(Long examId) {
        // 获取竞赛详情
        Exam exam = examMapper.lockExam(examId);
        if (exam == null) {
            throw new ServiceException(ResultCode.EXAM_NOT_EXISTS);
        }
        // 判断是否开赛
        checkExam(exam);
        // 判断竞赛是否结束
        if (exam.getEndTime().isBefore(LocalDateTime.now())) {
            throw new ServiceException(ResultCode.EXAM_IS_FINISH);
        }
        // 判断竞赛是否有题目  SQL语句: select count(*) from exam_question where exam_id = #{examId};
        Long questionNumber =
                examQuestionMapper.selectCount(
                        new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamId, examId));
        // 计数缺失或不大于 0 都表示没有可发布题目，不能把空比赛标记为已发布。
        if (questionNumber == null || questionNumber <= 0) {
            throw new ServiceException(ResultCode.EXAM_QUESTION_NOT_EXISTS);
        }
        // 判断完成后，修改exam的发布状态，1是发布，0未发布
        exam.setStatus(Constants.TRUE);
        examMapper.freezeQuestions(examId);

        if (examMapper.updateById(exam) != 1) {
            throw new ServiceException(ResultCode.EXAM_NOT_EXISTS);
        }
        return exam;
    }

    /**
     * 撤销发布
     * @param examId
     * @return
     */
    public int cancelPublish(Long examId) {
        // 获取竞赛，判断竞赛是否存在
        Exam exam = getExam(examId);
        // 判断竞赛是否开赛
        checkExam(exam);
        // 判断竞赛是否结束
        if (exam.getEndTime().isBefore(LocalDateTime.now())) {
            throw new ServiceException(ResultCode.EXAM_IS_FINISH);
        }
        // 修改状态
        exam.setStatus(Constants.FALSE);
        // 竞赛取消发布，就需要删除之前存储在redis中的缓存 e:t:l  e:d:examId
        examCacheManager.deleteCache(exam);
        return examMapper.updateById(exam);
    }

    /**
     *在编辑竞赛时，对于前端传入的参数进行校验
     * @param examAddDTO
     * @param examId
     */
    private void checkExamSaveParams(ExamAddDTO examAddDTO, Long examId) {
        // 查询竞赛
        QueryWrapper<Exam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("title", examAddDTO.getTitle()).ne(examId != null, "exam_id", examId);
        // 这个语句代表当examId不为空时，也就是前端传入了examId.会将examId = 我们传入的id的Exam
        // 这是为了防止，在编辑信息时，未修改title，只修改了其他的成员属性，导致此时title重复导致报错。
        // 因为此时title并没有进行更改，并且此时查询数据库肯定存在这个title，那么就会进行报错，因为title是唯一的
        List<Exam> exams = examMapper.selectList(queryWrapper);

        // 判断竞赛是否存在
        if (CollectionUtil.isNotEmpty(exams)) {
            throw new ServiceException(ResultCode.FAILED_ALREADY_EXISTS);
        }

        // 竞赛起始时间不能早于当前时间
        if (examAddDTO.getStartTime().isBefore(LocalDateTime.now())) {
            throw new ServiceException(ResultCode.EXAM_START_TIME_BEFORE_CURRENT_TIME);
        }

        // 竞赛起始时间不能在结束时间之后
        if (examAddDTO.getStartTime().isAfter(examAddDTO.getEndTime())) {
            throw new ServiceException(ResultCode.EXAM_START_TIME_AFTER_END_TIME);
        }
    }

    /**
     * 判断竞赛是否开赛
     * @param exam
     */
    private void checkExam(Exam exam) {
        // 判断此时竞赛是否已经开始
        if (exam.getStartTime().isBefore(LocalDateTime.now())
                && exam.getEndTime().isAfter(LocalDateTime.now())) {
            throw new ServiceException(ResultCode.EXAM_STARTED);
        }
    }

    /**
     *在竞赛中添加多条题目的方法封装
     * @param questionIdSet
     * @param exam
     * @return
     */
    private boolean saveExamQuestion(LinkedHashSet<Long> questionIdSet, Exam exam) {
        List<ExamQuestion> examQuestionList = new ArrayList<>();
        int nums = 1;
        for (Long questionId : questionIdSet) {
            // 将前端勾选的题目进行添加到数据库中
            ExamQuestion examQuestion = new ExamQuestion();
            examQuestion.setQuestionId(questionId);
            examQuestion.setExamId(exam.getExamId());
            // 对题目顺序进行编号
            examQuestion.setQuestionOrder(nums++);
            examQuestionList.add(examQuestion);
            // examQuestionMapper.insert(examQuestion);  一次性插入一条数据
        }
        // 一次性插入多条数据
        return saveBatch(examQuestionList);
    }

    /**
     * 获取竞赛信息,并且判断竞赛是否存在
     * @param examId
     * @return
     */
    private Exam getExam(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new ServiceException(ResultCode.FAILED_NOT_EXISTS);
        }
        return exam;
    }
}
