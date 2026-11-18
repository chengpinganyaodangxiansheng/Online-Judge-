package org.example.friend.mapper.user;

import org.apache.ibatis.annotations.*;
import org.example.friend.domain.question.Question;

/** 提交资格及规则读取；共享锁必须处于受理事务内，协调题目编辑和比赛状态变更。 */
@Mapper
public interface JudgeAdmissionMapper {
    // 受理校验读取数据库权威状态；比赛和题目共享锁与发布、关闭等写操作协调。
    // 已受理提交应先走幂等重放，不因当前比赛截止或题目更新而重新创建快照。
    /** 比赛使用发布时的固定题目快照，不能回退读取当前题目代替缺失的快照。 */
    @Select("SELECT judge_question_json FROM tb_exam_question WHERE exam_id=#{exam} AND question_id=#{question}")
    String frozenQuestion(@Param("exam") Long exam, @Param("question") Long question);

    /** 练习受理期间阻止并发编辑，保证校验版本与生成快照使用同一份数据。 */
    @Select("SELECT * FROM tb_question WHERE question_id=#{id} FOR SHARE")
    Question lockQuestion(Long id);

    /**
     * 检查比赛是否允许受理新提交，并在当前事务内持有比赛行共享锁。
     * status=1：比赛已发布；settlement_state=OPEN：尚未进入结算或定榜阶段；
     * start_time<=NOW(6)：已经开赛；end_time>NOW(6)：尚未截止，截止瞬间不再受理。
     * 所有条件必须同时满足，以数据库时间为准，不能只信前端倒计时。
     * FOR SHARE 成功锁住比赛行后，直到受理事务结束，其他事务不能修改或删除该行；
     * 多个提交的共享锁彼此兼容，不负责提交去重，去重由任务表唯一约束保证。
     * 仅遇到不兼容的锁才可能等待；不能把正常发布与正常参赛描述成必然的竞争场景。
     * 当前正常业务限制开赛后的编辑/撤销，此锁不能据此被称为已实现完整结算协调。
     * 需求中的关闭受理/结算写锁是后续协调边界，须由对应实际事务接入后验证。
     * 行锁也不会阻止时间流逝：这里检查查询时的时间条件，不保证事务提交时仍未截止。
     * @param id 比赛编号
     * @return 满足条件的比赛编号；不存在或任一条件不满足时返回 null
     */
    @Select(
            "SELECT exam_id FROM tb_exam WHERE exam_id=#{id} AND status=1 AND settlement_state='OPEN' AND start_time<=NOW(6) AND end_time>NOW(6) FOR SHARE")
    Long lockOpenExam(Long id);

    /**
     * 查询当前用户在指定比赛中的报名记录数，判断用户是否具备参赛资格。
     * tb_user_exam 保存用户与比赛的报名关系；没有报名的用户不能直接调用接口参赛。
     * @param exam 比赛编号，绑定 SQL 中的 #{exam}
     * @param user 当前登录用户编号，绑定 SQL 中的 #{user}，不使用请求体指定的身份
     * @return 匹配的报名记录数；业务层仅接受 1，0 表示未报名，多条异常记录也不放行
     */
    @Select("SELECT COUNT(*) FROM tb_user_exam WHERE exam_id=#{exam} AND user_id=#{user}")
    int registered(@Param("exam") Long exam, @Param("user") Long user);

    /**
     * 查询题目与比赛的关联记录数，确认提交的题目属于当前比赛。
     * tb_exam_question 保存比赛题目关系，防止篡改 questionId 将其他题目的成绩计入本场比赛。
     * 本方法只检查题目归属；是否报名、是否开赛及是否截止由其他检查负责。
     * @param exam 比赛编号
     * @param question 本次提交的题目编号
     * @return 匹配的关联记录数；业务层仅接受 1，0 表示不属于本场，多条异常记录也不放行
     */
    @Select(
            "SELECT COUNT(*) FROM tb_exam_question WHERE exam_id=#{exam} AND question_id=#{question}")
    int containsQuestion(@Param("exam") Long exam, @Param("question") Long question);
}
