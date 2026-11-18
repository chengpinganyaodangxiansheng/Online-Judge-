package org.example.friend.domain.question;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.example.common.core.domain.BaseEntity;

@TableName("tb_question")
@Data
public class Question extends BaseEntity {
    /** 当前题目规则版本，用于受理校验及固定执行快照。 */
    private Long questionVersion;

    /** 提交对应的题目编号，用于读取题目及关联判题记录。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long questionId;

    /** 题目标题，用于列表和详情展示。 */
    private String title;

    /** 题目难度等级，判题通过时参与得分计算。 */
    private Integer difficulty;

    /** 单个运行用例的任务组处理器时间上限，单位为毫秒；不是排队期限。 */
    private Long timeLimit;

    /** 单个运行用例的任务组内存上限，单位为千字节，发送执行请求时转换为字节。 */
    private Long spaceLimit;

    /** 题目的公开题面内容，供用户阅读和编写解法。 */
    private String content;

    /** 题目内部用例的序列化数据，用于生成判题快照，不直接返回公开详情。 */
    private String questionCase;

    /** 题目提供的初始代码模板，用于初始化用户编辑器。 */
    private String defaultCode;

    /** 服务端维护的程序入口代码，受理提交时拼入源码形成执行快照。 */
    private String mainFuc;
}
