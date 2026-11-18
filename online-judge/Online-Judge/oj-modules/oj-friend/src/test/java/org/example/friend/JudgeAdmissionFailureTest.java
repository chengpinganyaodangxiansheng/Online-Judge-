package org.example.friend;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import org.example.common.core.constants.Constants;
import org.example.common.core.utils.ThreadLocalUtil;
import org.example.common.mybatis.domain.JudgeTask;
import org.example.common.mybatis.mapper.JudgeTaskMapper;
import org.example.friend.domain.question.Question;
import org.example.friend.domain.user.dto.UserSubmitDTO;
import org.example.friend.mapper.user.JudgeAdmissionMapper;
import org.example.friend.service.user.impl.JudgeSubmissionServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.dao.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 模拟受理事务的异常分支，验证只有指定幂等约束冲突可以恢复为原任务。 */
class JudgeAdmissionFailureTest {
    /** 任务数据库访问对象，用于查询和条件推进可靠判题任务。 */
    private final JudgeTaskMapper tasks = mock(JudgeTaskMapper.class);

    /** 测试事务管理器替身，用于验证提交、回滚及冲突恢复的先后顺序。 */
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);

    /** 受理服务的测试实例，用于验证幂等冲突及事务失败的处理边界。 */
    private final JudgeSubmissionServiceImpl service = new JudgeSubmissionServiceImpl();

    /** 本测试使用的提交请求对象，保存幂等键、题目和原始源码。 */
    private final UserSubmitDTO request = new UserSubmitDTO();

    /** 为每个用例配置认证上下文、合法请求和数据库依赖替身，使测试只关注受理事务的失败边界。 */
    @BeforeEach
    void configure() {
        ThreadLocalUtil.set(Constants.USER_ID, 1L);
        request.setQuestionId(2L);
        request.setQuestionVersion(1L);
        request.setProgramType(0);
        request.setIdempotencyKey("admission-key");
        request.setUserCode("class Solution {}");
        JudgeAdmissionMapper admission = mock(JudgeAdmissionMapper.class);
        Question question = new Question();
        question.setQuestionId(2L);
        question.setQuestionVersion(1L);
        question.setDifficulty(1);
        question.setTimeLimit(1000L);
        question.setSpaceLimit(65536L);
        question.setMainFuc("main");
        question.setQuestionCase("[{\"input\":\"1\",\"output\":\"1\"}]");
        when(admission.lockQuestion(2L)).thenReturn(question);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ReflectionTestUtils.setField(service, "taskMapper", tasks);
        ReflectionTestUtils.setField(service, "admissionMapper", admission);
        ReflectionTestUtils.setField(
                service, "transactionTemplate", new TransactionTemplate(manager));
        ReflectionTestUtils.setField(service, "maxCodeBytes", 65536);
        ReflectionTestUtils.setField(service, "maxCases", 100);
    }

    /** 清除测试线程中的用户身份，避免认证信息污染后续用例。 */
    @AfterEach
    void clear() {
        ThreadLocalUtil.remove();
    }

    /** 注入Lock wait timed out及非幂等唯一约束异常，断言异常继续传播且创建事务回滚，不误报受理成功。 */
    @Test
    // 锁超时和其他唯一键冲突必须原样失败，不能被宽泛异常捕获伪装成受理成功。
    void lockTimeoutAndUnrelatedConstraintNeverBecomeIdempotentSuccess() {
        for (DataAccessException failure :
                Arrays.asList(
                        new CannotAcquireLockException("Lock wait timed out"),
                        new DuplicateKeyException("Duplicate other_constraint"))) {
            reset(tasks);
            when(tasks.insert(any(), anyInt())).thenThrow(failure);
            assertSame(
                    failure,
                    assertThrows(DataAccessException.class, () -> service.submit(request)));
            verify(tasks, times(2)).findRequest(1L, "admission-key");
        }
        verify(manager, times(2)).rollback(any());
        verify(manager, never()).commit(any());
    }

    /** 模拟用户幂等键竞争失败，验证先回滚创建事务，再读取原任务并返回原提交编号。 */
    @Test
    // 用调用顺序断言证明：先彻底回滚失败事务，再查询并返回另一个请求创建的任务。
    void duplicateRecoveryReadsOnlyAfterRollbackAndReturnsOriginal() {
        JudgeTask original = new JudgeTask();
        original.setSubmitId(3L);
        original.setState("RECEIVED");
        original.setRequestHash(
                DigestUtil.sha256Hex(
                        JSON.toJSONString(Arrays.asList(2L, null, 0, request.getUserCode()))));
        when(tasks.findRequest(1L, "admission-key")).thenReturn(null, null, original);
        when(tasks.insert(any(), anyInt()))
                .thenThrow(new DuplicateKeyException("Duplicate uk_judge_user_key"));
        assertEquals("3", service.submit(request).getSubmitId());
        org.mockito.InOrder order = inOrder(manager, tasks);
        order.verify(manager).rollback(any());
        order.verify(tasks).findRequest(1L, "admission-key");
        verify(manager, never()).commit(any());
    }

    /** 模拟幂等冲突后的恢复查询断连，确认不返回受理成功且不提交失败事务。 */
    @Test
    // 冲突后的恢复查询也断库时，无法证明已有任务，必须保留失败而不是返回成功。
    void failedRecoveryReadDoesNotReportAcceptance() {
        when(tasks.findRequest(1L, "admission-key"))
                .thenReturn(null, null)
                .thenThrow(new DataAccessResourceFailureException("Database disconnected"));
        when(tasks.insert(any(), anyInt()))
                .thenThrow(new DuplicateKeyException("Duplicate uk_judge_user_key"));
        assertThrows(DataAccessResourceFailureException.class, () -> service.submit(request));
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }
}
