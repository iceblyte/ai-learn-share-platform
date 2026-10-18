package com.learning.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiChatRequest {

    @NotBlank(message = "消息不能为空")
    private String message;

    private String route;

    private String pageTitle;

    /** 页面上下文类型：RESOURCE / NONE（空与未知值按 NONE 处理） */
    private String pageContextType;

    /** 页面上下文关联的资源 ID（服务端二次校验并富化上下文） */
    private Long pageContextId;

    /** 用户划选的文本片段（防滥用限长） */
    @Size(max = 500, message = "划选文本不能超过500字")
    private String selectedText;

    /** 智能搜索开关：true 时暴露 web_search 联网搜索工具并在回答后回传来源 */
    private Boolean webSearch;
}
