package com.learning.platform.ai.image;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生图工具二：unDraw 插图映射。
 *
 * <p>unDraw 无公开文生图 API（官网在 Cloudflare 后面），改为
 * <b>本地关键词 → 插图 slug 映射</b>：SVG 已随前端打包
 * （frontend/public/undraw/*.svg，来源 npm undraw-svg 包，MIT/免费授权），
 * URL 为同源相对路径 /undraw/{slug}.svg。查询词命中多个主题时最多返回 2 张。</p>
 */
@Component
public class UndrawImageTool implements ImageSearchTool {

    /** 中文关键词 → 插图 slug（保持插入序：更具体的主题在前） */
    private static final Map<String, String> KEYWORD_MAP = new LinkedHashMap<>();

    static {
        put("头脑风暴", "brainstorming");
        put("构思", "brainstorming");
        put("点子", "brainstorming");
        put("想法", "brainstorming");
        put("创意", "creative-thinking");
        put("灵感", "creative-thinking");
        put("创新", "creative-thinking");
        put("数据分析", "data-analysis");
        put("统计", "data-analysis");
        put("图表", "data-analysis");
        put("代码审查", "code-review");
        put("重构", "code-review");
        put("编程", "code-thinking");
        put("代码", "code-thinking");
        put("程序", "code-thinking");
        put("开发", "code-thinking");
        put("算法", "code-thinking");
        put("Java", "code-thinking");
        put("Python", "code-thinking");
        put("搜索", "searching");
        put("查找", "searching");
        put("检索", "searching");
        put("查询", "searching");
        put("在线", "browsing-online");
        put("网页", "browsing-online");
        put("浏览", "browsing-online");
        put("协作", "collaborating");
        put("合作", "collaborating");
        put("团队", "collaborating");
        put("小组", "collaborating");
        put("在线学习", "online-learning");
        put("网课", "online-learning");
        put("课程", "online-learning");
        put("自学", "continuous-learning");
        put("成长", "continuous-learning");
        put("进步", "continuous-learning");
        put("持续学习", "continuous-learning");
        put("知识", "knowledge");
        put("概念", "knowledge");
        put("原理", "knowledge");
        put("笔记", "add-notes");
        put("清单", "note-list");
        put("待办", "note-list");
        put("阅读", "book-reading");
        put("读书", "book-reading");
        put("书", "books");
        put("书籍", "books");
        put("教材", "books");
        put("图书", "books");
        put("学习", "online-learning");
        put("复习", "online-learning");
        put("老师", "teaching");
        put("教学", "teaching");
        put("辅导", "teaching");
        put("毕业", "graduation");
    }

    private static void put(String keyword, String slug) {
        KEYWORD_MAP.put(keyword, slug);
    }

    @Override
    public String source() {
        return "undraw";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public List<AnswerImage> search(String query) {
        List<AnswerImage> images = new ArrayList<>();
        for (Map.Entry<String, String> entry : KEYWORD_MAP.entrySet()) {
            if (query.contains(entry.getKey())) {
                String slug = entry.getValue();
                images.add(new AnswerImage(source(), "/undraw/" + slug + ".svg", slug + " 插图"));
                if (images.size() >= 2) {
                    break;
                }
            }
        }
        return images;
    }
}
