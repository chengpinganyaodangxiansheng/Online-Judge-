package org.example.judge.service;

import org.example.api.domain.dto.JudgeRequestedDTO;
import org.example.api.domain.vo.UserQuestionResultVO;
import org.example.common.mybatis.domain.JudgeTask;

/** Judge 的短事务业务接口；调用方必须通过 Spring 代理调用以应用事务。 */
public interface JudgeTaskService {
    /** 校验消息的事件归属和派发代次，必要时持久化排队超时或租约恢复；返回可尝试领取，不授予执行权。 */
    boolean ready(JudgeRequestedDTO message);

    /** 取得本地资源后调用；以任务状态、代次和期限竞争执行权，同事务创建尝试历史，竞争失败返回空值，数据库异常向上传播。 */
    JudgeTask claim(JudgeRequestedDTO message);

    /** 使用领取时保存的令牌提交结果；有效结果、任务终态、比赛成绩和事件共同提交，失权或重复完成返回否，写入失败整体回滚。 */
    boolean finish(JudgeTask task, UserQuestionResultVO result);

    /** 保存系统失败原因并按预算决定退避重试或最终失败；第三个参数区分过期补偿和仍持有租约的执行者报错，两者使用不同条件校验。 */
    void fail(JudgeTask task, String reason, boolean expired);

    /** 按提交编号锁定权威任务状态，再判断排队超时、过期执行或到期重试；重派发的状态、代次与新事件必须在同一事务提交。 */
    void recover(Long submitId);
}
