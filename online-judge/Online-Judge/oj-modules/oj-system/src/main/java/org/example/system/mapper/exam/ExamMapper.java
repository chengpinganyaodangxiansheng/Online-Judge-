package org.example.system.mapper.exam;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.system.domain.exam.Exam;
import org.example.system.domain.exam.dto.ExamQueryDTO;
import org.example.system.domain.exam.vo.ExamVO;

import java.util.List;

@Mapper
public interface ExamMapper extends BaseMapper<Exam> {
    /** 发布事务锁定比赛，冻结规则和发布状态在同一事务中完成。 */
    @org.apache.ibatis.annotations.Select("SELECT * FROM tb_exam WHERE exam_id=#{id} FOR UPDATE")
    Exam lockExam(Long id);

    /** 固定完整判题规则及展示字段，后续比赛详情和提交均读取该快照。 */
    @org.apache.ibatis.annotations.Update(
            "UPDATE tb_exam_question eq JOIN tb_question q ON q.question_id=eq.question_id "
                    + "SET eq.judge_question_json=JSON_OBJECT('questionId',q.question_id,'questionVersion',q.question_version,"
                    + "'title',q.title,'content',q.content,'defaultCode',q.default_code,'difficulty',q.difficulty,"
                    + "'timeLimit',q.time_limit,'spaceLimit',q.space_limit,'questionCase',q.question_case,'mainFuc',q.main_fuc) WHERE eq.exam_id=#{id}")
    int freezeQuestions(Long id);

    /** 按比赛查询条件读取列表数据，返回公开比赛摘要，分页由调用方控制。 */
    List<ExamVO> selectExamList(ExamQueryDTO examQueryDTO);
}
