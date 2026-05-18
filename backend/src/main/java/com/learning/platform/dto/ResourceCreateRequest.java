package com.learning.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ResourceCreateRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最多200字")
    private String title;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @Size(max = 10, message = "标签最多10个")
    private List<String> tags;

    @NotBlank(message = "描述不能为空")
    private String description;

    @NotBlank(message = "资源类型不能为空")
    private String resourceType;

    private String externalUrl;

    private String coverImageUrl;
}
