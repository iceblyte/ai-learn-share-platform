-- 迁移：清理 category 表因 seed 多次重跑产生的重复分类（每个分类 9 份副本），
-- 并为 name 加唯一约束，使 seed.sql 的 INSERT IGNORE 从此幂等。
--
-- 执行前提：category 表存在 name 重复行（SELECT name, COUNT(*) FROM category GROUP BY name HAVING COUNT(*) > 1）。
-- 可重复执行：无重复时所有语句均为空操作。

-- 1. 计算每个分类名的保留 id（最小 id，即最早插入的正本）
CREATE TEMPORARY TABLE tmp_category_canon AS
SELECT MIN(id) AS cid, name FROM category GROUP BY name;

-- 2. 引用重定向：resource.category_id 指向重复副本的，改指正本
UPDATE resource r
JOIN category c ON r.category_id = c.id
JOIN tmp_category_canon t ON c.name = t.name
SET r.category_id = t.cid
WHERE r.category_id <> t.cid;

-- 3. 删除重复副本（只留正本）
DELETE c FROM category c
JOIN tmp_category_canon t ON c.name = t.name
WHERE c.id <> t.cid;

-- 4. 唯一约束（若已存在则跳过本句报错，无副作用）
ALTER TABLE category ADD UNIQUE KEY uk_category_name (name);

DROP TEMPORARY TABLE tmp_category_canon;
