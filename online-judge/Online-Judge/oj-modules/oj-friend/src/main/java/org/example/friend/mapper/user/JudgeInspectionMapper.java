package org.example.friend.mapper.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.common.mybatis.domain.JudgeTask;
import org.example.friend.domain.user.vo.JudgeDeliveryVO;
import java.util.List;

/** 管理排查只读查询，调用前由业务层检查管理员身份，并限制结果数量。 */
@Mapper
public interface JudgeInspectionMapper {
    // 这里只负责受限查询和摘要映射，管理员鉴权由调用服务执行，不能直接免鉴权暴露。
    // 投递排查不返回完整消息载荷，避免将源码或内部判题数据带入管理响应。
    /** 只取系统失败任务，按编号倒序返回有限条目；WA/CE 等有效判题结果不是系统失败。 */
    @Select(
            "SELECT * FROM tb_judge_task WHERE state='SYSTEM_FAILED' ORDER BY submit_id DESC LIMIT #{limit}")
    List<JudgeTask> failed(int limit);

    /** 只返回该任务的 JudgeRequested 投递记录，最多 100 条，不混入排名事件，也不暴露完整 payload。 */
    @Select(
            "SELECT event_id,dispatch_seq,state,send_count,failure_reason,next_retry_at FROM tb_judge_outbox "
                    + "WHERE submit_id=#{id} AND event_type='JudgeRequested' ORDER BY dispatch_seq DESC LIMIT 100")
    List<JudgeDeliveryVO> deliveries(Long id);
}
