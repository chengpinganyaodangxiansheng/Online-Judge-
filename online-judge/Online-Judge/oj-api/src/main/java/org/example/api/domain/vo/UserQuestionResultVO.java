package org.example.api.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.example.api.domain.UserExeResult;

import java.util.List;

@Data
public class UserQuestionResultVO {
    /** 业务判题结果：AC、WA、CE、RE、TLE、MLE、OLE；系统故障由任务状态单独表达。 */
    private String verdict;

    /** 保存所有已确认阶段的资源原因，避免单一展示码丢失并发超限证据。 */
    private List<JudgeStageEvidenceVO> stageEvidence;

    // 是够通过标识
    private Integer pass; // 0  未通过  1 通过

    /** 执行失败或超限的诊断摘要，向调用方解释判定原因。 */
    private String exeMessage; // 异常信息

    /** 内部逐用例诊断列表，包含输入、期望输出和实际输出；公开任务查询应移除此字段，避免泄露隐藏用例。 */
    private List<UserExeResult> userExeResultList;

    /** 本次判题得分，供服务端成绩聚合使用，不作为公开接口字段输出。 */
    @JsonIgnore private Integer score;
}
