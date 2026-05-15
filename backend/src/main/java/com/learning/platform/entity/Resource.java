package com.learning.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("resource")
public class Resource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private Long categoryId;

    private Long publisherId;

    private String description;

    private String aiSummary;

    private String resourceType;

    private String externalUrl;

    private String coverImageUrl;

    private Integer viewCount;

    private Integer likeCount;

    private Integer favoriteCount;

    private Integer commentCount;

    private BigDecimal avgRating;

    private Integer ratingCount;

    private Integer hotScore;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;

    // Non-DB fields
    @TableField(exist = false)
    private Category category;

    @TableField(exist = false)
    private List<Tag> tags;

    @TableField(exist = false)
    private User publisher;
}
