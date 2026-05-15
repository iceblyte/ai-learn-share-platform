package com.learning.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuditRequest {

    @NotBlank(message = "审核动作不能为空")
    private String action; // APPROVE or REJECT

    private String reason;
}
