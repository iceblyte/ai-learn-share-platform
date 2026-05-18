package com.learning.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequest {

    @NotBlank(message = "消息不能为空")
    private String message;

    private String route;

    private String pageTitle;
}
