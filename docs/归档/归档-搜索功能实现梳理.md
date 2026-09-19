# ai-mall 搜索功能：到底是怎么实现的？

**一句话结论：不是"只有 LIKE"，而是三套检索并存——商品走 LIKE，笔记走 MySQL FULLTEXT(ngram)，AI 问答走 FULLTEXT + 向量的混合检索。**

面试如果被问"搜索怎么做的"，答案的关键不是"我用了什么"，而是**为什么同一个项目里它们不一样**。

---

## 一、三套检索总览

| 场景 | 入口 | 实现 | 为什么这么选 |
|---|---|---|---|
| 商品列表/搜索 | `GET /api/v1/products?keyword=` | `LIKE CONCAT('%',kw,'%')` | ⚠️ 见第二节：**是"没做完"，不是选型结论** |
| 社区笔记搜索 | `GET /api/v1/notes?keyword=` | `MATCH(title,content) AGAINST(...)` ngram 全文索引 | 正文是 TEXT，`LIKE` 必全表扫 |
| AI 问答 RAG 召回 | `HybridRetriever` | 关键词通道(FULLTEXT) + 向量通道 → RRF 融合 | 字面 + 语义双通道 |

---

## 二、商品：这是缺口，不是"合理选型"（★ 口径已修正）

`backend/src/main/resources/mapper/ProductMapper.xml`

```xml
<if test="keyword != null and keyword != ''">
    AND (p.spu_name LIKE CONCAT('%', #{keyword}, '%')
         OR p.sub_title LIKE CONCAT('%', #{keyword}, '%'))
</if>
```

### 2.1 先明确否定一个错误说法

**❌ 错误口径（以前笔记/项目文档里写的）：**
> "商品只有几百条，LIKE 全表扫无感，所以够用。"

**这个说法站不住，两条硬伤：**

1. **它是规模借口，不是工程理由。** 面试官一句"那你有 50 万商品怎么办"就把它问死了——因为你手里只有"现在少"，没有答案。
2. **它等于主动承认项目是玩具。** "才几百条"这种回答，是在告诉面试官"我没见过真实规模"。**规模小是事实，但不该被当成年设计理由讲出来。**

### 2.2 技术层面：`LIKE '%kw%'` 的真实代价（说清楚才算懂）

- **前缀通配符让 B+ 树索引彻底失效**：B+ 树按**前缀**组织数据，`'%kw%'` 没有可用的前缀，只能**全表扫描**。`LIKE 'kw%'` 能用索引，`LIKE '%kw%'` 不能——这是索引结构决定的，不是优化问题，**加多少索引都没用**；
- **代价随数据量线性增长**，且叠加在 `LEFT JOIN t_product_sku` + `GROUP BY`（取 `MIN(price)` 作起售价）之上——扫的量还要乘以 SKU 行；
- 商品搜索是电商**最核心的入口之一**，把核心链路压在一个必然退化的实现上，本身就是技术债。

---

## 三、笔记搜索：这块是真做了的（FULLTEXT + ngram）

`backend/src/main/resources/mapper/NoteMapper.xml`

```xml
<!-- MySQL FULLTEXT + ngram 解析器：中文关键词检索（无需 ES） -->
<if test="keyword != null and keyword != ''">
    AND MATCH(title, content) AGAINST (#{keyword} IN NATURAL LANGUAGE MODE)
</if>
```

索引定义在 `backend/src/main/resources/db/migration/V2__content_and_rag.sql`：

```sql
FULLTEXT KEY ft_title_content (title, content) WITH PARSER ngram
```

### 3.1 为什么必须加 `WITH PARSER ngram`——这是整个设计的题眼

**MySQL 默认的 FULLTEXT 分词规则是"按空格和标点切分"。**

- 英文 `"bluetooth earphone"` → 切成 `bluetooth` / `earphone` 两个词元，检索正常；
- 中文 `"这款蓝牙耳机佩戴很舒服"` → **中间没有空格，整句被当成一个词元**！用户搜"耳机"时，`MATCH ... AGAINST('耳机')` 要拿"耳机"去匹配"这款蓝牙耳机佩戴很舒服"这整个词元，**匹配不上，检索彻底失效**。

这不是"效果差一点"，是**中文场景下默认 FULLTEXT 完全不可用**。很多人踩过这个坑后得出"MySQL 全文索引不支持中文"的错误结论——其实只差一个 `WITH PARSER ngram`。

**ngram 解析器做的事**：把文本按固定长度 N 的滑窗切分，默认 `ngram_token_size = 2`，即 **2-gram（bigram）**：

```
"蓝牙耳机"  →  蓝牙 / 牙耳 / 耳机
```

搜索时查询串也按同样规则切分，然后按 token 匹配。用户搜"耳机"，查询切成"耳机"，正好命中索引里的"耳机"token → **中文字面检索可用**。

### 3.2 ngram 的代价（面试要主动说出来，这是加分项）

1. **索引膨胀**：一个 n 字的中文串会产生 n-1 个 bigram，索引体积远大于原文本；
2. **不支持单字检索**：用户搜"机"（长度 1 < `ngram_token_size=2`）**匹配不到任何东西**——这是 ngram 方案的客观边界，注意**不要**拿它当"商品用 LIKE 合理"的理由（商品是没做，不是选型，见第二节）；
3. **纯字面，无语义**：搜"戴着疼"匹配不到"佩戴体验"。
   → **第 3 点的解法就是 RAG 那套 HybridRetriever 的向量通道**，两条线在这里接上了。

### 3.3 和游标分页的配合

`pageWhere` 里还做了游标分页，注释写得很清楚：

```
★ 游标必须与排序键配套，否则会漏数据：
  - 按最新排(id DESC)：游标 = id < cursorId
  - 按热度排(hot_score DESC, id DESC)：游标是二元组
    只用 id 做游标会漏掉"热度更高但 id 更小"的笔记
```

**面试点**：`LIMIT offset, size` 深翻页会越翻越慢（要扫掉前 offset 行），游标分页（keyset pagination）用 `WHERE id < cursor` 走主键索引直接定位。**但代价是不能跳页**，只能"下一页"——Feed 流场景天然适合，后台管理列表就不适合。

---

## 四、RAG 混合检索：FULLTEXT 的第二个用武之地

`backend/src/main/java/com/aimall/ai/rag/HybridRetriever.java`

```
用户问题 "这耳机戴着耳朵疼吗"
  │
  ├─① 关键词通道：MySQL FULLTEXT(ngram) MATCH AGAINST → Top-20
  │    （"耳机""戴着"这类字面词命中）
  │
  ├─② 向量通道：问题 embed → 与全量切片向量算余弦 → Top-20
  │    （"戴着疼"≈"佩戴体验/压耳感"这类语义命中）
  │
  └─③ RRF 融合两路排名：
       score(d) = Σ 1 / (k + rank_i(d))     k=60
       两路都命中的片段得分叠加 → 排到最前
  │
  └─ 截断 Top-5 → 返回（带来源：MANUAL/NOTE）
```

切片表的索引（`V2__content_and_rag.sql` 第 151 行）：

```sql
FULLTEXT KEY ft_content (content) WITH PARSER ngram
```

**RRF 为什么好**（源码注释原文）：两路通道的分数量纲完全不同（BM25 分可以上百，余弦 ∈ [0,1]），**直接加权融合 = 拿厘米和斤相加**。RRF 只用「排名」不用「分数」——天然免疫量纲问题，还有个漂亮性质：**两路都命中的片段得分自动叠加**。k=60 是原论文推荐值，用来平滑"第 1 名与第 2 名"的差距。

### 4.1 顺带说清：向量为什么也敢存 MySQL

切片数在数千量级，全量加载进内存算余弦是毫秒级。检索层已抽象（`VectorRetriever` / `HybridRetriever`），**到万级切片换 pgvector 或 Milvus 时只换实现类，业务零改动**——和商品搜索一样，关键是**退路已铺好、触发条件明确**。
