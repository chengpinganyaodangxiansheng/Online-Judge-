package org.example.friend.service.user;

import org.example.api.domain.vo.JudgeTaskVO;
import org.example.friend.domain.user.dto.UserSubmitDTO;
import org.example.friend.domain.user.vo.JudgeDeliveryVO;
import java.util.List;

/** Friend 负责受理及公开查询，实际编译运行由 Judge 服务异步执行。 */
public interface JudgeSubmissionService {
    /** 校验当前用户的请求并原子保存任务、快照和派发事件；同键同内容返回原任务，同键不同内容拒绝，受理成功不表示执行完成。 */
    JudgeTaskVO submit(UserSubmitDTO request);

    /** 按提交编号查询当前登录用户自己的任务；不存在或归属不符时拒绝，公开结果移除隐藏用例诊断。 */
    JudgeTaskVO find(Long submitId);

    /** 管理员提供提交编号和原因后重派发系统失败任务，保留快照及历史并记审计；已完成任务和已定榜比赛不能通过此入口恢复。 */
    JudgeTaskVO retry(Long submitId, String reason);

    /** 管理员按给定数量上限读取系统失败任务，限制一次查询规模；普通用户无权调用。 */
    List<JudgeTaskVO> failed(int limit);

    /** 管理员按提交编号读取投递历史摘要，用于区分发送耗尽与执行故障；不返回事件完整载荷。 */
    List<JudgeDeliveryVO> deliveries(Long submitId);
}
