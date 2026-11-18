package org.example.system;

import org.example.common.security.exception.ServiceException;
import org.example.system.domain.exam.Exam;
import org.example.system.manager.ExamCacheManager;
import org.example.system.mapper.exam.ExamMapper;
import org.example.system.mapper.exam.ExamQuestionMapper;
import org.example.system.service.exam.impl.ExamServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 发布事务顺序测试，避免 Redis 网络操作占用数据库锁或暴露未提交规则。 */
class ExamPublishTest {
    /** 验证题目冻结和比赛发布先在数据库事务中提交，再更新缓存，避免缓存先于权威状态生效。 */
    @Test
    // 用 InOrder 检查冻结、发布、事务提交、缓存写入的严格先后关系。
    void freezeAndPublishCommitBeforeCacheWrite() {
        ExamMapper exams = mock(ExamMapper.class);
        ExamQuestionMapper questions = mock(ExamQuestionMapper.class);
        ExamCacheManager cache = mock(ExamCacheManager.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ExamServiceImpl service = new ExamServiceImpl();
        ReflectionTestUtils.setField(service, "examMapper", exams);
        ReflectionTestUtils.setField(service, "examQuestionMapper", questions);
        ReflectionTestUtils.setField(service, "examCacheManager", cache);
        ReflectionTestUtils.setField(
                service, "transactionTemplate", new TransactionTemplate(manager));
        Exam exam = new Exam();
        exam.setExamId(1L);
        exam.setStartTime(LocalDateTime.now().plusHours(1));
        exam.setEndTime(LocalDateTime.now().plusHours(2));
        when(exams.lockExam(1L)).thenReturn(exam);
        when(questions.selectCount(any())).thenReturn(1L);
        when(exams.updateById(exam)).thenReturn(1);
        assertEquals(1, service.publish(1L));
        InOrder order = inOrder(exams, manager, cache);
        order.verify(exams).freezeQuestions(1L);
        order.verify(exams).updateById(exam);
        order.verify(manager).commit(any());
        order.verify(cache).addCache(exam);
    }

    /** 模拟比赛不存在，确认发布事务回滚且不调用缓存更新。 */
    @Test
    // 比赛不存在时必须回滚，且不能调用缓存组件。
    void missingExamRollsBackWithoutCacheCall() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ExamServiceImpl service = new ExamServiceImpl();
        ExamCacheManager cache = mock(ExamCacheManager.class);
        ReflectionTestUtils.setField(service, "examMapper", mock(ExamMapper.class));
        ReflectionTestUtils.setField(service, "examCacheManager", cache);
        ReflectionTestUtils.setField(
                service, "transactionTemplate", new TransactionTemplate(manager));
        assertThrows(ServiceException.class, () -> service.publish(1L));
        verify(manager).rollback(any());
        verifyNoInteractions(cache);
    }
}
