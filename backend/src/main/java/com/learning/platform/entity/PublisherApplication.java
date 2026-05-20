package com.learning.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("publisher_application")
public class PublisherApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String reason;

    private String status;

    private String rejectReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
