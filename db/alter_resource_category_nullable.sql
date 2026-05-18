-- 允许 resource 表的 category_id 为空（草稿可能未选分类）
ALTER TABLE `resource` MODIFY `category_id` BIGINT NULL COMMENT '分类ID';
