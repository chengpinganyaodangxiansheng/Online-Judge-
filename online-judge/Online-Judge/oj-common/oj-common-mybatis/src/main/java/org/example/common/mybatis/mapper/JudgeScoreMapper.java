package org.example.common.mybatis.mapper;

import org.apache.ibatis.annotations.*;

/** 完成事务中的比赛计分；固定按比赛行、任务行、用户总分、单题成绩的顺序加锁。 */
@Mapper
public interface JudgeScoreMapper {
    // 完成事务先锁比赛，再锁任务、用户成绩和单题成绩；行锁须在上层事务中使用。
    // 最高分和总分不能拆到任务终态之后另行提交，否则可能出现已完成但成绩未更新。
    /** 与定榜排他锁协调，多个正常判题完成可以同时持有比赛共享锁。 */
    @Select("SELECT settlement_state FROM tb_exam WHERE exam_id=#{id} FOR SHARE")
    String lockExam(Long id);

    /** 同一用户的不同题完成也串行计分，防止总分更新丢失。 */
    @Select(
            "SELECT COALESCE(score,0) FROM tb_user_exam WHERE exam_id=#{exam} AND user_id=#{user} FOR UPDATE")
    Integer lockScore(@Param("exam") Long exam, @Param("user") Long user);

    /**
     * 每题只保留最高分；同分追溯到最早受理、再按最小提交编号选出的提交。
     * 没有原记录时直接插入；已有记录时，以下任一情况替换追溯的 submit_id：
     * 新分数更高；同分但受理更早；同分且受理时间相同但提交编号更小。
     * 更低分或同分更晚提交不替换；accepted_at 随更高分或同分更早提交更新。
     * 最后用 GREATEST 保存新旧分数最大值，失败重提不能降低已取得的单题成绩。
     * best_score 放在最后赋值，使前面的追溯字段比较使用更新前的最高分。
     */
    @Insert(
            "INSERT INTO tb_exam_question_score(exam_id,user_id,question_id,best_score,submit_id,accepted_at) "
                    + "SELECT exam_id,user_id,question_id,#{score},submit_id,accepted_at FROM tb_judge_task WHERE submit_id=#{id} "
                    + "ON DUPLICATE KEY UPDATE submit_id=IF(VALUES(best_score)>best_score OR (VALUES(best_score)=best_score "
                    + "AND (VALUES(accepted_at)<tb_exam_question_score.accepted_at OR (VALUES(accepted_at)=tb_exam_question_score.accepted_at AND VALUES(submit_id)<tb_exam_question_score.submit_id))),VALUES(submit_id),tb_exam_question_score.submit_id),"
                    + "accepted_at=IF(VALUES(best_score)>best_score OR (VALUES(best_score)=best_score AND VALUES(accepted_at)<tb_exam_question_score.accepted_at),VALUES(accepted_at),tb_exam_question_score.accepted_at),"
                    + "best_score=GREATEST(best_score,VALUES(best_score))")
    int aggregate(@Param("id") Long id, @Param("score") int score);

    /** 从单题最高分重算绝对总分，重复提交相同题目不会反复加分。 */
    @Select(
            "SELECT COALESCE(SUM(best_score),0) FROM tb_exam_question_score WHERE exam_id=#{exam} AND user_id=#{user}")
    long total(@Param("exam") Long exam, @Param("user") Long user);

    /** 在持有该用户比赛成绩行锁的事务内写入重算的绝对总分，不做增量累加。 */
    @Update("UPDATE tb_user_exam SET score=#{score} WHERE exam_id=#{exam} AND user_id=#{user}")
    int updateTotal(@Param("exam") Long exam, @Param("user") Long user, @Param("score") long score);
}
