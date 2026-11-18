package org.example.system.domain.exam;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.example.common.core.domain.BaseEntity;

@Data
@TableName("tb_exam_question")
public class ExamQuestion extends BaseEntity {
    @TableId(value = "EXAM_QUESTION_ID", type = IdType.ASSIGN_ID)
    private Long examQuestionId;

    private Long examId;

    private Long questionId;

    private Integer questionOrder;
}
