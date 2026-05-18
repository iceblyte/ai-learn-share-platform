-- ============================================
-- 更新种子数据：用户头像、资源封面、外部链接
-- 使用在线 URL 替代 NULL 值
-- ============================================

USE learning_platform;
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ============================================
-- 1. 更新用户头像 (ui-avatars.com 生成)
-- ============================================
UPDATE `user` SET `avatar_url` = 'https://ui-avatars.com/api/?name=Admin&background=0D8ABC&color=fff&size=150' WHERE `username` = 'admin';
UPDATE `user` SET `avatar_url` = 'https://ui-avatars.com/api/?name=Publisher&background=10B981&color=fff&size=150' WHERE `username` = 'publisher';
UPDATE `user` SET `avatar_url` = 'https://ui-avatars.com/api/?name=ZS&background=3B82F6&color=fff&size=150' WHERE `username` = 'zhangsan';
UPDATE `user` SET `avatar_url` = 'https://ui-avatars.com/api/?name=LM&background=8B5CF6&color=fff&size=150' WHERE `username` = 'lisi';
UPDATE `user` SET `avatar_url` = 'https://ui-avatars.com/api/?name=WJ&background=EF4444&color=fff&size=150' WHERE `username` = 'wangwu';
UPDATE `user` SET `avatar_url` = 'https://ui-avatars.com/api/?name=ZX&background=F59E0B&color=fff&size=150' WHERE `username` = 'zhaoliu';
UPDATE `user` SET `avatar_url` = 'https://ui-avatars.com/api/?name=SQ&background=10B981&color=fff&size=150' WHERE `username` = 'sunqi';
UPDATE `user` SET `avatar_url` = 'https://ui-avatars.com/api/?name=ZB&background=6366F1&color=fff&size=150' WHERE `username` = 'zhouba';
UPDATE `user` SET `avatar_url` = 'https://ui-avatars.com/api/?name=WJ&background=EC4899&color=fff&size=150' WHERE `username` = 'wujiu';
UPDATE `user` SET `avatar_url` = 'https://ui-avatars.com/api/?name=ZS&background=14B8A6&color=fff&size=150' WHERE `username` = 'zhengshi';

-- ============================================
-- 2. 更新资源封面图 (picsum.photos 随机高清图)
-- ============================================
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/java-concurrent/800/450' WHERE `id` = 1;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/python-ml/800/450' WHERE `id` = 2;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/data-structure/800/450' WHERE `id` = 3;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/springboot3/800/450' WHERE `id` = 4;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/go-performance/800/450' WHERE `id` = 5;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/leetcode100/800/450' WHERE `id` = 6;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/graph-theory/800/450' WHERE `id` = 7;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/os-tsinghua/800/450' WHERE `id` = 8;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/linux-kernel/800/450' WHERE `id` = 9;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/computer-network/800/450' WHERE `id` = 10;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/tcp-ip/800/450' WHERE `id` = 11;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/mysql-index/800/450' WHERE `id` = 12;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/redis-design/800/450' WHERE `id` = 13;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/deep-learning/800/450' WHERE `id` = 14;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/nlp-python/800/450' WHERE `id` = 15;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/calculus-tongji/800/450' WHERE `id` = 16;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/linear-algebra-mit/800/450' WHERE `id` = 17;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/probability/800/450' WHERE `id` = 18;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/cet4-vocab/800/450' WHERE `id` = 19;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/jlpt-n2/800/450' WHERE `id` = 20;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/kaoyan-math/800/450' WHERE `id` = 21;
UPDATE `resource` SET `cover_image_url` = 'https://picsum.photos/seed/cet-writing/800/450' WHERE `id` = 22;

-- ============================================
-- 3. 更新外部链接 (替换 example.com)
-- ============================================
UPDATE `resource` SET `external_url` = 'https://www.bilibili.com/video/BV1jE411J7N5' WHERE `id` = 2;
UPDATE `resource` SET `external_url` = 'https://github.com/AobingJava/JavaFamily' WHERE `id` = 4;
UPDATE `resource` SET `external_url` = 'https://github.com/youngyangyang04/leetcode-master' WHERE `id` = 6;
UPDATE `resource` SET `external_url` = 'https://github.com/torvalds/linux' WHERE `id` = 9;
UPDATE `resource` SET `external_url` = 'https://www.bilibili.com/video/BV1c4411d7jb' WHERE `id` = 11;
UPDATE `resource` SET `external_url` = 'https://github.com/fighting41love/funNLP' WHERE `id` = 15;
UPDATE `resource` SET `external_url` = 'https://ocw.mit.edu/courses/18-06-linear-algebra-spring-2010/' WHERE `id` = 17;
-- 分布式系统设计 (PENDING) - 查找正确ID
UPDATE `resource` SET `external_url` = 'https://github.com/donnemartin/system-design-primer' WHERE `title` = '分布式系统设计';

-- ============================================
-- 4. 添加资源文件记录 (FILE类型资源的下载文件)
-- ============================================
INSERT IGNORE INTO `resource_file` (`resource_id`, `file_name`, `file_url`, `file_size`, `file_type`) VALUES
(1, 'Java并发编程实战笔记.pdf', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 2048000, 'application/pdf'),
(3, '数据结构与算法完整笔记.pdf', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 3072000, 'application/pdf'),
(5, 'Go语言高性能编程.pdf', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 1536000, 'application/pdf'),
(7, '图论算法专题.pdf', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 1024000, 'application/pdf'),
(8, '操作系统原理笔记.pdf', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 2560000, 'application/pdf'),
(10, '计算机网络笔记.pdf', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 1792000, 'application/pdf'),
(12, 'MySQL索引优化实战.pdf', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 1280000, 'application/pdf'),
(13, 'Redis设计与实现笔记.pdf', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 1152000, 'application/pdf'),
(14, '深度学习入门笔记.pdf', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 2304000, 'application/pdf'),
(16, '高等数学精讲笔记.pdf', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 3584000, 'application/pdf'),
(18, '概率论期末复习.pdf', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 1408000, 'application/pdf'),
(19, '英语四级高频词汇.pdf', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 896000, 'application/pdf'),
(20, '日语N2语法总结.pdf', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 768000, 'application/pdf'),
(21, '考研数学一真题解析.pdf', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 4096000, 'application/pdf'),
(22, '四六级写作模板与范文.pdf', 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf', 1024000, 'application/pdf');
