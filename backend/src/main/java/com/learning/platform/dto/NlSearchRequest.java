package com.learning.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class NlSearchRequest {

    @NotBlank(message = "查询内容不能为空")
    private String query;

    /** 页面上手动选择的排序（rating/latest/hot），非空时覆盖 AI 解析结果 */
    private String sortBy;

    /** 页面上手动选择的分类，非空时覆盖 AI 解析结果 */
    private Long categoryId;

    /** 页面上手动选择的标签，非空时覆盖 AI 解析结果 */
    private List<String> tags;

    /** 页面上手动选择的最低评分，非空时覆盖 AI 解析结果 */
    private Double minRating;
}
