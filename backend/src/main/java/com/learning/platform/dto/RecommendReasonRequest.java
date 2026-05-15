package com.learning.platform.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class RecommendReasonRequest {

    @NotEmpty(message = "资源ID列表不能为空")
    private List<Long> resourceIds;
}
