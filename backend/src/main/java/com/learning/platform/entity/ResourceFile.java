package com.learning.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resource_file")
public class ResourceFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resourceId;

    private String fileName;

    private String fileUrl;

    private Long fileSize;

    private String fileType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime uploadedAt;
}
