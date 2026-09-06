-- =====================================================================
-- V2__content_and_rag.sql —— 内容社区 + RAG 知识库表结构
--
-- 新增三大块：
--   1. 内容域：笔记/图片/标签/关联商品/点赞/收藏/关注
--   2. 审核流水：AI 审核结果留痕（t_audit_record）
--   3. RAG：知识文档与切片（t_knowledge_doc / t_knowledge_chunk）
--
-- ★ 中文检索的关键：MySQL 8 的 ngram 全文解析器
--   默认 FULLTEXT 按"空格/标点"分词，中文一整句就是一个词 → 检索失效。
--   WITH PARSER ngram 让 MySQL 按 2-gram 切词（默认 ngram_token_size=2），
--   中文关键词检索才真正可用，无需额外安装 ES。
--   （代价：ngram 是"字面切词"，没有语义；语义部分交给向量检索——见 HybridRetriever）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 内容域
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_note (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT       NOT NULL COMMENT '作者 id',
    title         VARCHAR(100) NOT NULL COMMENT '标题',
    cover         VARCHAR(255) DEFAULT NULL COMMENT '封面图 URL',
    content       TEXT         NOT NULL COMMENT '正文（支持换行，纯文本/简单富文本）',
    status        VARCHAR(20)  NOT NULL DEFAULT 'AUDITING' COMMENT '状态：AUDITING审核中 PUBLISHED已发布 REJECTED已驳回 OFFLINE已下架',
    audit_result  VARCHAR(500) DEFAULT NULL COMMENT '审核结果说明（驳回原因/风险分类），给用户看',
    hot_score     INT          NOT NULL DEFAULT 0 COMMENT '热度分（点赞*3+收藏*5+浏览*1，V3 由 Redis zset 接管排序）',
    like_count    INT          NOT NULL DEFAULT 0 COMMENT '点赞数（冗余计数，V3 改为 Redis 计数+定时落库）',
    collect_count INT          NOT NULL DEFAULT 0 COMMENT '收藏数',
    view_count    INT          NOT NULL DEFAULT 0 COMMENT '浏览数',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_status_created (status, created_at),
    KEY idx_hot (hot_score),
    -- ★ ngram 中文全文索引：笔记标题+正文的关键词检索（V2 不上 ES，数据量 500 篇足够）
    FULLTEXT KEY ft_title_content (title, content) WITH PARSER ngram
) ENGINE = InnoDB COMMENT ='种草笔记表';

CREATE TABLE IF NOT EXISTS t_note_image (
    id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    note_id BIGINT       NOT NULL COMMENT '笔记 id',
    url     VARCHAR(255) NOT NULL COMMENT '图片 URL（V1.5 起走对象存储，不存本地裸路径）',
    sort    INT          NOT NULL DEFAULT 0 COMMENT '排序（小在前）',
    PRIMARY KEY (id),
    KEY idx_note (note_id)
) ENGINE = InnoDB COMMENT ='笔记图片表';

CREATE TABLE IF NOT EXISTS t_note_tag (
    id      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    note_id BIGINT      NOT NULL COMMENT '笔记 id',
    tag     VARCHAR(30) NOT NULL COMMENT '标签，如 #平价好物 / #通勤穿搭',
    PRIMARY KEY (id),
    KEY idx_note (note_id),
    KEY idx_tag (tag)
) ENGINE = InnoDB COMMENT ='笔记标签表';

-- 种草关联商品：笔记 → 商品的"购买入口"，是内容域与电商域的接缝
CREATE TABLE IF NOT EXISTS t_note_product (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    note_id    BIGINT      NOT NULL COMMENT '笔记 id',
    product_id BIGINT      NOT NULL COMMENT '关联商品 id',
    remark     VARCHAR(100) DEFAULT NULL COMMENT '推荐语，如"这个色号显白"',
    PRIMARY KEY (id),
    KEY idx_note (note_id),
    KEY idx_product (product_id)
) ENGINE = InnoDB COMMENT ='笔记关联商品表(种草清单)';

-- ★ 联合唯一索引 = 天然幂等：重复点赞会撞唯一键，无需先查再插
CREATE TABLE IF NOT EXISTS t_note_like (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    note_id    BIGINT   NOT NULL COMMENT '笔记 id',
    user_id    BIGINT   NOT NULL COMMENT '点赞用户 id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_note_user (note_id, user_id),
    KEY idx_user (user_id)
) ENGINE = InnoDB COMMENT ='笔记点赞表(联合唯一索引保证幂等)';

CREATE TABLE IF NOT EXISTS t_note_collect (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    note_id    BIGINT   NOT NULL COMMENT '笔记 id',
    user_id    BIGINT   NOT NULL COMMENT '收藏用户 id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_note_user (note_id, user_id),
    KEY idx_user (user_id)
) ENGINE = InnoDB COMMENT ='笔记收藏表(联合唯一索引保证幂等)';

CREATE TABLE IF NOT EXISTS t_user_follow (
    id             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT   NOT NULL COMMENT '关注人 id',
    follow_user_id BIGINT   NOT NULL COMMENT '被关注人 id',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_follow (user_id, follow_user_id),
    KEY idx_follow (follow_user_id)
) ENGINE = InnoDB COMMENT ='用户关注表';

-- ---------------------------------------------------------------------
-- 审核流水（AI 审核结果留痕，可追溯 / 可人工复核）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_audit_record (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_type       VARCHAR(20) NOT NULL COMMENT '业务类型：NOTE笔记 / COMMENT评论',
    biz_id         BIGINT      NOT NULL COMMENT '业务 id',
    ai_result_json TEXT        COMMENT 'AI 原始返回（JSON：pass/reason/categories/score）',
    status         VARCHAR(20) NOT NULL COMMENT '审核结果：PASS通过 / REJECT驳回 / ERROR审核失败',
    -- ★ 幂等键：同一业务同一版本只审一次，MQ 重投不会重复处理
    biz_version    INT         NOT NULL DEFAULT 1 COMMENT '业务版本号（笔记每次编辑+1，用于审核幂等）',
    reviewer       VARCHAR(50) DEFAULT NULL COMMENT '人工复核人（V3 扩展）',
    reviewed_at    DATETIME    DEFAULT NULL COMMENT '复核时间',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz (biz_type, biz_id, biz_version),
    KEY idx_status (status)
) ENGINE = InnoDB COMMENT ='内容审核流水表';

-- ---------------------------------------------------------------------
-- RAG：知识库
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_knowledge_doc (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    source_type VARCHAR(20) NOT NULL COMMENT '来源：MANUAL商品说明书 / NOTE种草笔记',
    ref_id      BIGINT      NOT NULL COMMENT '来源 id（商品 id 或笔记 id）',
    title       VARCHAR(200) DEFAULT NULL COMMENT '文档标题（笔记标题/商品名）',
    chunk_count INT         NOT NULL DEFAULT 0 COMMENT '切片数',
    status      VARCHAR(20) NOT NULL DEFAULT 'INDEXED' COMMENT '状态：PENDING待索引 / INDEXED已索引 / FAILED失败',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    -- 同一来源只保留一份文档，重复索引走 UPDATE
    UNIQUE KEY uk_source (source_type, ref_id),
    KEY idx_status (status)
) ENGINE = InnoDB COMMENT ='RAG 知识文档表';

CREATE TABLE IF NOT EXISTS t_knowledge_chunk (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    doc_id      BIGINT       NOT NULL COMMENT '所属文档 id',
    chunk_index INT          NOT NULL COMMENT '切片序号（从 0 开始）',
    content     TEXT         NOT NULL COMMENT '切片正文',
    -- ★ 向量存 JSON 数组字符串。当前数据量（数百篇笔记+说明书 ≈ 数千切片）下，
    --   由 Java 侧加载后做余弦相似度即可；数据量上万后应迁 Redis Stack / pgvector，
    --   迁移只需替换 VectorRetriever 实现，业务代码不变（接口已抽象）。
    embedding   MEDIUMTEXT   COMMENT '向量（JSON 数组），由 EmbeddingClient 生成',
    token_count INT          NOT NULL DEFAULT 0 COMMENT '估算 token 数（成本控制用）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_doc (doc_id),
    FULLTEXT KEY ft_content (content) WITH PARSER ngram
) ENGINE = InnoDB COMMENT ='RAG 知识切片表';
