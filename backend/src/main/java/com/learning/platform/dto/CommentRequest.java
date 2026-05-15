package com.learning.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentRequest {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论最多1000字")
    private String content;

    private Long parentId;
}
