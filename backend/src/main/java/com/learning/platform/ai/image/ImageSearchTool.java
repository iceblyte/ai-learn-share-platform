package com.learning.platform.ai.image;

import java.util.List;

/**
 * 生图工具接口（工作流 imageCollect 节点的并发收集单元）。
 *
 * <p>与 {@link com.learning.platform.ai.ChatTool} 的区别：生图工具由图
 * 确定式调度，不暴露给模型做 function calling（避免模型滥用文生图）。</p>
 */
public interface ImageSearchTool {

    /** 源标识（进 AnswerImage.source，前端按来源渲染角标） */
    String source();

    /** 未配置 key / 资源不可用时的降级开关 */
    boolean isConfigured();

    /**
     * 按查询词收集图片。任何异常由收集器兜底（该源静默跳过），
     * 实现内部应自带 HTTP 超时，避免拖占收集线程。
     */
    List<AnswerImage> search(String query);
}
