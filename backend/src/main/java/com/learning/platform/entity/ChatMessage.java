package com.learning.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private String role;

    private String content;

    /** 配图快照（JSON 数组：[{"source","url","alt"}]，工作流 imageCollect 产出） */
    private String imagesJson;

    /** 联网搜索来源快照（JSON 数组：[{"title","url","snippet"}]，智能搜索开启时） */
    private String sourcesJson;

    private String contextJson;

    private Integer tokensUsed;

    private Integer latencyMs;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
