package org.example.job.domain.message;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.example.common.core.domain.BaseEntity;

@TableName("tb_message_text")
@Data
public class MessageText extends BaseEntity {

    @TableId(value = "TEXT_ID", type = IdType.ASSIGN_ID)//MyBatis-Plus 在插入成功后会将数据库生成的主键值回填到对应的实体对象中。
    private Long textId;

    private String messageTitle;

    private String messageContent;
}