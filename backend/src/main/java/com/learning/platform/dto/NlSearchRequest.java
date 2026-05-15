package com.learning.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NlSearchRequest {

    @NotBlank(message = "查询内容不能为空")
    private String query;
}
