package com.learning.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内通知：被关注/被点赞/被收藏/被评论/被回复/被 @ 等 */
@Data
@TableName("notification")
public class Notification {

    public static final String TYPE_FOLLOW = "FOLLOW";
    public static final String TYPE_LIKE_RESOURCE = "LIKE_RESOURCE";
    public static final String TYPE_LIKE_COMMENT = "LIKE_COMMENT";
    public static final String TYPE_FAVORITE = "FAVORITE";
    public static final String TYPE_COMMENT = "COMMENT";
    public static final String TYPE_REPLY = "REPLY";
    public static final String TYPE_MENTION = "MENTION";
    public static final String TYPE_AI_SUMMARY = "AI_SUMMARY";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 */
    private Long userId;

    /** 类型：FOLLOW / LIKE_RESOURCE / LIKE_COMMENT / FAVORITE / COMMENT / REPLY / MENTION / AI_SUMMARY */
    private String type;

    /** 触发者（AI 助手等系统触发的通知可为空） */
    private Long actorId;

    private Long resourceId;

    private Long commentId;

    /** 通知摘要文本（截断后的评论内容等） */
    private String content;

    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
