# V2 内容社区与 Hybrid RAG 从零到实战：跟着 AI 种草商城学会做"会种草的电商"

> ai-mall 项目升级讲解文档 · V2（内容社区 / Feed 流 / 点赞幂等 / AI 审核 + MQ / Hybrid RAG / AI 文案）
> 配套词典：[V2内容社区与RAG详解【知识词典】.md](V2内容社区与RAG详解【知识词典】.md)——正文中每个带链接的概念都在词典里有"一句话核心+比喻+原理展开+代码对应+常见追问"
> 前置阅读：[AI模块详解-SpringAI从零到实战.md](AI模块详解-SpringAI从零到实战.md)（ChatClient/函数调用——RAG 工具是它的续集）、[V1.5工程基建详解.md](V1.5工程基建详解.md)（Flyway/线程池/可降级传统）
> 读法建议：按章顺序读，每章末尾有动手作业——**动手做过的才叫掌握**。

---

## 【易错关键点】（先背这个，全文读完回来再背一遍）

1. **RAG 只管"文档"不管"数据"**——价格/库存走 SQL（searchProduct），说明书/用户体验走 RAG（searchDocs）。说反了就是原理性错误，这是 V2 全篇第一红线（[RAG 的边界](V2内容社区与RAG详解【知识词典】.md#rag-的边界)）。
2. **"几百份文档为什么不塞长上下文"必考**——四条标准答案：语料动态增长 / C 端首字延迟 / 权限隔离 / 成本（[长上下文 vs RAG](V2内容社区与RAG详解【知识词典】.md#长上下文-vs-rag)）。
3. **Hybrid 不是噱头是刚需**——"蓝牙5.3""IPX7"这类精确词向量抓不住（BM25 的主场）；"戴久了舒服吗"这种说法 BM25 搜不到（向量的主场）。电商两类问题都高频，必须双路（[Hybrid 检索](V2内容社区与RAG详解【知识词典】.md#hybrid-检索)）。
4. **RRF 用"排名"不用"分数"**——BM25 分可以上百、余弦 ∈ [0,1]，量纲不同不能直接加权；RRF 天然免疫量纲，且双路命中的片段自动加分（[RRF](V2内容社区与RAG详解【知识词典】.md#rrf)）。
5. **点赞幂等靠唯一索引，不是先查后插**——`uk(note_id,user_id)` 撞键抛 [DuplicateKeyException](V2内容社区与RAG详解【知识词典】.md#duplicatekeyexception) 当"已点过"处理；先查后插在并发下会插两条（[唯一索引幂等](V2内容社区与RAG详解【知识词典】.md#唯一索引幂等)）。
6. **发布秒回、审核异步**——AI 审核要几秒，用户点发布必须立刻看到结果；这是 MQ 的第一个真实业务理由，不是"为了用 MQ 而用 MQ"（[异步解耦](V2内容社区与RAG详解【知识词典】.md#异步解耦)）。
7. **消费端防"毒消息循环"**——必然失败的消息无限重试只会打爆日志；本项目消费端吞异常 ACK，失败由 ERROR 流水+死信队列兜底（[毒消息](V2内容社区与RAG详解【知识词典】.md#毒消息)）。
8. **游标分页与排序键必须配套**——按热度排时游标是 `(hot_score, id)` [二元组](V2内容社区与RAG详解【知识词典】.md#二元组游标)，只用 id 会漏数据。
9. **MySQL 中文全文检索必须 `WITH PARSER ngram`**——默认解析器按空格分词，中文整句是一个词，检索全失效（[ngram](V2内容社区与RAG详解【知识词典】.md#ngram-解析器)）。
10. **AI 文案只出草稿绝不自动发布**——"AI 不直写业务数据"是项目红线；未经审核的 AI 内容直接公开是 UGC 平台事故（[AI 不直写](V2内容社区与RAG详解【知识词典】.md#ai-不直写业务数据)）。
11. **审核幂等三防线**——流水表版本唯一键 + INSERT IGNORE / 笔记状态机条件更新 / AI 失败只记 ERROR 不误杀（[消费端幂等](V2内容社区与RAG详解【知识词典】.md#消费端幂等)）。
12. **embedding 默认 local 哈希是诚实降级**——它只能捕获字面重叠捕获不了语义（"续航久"≠"电池耐用"），价值是零依赖跑通全链路；接真实模型后质量升级（[EmbeddingClient](V2内容社区与RAG详解【知识词典】.md#embeddingclient)）。
13. **双工具分流不写 if-else**——searchProduct/searchDocs 的分流由模型读工具 description 自选，代码零意图判断（[双工具路由](V2内容社区与RAG详解【知识词典】.md#双工具路由)）。

---

## 全书地图

```
第 0 章  破除恐惧：内容社区就是你天天刷的东西（心智模型）
第 1 章  数据库设计：内容域 10 张表的取舍
第 2 章  Feed 流（上）：游标分页——干掉 OFFSET 深分页
第 3 章  Feed 流（下）：N+1 攻防与中文全文检索
第 4 章  点赞收藏：幂等与计数不丢失
第 5 章  RAG 前传：先立边界——什么问题走 RAG（全篇最重要）
第 6 章  索引链路：切片、向量化、EmbeddingClient 可插拔
第 7 章  检索链路：双通道 + RRF 融合（本章最重）
第 8 章  Agent 分流：双工具，模型自己选
第 9 章  AI 审核 + MQ：发布秒回、异步回写、幂等三防线
第 10 章 AI 种草文案：RAG 参与创作的内容飞轮
第 11 章 模拟数据：让 500 篇笔记长得像真的
第 12 章 验收清单 + 面试速查 + 报错速查
```

---

# 第 0 章 破除恐惧：内容社区就是你天天刷的东西

## 0.1 你已经是内容社区的资深用户

0 基础对"做一个内容社区"的想象往往是一大坨神秘的产品工程。拆开看，**你每天刷小红书时看到的每个元素，就是全部需求清单**：

| 你刷到的 | 背后的技术问题 | 本项目对应 |
|---|---|---|
| 首页瀑布流无限刷 | 分页怎么翻到"第 1000 页"还不卡 | 第 2 章 [游标分页](V2内容社区与RAG详解【知识词典】.md#游标分页) |
| 搜索"通勤穿搭" | 中文关键词怎么命中 | 第 3 章 [ngram](V2内容社区与RAG详解【知识词典】.md#ngram-解析器) |
| 点赞红心 | 狂点红心会不会记两次 | 第 4 章 [幂等](V2内容社区与RAG详解【知识词典】.md#唯一索引幂等) |
| 笔记里的商品链接 | 内容和电商怎么接起来 | 第 1 章 t_note_product |
| 发布后"审核中" | 审核为什么快、怎么不卡发布 | 第 9 章 [MQ 异步](V2内容社区与RAG详解【知识词典】.md#异步解耦) |
| 问 AI"这耳机戴着舒服吗" | AI 怎么知道用户的真实体验 | 第 5-8 章 RAG |

**没有一项技术是"凭空为了简历"的——每项都对应你刷过的某个瞬间**。这就是"业务驱动演进"（项目设计宪法的核心）。

## 0.2 V2 在补什么：V1 留下的三个"名不副实"

V1 结束时项目叫"AI **种草**商城"，但：
- **没有任何种草内容**——"种草"二字无实体承载；
- **MQ 无处落地**——设计文档写了审核异步，但没有内容可审；
- **RAG 无处落地**——没有说明书语料，更没有 UGC 笔记语料。

V2 一句话：**把"种草"变成数据库里的表、页面上的流、AI 嘴里的语料。**

> 💡 **心智模型①（内容飞轮）**：用户发笔记 → AI 审核通过 → 成为 [RAG 语料](V2内容社区与RAG详解【知识词典】.md#语料) → AI 导购能答体验类问题 / AI 文案能引用真实体验 → 帮新用户写出更好的笔记 → 更多语料。**每一步都在喂养下一步**——这是"种草社区+RAG"独有的化学反应，纯电商项目抄不来。面试画在白板上，这一张图就是 V2 的全部价值主张。

## 0.3 高频术语速查表（全书通用）

| 术语 | 一句话 | 详见词典 |
|---|---|---|
| UGC | 用户生成的内容（笔记/评论） | [UGC](V2内容社区与RAG详解【知识词典】.md#ugc) |
| Feed 流 | 信息流：按时间/热度排序的内容列表 | [Feed](V2内容社区与RAG详解【知识词典】.md#feed-流) |
| 幂等 | 同一操作执行 N 次 = 执行 1 次 | [唯一索引幂等](V2内容社区与RAG详解【知识词典】.md#唯一索引幂等) |
| RAG | 检索增强生成：先查资料再回答 | [RAG](V2内容社区与RAG详解【知识词典】.md#rag) |
| 切片 | 把长文档切成几百字的小段 | [Chunking](V2内容社区与RAG详解【知识词典】.md#chunking) |
| Embedding | 文本 → 高维向量，语义近则距离近 | [Embedding](V2内容社区与RAG详解【知识词典】.md#embedding) |
| BM25 | 经典关键词相关性打分算法 | [BM25](V2内容社区与RAG详解【知识词典】.md#bm25) |
| RRF | 按排名倒数融合多路检索结果 | [RRF](V2内容社区与RAG详解【知识词典】.md#rrf) |
| DLX | 死信交换机：被拒/过期消息的去处 | [死信队列 DLX](V2内容社区与RAG详解【知识词典】.md#死信队列-dlx) |
| ngram | MySQL 的中文二元分词器 | [ngram 解析器](V2内容社区与RAG详解【知识词典】.md#ngram-解析器) |

## 本章动手作业

不写代码，回答三个问题：
1. 内容飞轮有五环，说出每环"喂养"下一环的具体机制。
2. V1 的"种草"二字缺了什么才名不副实？V2 用哪三样东西补齐？
3. 你刷小红书时经历过的"审核中"状态，后端此刻在做什么？

---

# 第 1 章 数据库设计：内容域 10 张表的取舍

> 核心机制：[冗余计数](V2内容社区与RAG详解【知识词典】.md#冗余计数) · [软删除](V2内容社区与RAG详解【知识词典】.md#软删除) · [唯一索引幂等](V2内容社区与RAG详解【知识词典】.md#唯一索引幂等)

## 1.1 全景：10 张表一张图

```
内容域（V2 新增）
├─ t_note            笔记主表（状态机 + ngram 全文索引 + 冗余计数）
├─ t_note_image      图集（URL 存 V1.5 对象存储的返回值）
├─ t_note_tag        标签（独立表而非 JSON 列）
├─ t_note_product    种草清单 ★ 内容域与电商域的接缝
├─ t_note_like       点赞（uk_note_user 幂等）
├─ t_note_collect    收藏（与点赞同构）
├─ t_user_follow     关注（V3 Feed 关注流用，先建表不建接口——按需演进）
├─ t_audit_record    审核流水（uk 幂等 + AI 结果留痕）
└─ RAG：t_knowledge_doc（文档头）+ t_knowledge_chunk（切片+向量）
```

## 1.2 三个值得单独讲的设计决策

**① 冗余计数为什么放主表**——like_count/collect_count/view_count 是[冗余计数](V2内容社区与RAG详解【知识词典】.md#冗余计数)：列表页要展示，每次 `count(*)` 聚合太贵。V2 用同事务 `UPDATE ... SET like_count = like_count + 1`（原子，不丢更新）；V3 升级为 Redis 计数+定时落库——**先简单正确，再按压力演进**。

**② 审核为什么单独建流水表**——AI 判定是概率性的：出错要能翻案（ai_result_json 留痕）、要能人工复核（reviewer 字段）、要幂等（版本唯一键）。只在主表打状态，出问题就是黑箱（[审核流水](V2内容社区与RAG详解【知识词典】.md#审核流水)）。

**③ 向量为什么敢存 MySQL**——**真实理由是"MySQL 没有向量索引能力，而这个量级下内存余弦的代价可接受"**，不是"数据少所以随便存"。判断依据是数学：500 笔记×300 字/50 字步长 ≈ 数千切片，全量加载（每片 256 维 JSON ≈ 2KB，总 ~10MB）+ 内存余弦 = 毫秒级。**触发条件是"切片数上万"**——届时加载耗时与内存膨胀会让全量扫变得不可接受，换 pgvector/Milvus。检索层已抽象（EmbeddingClient/VectorRetriever），届时只换实现类。

## 本章动手作业

1. 打开 `V2__content_and_rag.sql`，对照 1.1 的图找到每张表，说出三张唯一索引各自的幂等语义（uk_note_user / uk(biz_type,biz_id,biz_version) / uk(source_type,ref_id)）。
2. 思考：标签为什么独立建表而不是在 t_note 加个 JSON 列？（答：可索引（按标签筛选走索引）、可聚合（热门话题榜）、可加约束——JSON 列三样都弱。）
3. t_user_follow 建了表却不建接口，算不算过度设计？（答：不算——表是数据结构（迁移只增不改，早建晚建成本一样），接口是行为（V3 关注流需要时再加）。边界感。）

---

# 第 2 章 Feed 流（上）：游标分页——干掉 OFFSET 深分页

> 核心机制：[游标分页](V2内容社区与RAG详解【知识词典】.md#游标分页) · [二元组游标](V2内容社区与RAG详解【知识词典】.md#二元组游标)

## 2.1 先撞南墙：LIMIT 100000, 20 有多慢

0 基础的第一反应：分页就是 `LIMIT offset, size`。翻到第 5000 页：

```sql
SELECT * FROM t_note ORDER BY id DESC LIMIT 100000, 20;
```

MySQL 的执行方式：**扫描并丢弃前 100000 行，然后返回 20 行**。offset 越大越慢——用户翻得越深，你付的钱（扫描量）越多，这个曲线在产品上是**惩罚深度用户**的。

时间复杂度对比：OFFSET 方式 O(offset+size)，游标方式 O(logN + size)。

## 2.2 游标分页：把"第几页"换成"从哪继续"

核心思路一句话：**记住上一页最后一条的排序值，下一页从它后面取**。

```sql
-- 第一页
SELECT * FROM t_note ORDER BY id DESC LIMIT 20;
-- 第二页（cursorId = 上一页最后一条的 id）
SELECT * FROM t_note WHERE id < #{cursorId} ORDER BY id DESC LIMIT 20;
```

`WHERE id < cursor` 走主键索引直接定位，**无论翻到第几页，开销恒定**。这就是信息流能无限刷的底层原理。

`NoteMapper.xml` 的 selectPage（节选）：

```xml
<choose>
    <when test="orderBy != null and orderBy == 'newest'">
        <if test="cursorId != null">
            AND id &lt; #{cursorId}          <!-- 最新排序：游标只要 id -->
        </if>
    </when>
    <otherwise>
        <if test="cursorHot != null and cursorId != null">
            AND (hot_score &lt; #{cursorHot}
            OR (hot_score = #{cursorHot} AND id &lt; #{cursorId}))
            <!-- ★ 热度排序：游标是二元组，见 2.3 -->
        </if>
    </otherwise>
</choose>
```

## 2.3 二元组游标：本项目最容易讲错/写错的细节

按热度排序时，`ORDER BY hot_score DESC, id DESC`——**热度会并列**（两篇笔记都是 999 分）。如果游标只用 hot_score：

```
... WHERE hot_score < 999   ← 漏掉所有 999 分的其它笔记！
```

如果只用 id（热度排序下 id 无序）：漏掉"热度比上页末条更高、但 id 更小"的笔记。

**正确姿势：复合游标**——`(hot_score, id)` 两个维度同时比较：

```sql
WHERE hot_score < 999
   OR (hot_score = 999 AND id < 10537)   -- 先比热度，平了再比 id（id 唯一，保证全序）
```

通用规律：**排序键有 N 列，游标就是 N 元组**——最后一列必须是唯一键（否则页边界的"下一个"无法定义）。前端配合：把上一页最后一条的 `id + hotScore` 带回来（Notes.vue 的 `cursor` 状态）。

## 2.4 游标分页的代价（面试要能说出来）

**不支持直接跳页**——只能"下一页/上一页"连续翻。这对 Feed 天然够用（用户本来就是往下滑），但后台管理要跳页就得用 offset——**两种方案按场景选，不是谁替代谁**。这个"承认自己方案的边界"的表述方式，面试远比"游标分页更优"这种绝对化说法加分。

## 本章动手作业

1. `/api/v1/notes?orderBy=newest&pageSize=20` 连刷三页，观察请求参数里 cursorId 的变化。
2. 切 `orderBy=hot`，观察参数多了 cursorHot——为什么？（二元组。）
3. 思考：按"创建时间 created_at"排序时，游标应该是什么？（答：(created_at, id) 二元组——created_at 会并列。凡是可能重复的排序键都要补唯一键。）

---

# 第 3 章 Feed 流（下）：N+1 攻防与中文全文检索

> 核心机制：[N+1 问题](V2内容社区与RAG详解【知识词典】.md#n1-问题) · [ngram 解析器](V2内容社区与RAG详解【知识词典】.md#ngram-解析器) · [FULLTEXT 索引](V2内容社区与RAG详解【知识词典】.md#fulltext-索引)

## 3.1 N+1 攻防：列表接口优化的第一答

**事故现场**：Feed 返回 20 篇笔记，每篇要展示作者昵称、封面、标签、关联商品、"我是否点过赞"。0 基础写法：

```java
for (Note n : notes) {
    User author = userMapper.selectById(n.getUserId());     // 20 次
    List<NoteImage> imgs = extraMapper.selectImages(n.getId());  // 20 次
    List<NoteTag> tags = extraMapper.selectTags(n.getId());      // 20 次
    // ...
}
```

1 次列表 + 20×5 次关联查询 = **101 条 SQL**。每条 SQL 都是一次网络往返（~1ms）+ 解析执行，列表接口 RT 直接爆炸。这就是 [N+1 问题](V2内容社区与RAG详解【知识词典】.md#n1-问题)——面试问"列表接口怎么优化"，这是第一答。

**对策：批量 IN 查询 + 内存分组组装**（`NoteServiceImpl.assembleList`）：

```java
List<Long> ids = notes.stream().map(Note::getId).toList();   // 20 个 id

// 5 条批量 SQL 拿全所有附属信息
Map<Long, User> users = ...;                                  // 批量查作者（computeIfAbsent 去重）
extraMapper.selectCoversByNotes(ids);                         // 批量封面（子查询取每篇 MIN(sort)）
extraMapper.selectTagsByNotes(ids);                           // 批量标签
extraMapper.selectProductsByNotes(ids);                       // 批量关联商品
likeMapper.listLikedNoteIds(me, ids);                         // 批量"我点过赞的"
collectMapper.listCollectedNoteIds(me, ids);                  // 批量"我收藏过的"

// 内存分组拼装（groupingBy / contains）
```

结果：**无论页大小，固定 5 条 SQL**。封面查询的子查询写法值得一提：

```xml
<!-- 只取每篇 sort 最小的那张图（封面），一条 SQL 搞定而不是 20 条 -->
SELECT t.* FROM t_note_image t
INNER JOIN (
    SELECT note_id, MIN(sort) AS min_sort FROM t_note_image
    WHERE note_id IN (...) GROUP BY note_id
) m ON t.note_id = m.note_id AND t.sort = m.min_sort
```

## 3.2 中文全文检索：ngram 一站讲透

### 先撞南墙

需求：搜索"降噪耳机"。0 基础写法 `WHERE title LIKE '%降噪%'`——**全表扫描**（前缀通配让索引失效），100 万行时必死。加普通索引也没用：B+ 树按**前缀**组织，`%xx%` 用不上。

### 正确姿势：FULLTEXT + ngram

```sql
-- 建表时声明（V2 迁移里）
FULLTEXT KEY ft_title_content (title, content) WITH PARSER ngram

-- 查询（NoteMapper.xml）
SELECT ... FROM t_note
WHERE MATCH(title, content) AGAINST (#{keyword} IN NATURAL LANGUAGE MODE)
```

**为什么必须 `WITH PARSER ngram`**——MySQL 默认全文解析器按**空格/标点**分词（为英文设计）。中文一句话没有空格 → 整句被当成**一个词** → 你搜"降噪"，库里没有任何词等于"降噪" → **检索全部失效**。[ngram 解析器](V2内容社区与RAG详解【知识词典】.md#ngram-解析器)把文本按 **2 个字符一组**切分：

```
"主动降噪耳机" → 主动 | 动降 | 降噪 | 噪耳 | 耳机     （ngram_token_size 默认 = 2）
```

索引建在这些二元组上，"降噪"能精确命中。代价：纯字面切词无语义（"续航"搜不到"电池耐用"——这部分交给 RAG 的向量通道，正是 [Hybrid](V2内容社区与RAG详解【知识词典】.md#hybrid-检索) 存在的理由）；单字查询搜不到（token 是 2 字）。

### 为什么不上 ES

一句话口径：**"我知道 ES 是搜索标配。我的检索层是可替换的抽象（HybridRetriever / VectorRetriever），触发条件很明确：MySQL 全文检索的 P99 超 200ms，或者需要复杂相关性调权、聚合分析、拼音同义词 analyzer 生态时换 ES——**用数据说话，不用简历驱动**。"**

> ⚠️ **别这么说**："数据才 500 条，上 ES 是过度设计 / 装航空母舰"。这话听着聪明，实则两条硬伤：①它是**规模借口**，面试官问"那 50 万条呢"你没有答案；②**等于主动承认项目没见过规模**。正确姿势是"**触发条件 + 已铺好的退路**"——前者显得没想过，后者显得想清楚了选择不做。
>
> ⚠️ **更要注意**：这套"量级不够所以不上 ES"的说法**只适用于笔记检索**（这里确实用 MySQL 全文索引做出了完整方案）。**商品搜索的 `LIKE` 不能套用这套话术**——那里是**没做**，不是选型。见 [项目设计文档 5.2 搜索口径](项目设计文档.md)。

## 本章动手作业

1. 开 SQL 日志，刷一页 Feed，数 SQL 条数——应是固定 5 条（+1 条主查询）。
2. 搜"降噪"确认有结果；再搜一个只出现在笔记正文一个字的词（如"梨"）——确认搜不到（ngram 2 字粒度）。
3. `EXPLAIN` 一条 LIKE '%xx%' 与 MATCH...AGAINST，对比 type 列（ALL vs fulltext/索引）。
4. 思考：按标签筛选（tag=通勤穿搭）走的是什么索引？（idx_tag 普通索引——等值查询不需要全文。）

---

# 第 4 章 点赞收藏：幂等与计数不丢失

> 核心机制：[唯一索引幂等](V2内容社区与RAG详解【知识词典】.md#唯一索引幂等) · [DuplicateKeyException](V2内容社区与RAG详解【知识词典】.md#duplicatekeyexception) · [丢失更新](V2内容社区与RAG详解【知识词典】.md#丢失更新) · [TOCTOU](V2内容社区与RAG详解【知识词典】.md#toctou)

## 4.1 先撞南墙：狂点红心会发生什么

用户狂点红心（前端防抖失灵），两个"点赞"请求**同时**到达：

```
时刻    线程A                          线程B
t1     SELECT（没点过）
t2                                    SELECT（也没点过！）    ← 都通过了检查
t3     INSERT 点赞记录
t4                                    INSERT 点赞记录         ← 插了两条！
```

这是 [TOCTOU](V2内容社区与RAG详解【知识词典】.md#toctou)（Time-Of-Check-To-Time-Of-Use）竞态：**检查与使用之间被插队**。后果：like_count 被加两次、数据脏、取消点赞只删得掉一条。

## 4.2 对策：唯一索引兜底，不靠代码

```java
// NoteServiceImpl.like（逐段讲）
@Transactional(rollbackFor = Exception.class)
public void like(Long noteId, boolean liked) {
    Long userId = StpUtil.getLoginIdAsLong();
    ensurePublished(noteId);
    if (liked) {
        try {
            likeMapper.insert(noteId, userId);       // ★ 直接 INSERT，不先查
            noteMapper.incrLikeCount(noteId);
        } catch (DuplicateKeyException e) {
            // 已点过：幂等静默（不报错，前端体验为"红心已是亮的"）
            log.debug("重复点赞已忽略 noteId={} userId={}", noteId, userId);
        }
    } else {
        int rows = likeMapper.delete(noteId, userId);
        if (rows > 0) {                              // ★ 只有真删了才减计数
            noteMapper.decrLikeCount(noteId);
        }
    }
    refreshHotScore(noteId);
}
```

四个要点：
1. **直接 INSERT**——并发下第二个人的 INSERT 撞 `uk_note_user(note_id, user_id)` 抛 [DuplicateKeyException](V2内容社区与RAG详解【知识词典】.md#duplicatekeyexception)，捕获当"已点过"静默处理。**把并发正确性交给数据库唯一约束，是成本最低、最可靠的做法**（对比先查后插：两条 SQL 之间永远有竞态窗口）。
2. **计数增减必须与关系表操作同事务**——`@Transactional` 保证"插关系+加计数"要么都成功要么都回滚。
3. **取消点赞判断 rows>0 才减**——本来没点过时 delete 返回 0，不减计数（防负数偏移）。
4. **幂等的语义是"静默"**——点赞是"状态确认"型操作，重复=已达成，不报错。对比：限量发售撞限购 uk 是"业务失败"要提示——**同一个异常，两种业务语义，按场景选**（面试可讲）。

> **★ 2026-09-09 修缮更新（V3 后期：计数上 Redis）**：上面是 V2 原始实现（计数直写 DB）。V3 后期为治理写放大（热点笔记每次互动 3 次 DB 写），计数改为 Redis Hash 攒**增量**（`HINCRBY aimall:cnt:like {noteId} 1`）+ 60s 定时任务加法回写；`refreshHotScore` 也从互动路径收敛到落库路径。本节的"唯一索引幂等 + 事务保关系表"完全不变（关系表仍是 DB 事务），只有计数出口变了——Redis 不可用时自动降级回上面这套直写逻辑（V2 代码即降级路径，老代码没白写）。详见 `V3支付与Redis详解.md` 词典"计数增量桶"。

## 4.3 计数不丢失：原子自增 vs 先读后写

计数 SQL 必须写 `SET like_count = like_count + 1`（原子），**不能** Java 里 `setLikeCount(vo.getLikeCount()+1)`：

```
两个请求都读到 count=100 → 各自写 101 → 丢一次更新（[丢失更新](V2内容社区与RAG详解【知识词典】.md#丢失更新)）
```

自减加兜底 `AND like_count > 0` 防极端时序下减成负数。

## 本章动手作业

1. 用 curl 对同一笔记并发发 10 个 like（`seq 10 | xargs -P10 -I{} curl ...`），查 t_note_like——**只有 1 条**、like_count=1（幂等实证）。
2. 取消再点赞，计数回到 1 而不是 2。
3. 思考：为什么不把"是否点过赞"缓存在 Redis（V3 有 Redis 了）？（答：可以（V3 的演进方向），但 V2 的语义用唯一索引在 DB 层就已完备——缓存是性能优化不是正确性来源。）

---

# 第 5 章 RAG 前传：先立边界——什么问题走 RAG（全篇最重要）

> 核心机制：[RAG](V2内容社区与RAG详解【知识词典】.md#rag) · [RAG 的边界](V2内容社区与RAG详解【知识词典】.md#rag-的边界) · [长上下文 vs RAG](V2内容社区与RAG详解【知识词典】.md#长上下文-vs-rag) · [语义鸿沟](V2内容社区与RAG详解【知识词典】.md#语义鸿沟)

## 5.1 RAG 一句话（0 基础）

[RAG](V2内容社区与RAG详解【知识词典】.md#rag)（Retrieval-Augmented Generation，检索增强生成）= **先查资料、再回答**。

```
用户问："这耳机戴久了耳朵疼吗？"
  ① 检索：从你的知识库里找最相关的 3~5 段文字（别人的使用体验）
  ② 增强：把这几段塞进 prompt（"参考资料：……"）
  ③ 生成：模型基于参考资料回答（而不是凭训练记忆瞎编）
```

比喻：开卷考试——不让学霸（模型）背题库，而是发给它一本可以翻的资料（你的语料）。模型负责"读资料并组织答案"，检索负责"找到对的那一页"。

## 5.2 立边界：结构化 vs 非结构化（说反就是原理性错误）

| 用户问题 | 正确通道 | 理由 |
|---|---|---|
| "这耳机多少钱" | searchProduct → SQL | 价格是**实时结构化数据**，语料里的价格是过期快照 |
| "有货吗" | searchProduct → SQL | 库存同理，且要精确 |
| "防水吗/续航多久" | searchDocs → RAG | 说明书文本（非结构化） |
| "戴久了耳朵疼吗" | searchDocs → RAG | **只有别的用户的笔记能答**——说明书不会写缺点 |

第 4 行是本项目 RAG 的灵魂：**[UGC 笔记是最优质的语料](V2内容社区与RAG详解【知识词典】.md#ugc-语料)**，官方说明书答不了主观体验（"IPX5 防水"是说明书的话术，"戴着跑步出汗没坏"才是用户的）。这也是"种草社区+RAG"互相成就的业务逻辑——**V2 的存在让 RAG 有意义，RAG 的存在让笔记有价值**。

**反面口径（面试雷区）**："我用 RAG 查商品价格"——错。RAG 是模糊检索，价格要精确值；语料是快照，价格是实时态。正确表述永远是"价格库存走 SQL，说明书与体验走 RAG"。

## 5.3 必考题："几百份文档为什么不塞长上下文"

模型上下文已经 128K 了，把 500 篇笔记全塞进去不行吗？**四条标准答案**（[长上下文 vs RAG](V2内容社区与RAG详解【知识词典】.md#长上下文-vs-rag)）：

1. **语料动态增长**——笔记每天在发，"每次全量塞"不可持续（今天的 128K 明天就不够）；
2. **C 端首字延迟**——长上下文的 [Prefill](V2内容社区与RAG详解【知识词典】.md#首字延迟)（预填充）耗时随长度线性涨，打字机效果的第一字要等好几秒——体验崩；
3. **权限隔离**——RAG 检索层可以按来源过滤（只检公开笔记），prompt 全塞没有权限边界；
4. **成本**——每次对话全量灌入 token 费用 = (全部语料)×(对话次数)，调用量上来费用失控。

## 5.4 语义鸿沟：为什么单靠关键词检索不够

[语义鸿沟](V2内容社区与RAG详解【知识词典】.md#语义鸿沟)：字面不同但意思相同——"续航久" vs "电池耐用"。全文检索（ngram/BM25）跨不过去：两个字都不重叠。反过来，字面相似但意思不同（"苹果手机" vs "苹果好吃"）关键词又会误命中。**这就是要双通道的根源**（第 7 章）。

## 本章动手作业

1. 把 5.2 的表抄一遍（手写，不看）——这是 V2 面试的第一道关。
2. 给"为什么不塞长上下文"的四条各配一个数字例子（如第 2 条：500 篇×500 字≈25 万字≈35 万 token 的 Prefill 要多少秒）。
3. 思考：用户问"3 号订单到哪了"该走哪？（答：都不是——当前没有订单工具，模型应如实说查不了。工具边界也是 RAG 边界的一部分。）

---

# 第 6 章 索引链路：切片、向量化、EmbeddingClient 可插拔

> 核心机制：[Chunking](V2内容社区与RAG详解【知识词典】.md#chunking) · [重叠 overlap](V2内容社区与RAG详解【知识词典】.md#重叠-overlap) · [Embedding](V2内容社区与RAG详解【知识词典】.md#embedding) · [EmbeddingClient](V2内容社区与RAG详解【知识词典】.md#embeddingclient) · [L2 归一化](V2内容社区与RAG详解【知识词典】.md#l2-归一化) · [哈希技巧](V2内容社区与RAG详解【知识词典】.md#哈希技巧) · [语义鸿沟](V2内容社区与RAG详解【知识词典】.md#语义鸿沟)

## 6.1 索引链路全景

```
笔记审核通过（或商品上架）
  → RagIndexService.index()
     ├─ upsert 文档头（uk(source_type,ref_id)：同来源只有一份）
     ├─ 删旧切片（替换语义——防脏语料，见 6.2）
     ├─ 切片：300字/片，重叠50字（见 6.3）
     ├─ embed：EmbeddingClient（local 哈希 / spring-ai 真实模型，见 6.4）
     └─ 批量写 t_knowledge_chunk
```

## 6.2 为什么是"删旧写新"而不是追加

笔记会被编辑、商品详情会更新。追加式索引会积累多版本切片——**已下架/已修正的旧内容继续被检索出来**。[脏语料](V2内容社区与RAG详解【知识词典】.md#脏语料)比没语料更糟（AI 会一本正经引用已下架的商品）。所以 index 是**替换语义**：删旧 → 切新 → 写新，一个事务里完成。笔记下架时调 remove 整体移除。

## 6.3 切片：固定长度 + 重叠（逐行读 split）

```java
// RagIndexServiceImpl.split
private List<String> split(String text) {
    List<String> pieces = new ArrayList<>();
    String normalized = text.strip();
    if (normalized.length() <= KnowledgeChunk.CHUNK_SIZE) {   // 300 字内不切
        pieces.add(normalized);
        return pieces;
    }
    int step = KnowledgeChunk.CHUNK_SIZE - KnowledgeChunk.CHUNK_OVERLAP;  // 步长=300-50=250
    for (int start = 0; start < normalized.length(); start += step) {
        int end = Math.min(start + KnowledgeChunk.CHUNK_SIZE, normalized.length());
        pieces.add(normalized.substring(start, end));         // 每片取 300 字
        if (end >= normalized.length()) break;
    }
    return pieces;
}
```

**为什么要 [重叠](V2内容社区与RAG详解【知识词典】.md#重叠-overlap)（overlap）**——关键句可能恰好横跨切片边界：

```
无重叠： "...跑步出汗也没 | 坏，续航很顶..."   ← "出汗也没坏"被切断，两边都语义残缺
有重叠： "...跑步出汗也没 | 出汗也没坏，续航..."  ← 关键句完整出现在第二片里
```

重叠保证**任何一句话至少完整存在于一个切片中**。步长 = 切片长 - 重叠。为什么切 300 字：检索粒度（片段级命中，不整篇塞）与上下文效率的平衡；更优的语义切分/父子切片知道即可，固定长度+重叠是性价比最高的起步方案。

## 6.4 EmbeddingClient：一个现实约束催生的可插拔设计

**现实约束**：本项目大模型是 DeepSeek，而 **DeepSeek 官方不提供 embedding 接口**。要真实语义向量得另配 embedding 服务（OpenAI/Ollama/硅基流动）。如果代码硬依赖某个 embedding 服务，没配它的机器 RAG 整个跑不起来——违背"任何环境都能启动"的硬约束。

于是抽象接口 + 双实现（与 StorageService、PayChannel 同族的**第四次**可插拔）：

```java
public interface EmbeddingClient {
    float[] embed(String text);   // 文本 → 已 L2 归一化的向量
    int dimension();              // 维度必须恒定（否则余弦不可比）
    String name();                // 日志排查用
}
```

**默认实现 LocalHashEmbeddingClient（[哈希技巧](V2内容社区与RAG详解【知识词典】.md#哈希技巧)）逐段精读**：

```java
public float[] embed(String text) {
    float[] vec = new float[dimension];                    // 256 维
    String norm = text.toLowerCase().replaceAll("\\s+", "");  // 归一化：空白无语义
    if (norm.length() == 1) {
        add(vec, String.valueOf(norm.charAt(0)), 1f);      // 单字退化（否则全 0 向量必失配）
    } else {
        // 主路径：滑动窗口切 2-gram——与 MySQL ngram 的 2-gram 口径一致！
        // 两个通道的"词"粒度对齐，融合才有意义
        for (int i = 0; i + 2 <= norm.length(); i++) {
            add(vec, norm.substring(i, i + 2), 1f);
        }
    }
    return l2Normalize(vec);                               // 归一化后点积=余弦
}

private void add(float[] vec, String gram, float weight) {
    int bucket = Math.floorMod(gram.hashCode(), dimension);  // floorMod：hashCode 可能为负
    vec[bucket] += weight;
}
```

**诚实定位（面试必背）**：它能捕获**字面重叠**（"降噪耳机"≈"耳机降噪"——共享大量 2-gram），**不能捕获语义**（"续航久"与"电池耐用"字面零重叠→向量不相似，而真实 embedding 模型知道它们是一回事）。所以它是**降级实现**：保证链路可跑、可演示、可验证工程正确性；语义质量靠关键词通道托底 + 接真实模型升级（改一行配置 `aimall.rag.embedding=spring-ai`，业务零改动）。

**[L2 归一化](V2内容社区与RAG详解【知识词典】.md#l2-归一化)的巧劲**：embed 出口统一把向量除以自身模长（变单位向量）→ 之后**点积 = 余弦相似度**，省一次模长计算——细节处的性能意识。

## 本章动手作业

1. `POST /api/v1/ai/index-all` 后查库：`SELECT doc_id, COUNT(*) FROM t_knowledge_chunk GROUP BY doc_id` 看每篇切片数；找一篇 700 字的笔记验证切成 3 片（300/300/100，其中 50+50 重叠）。
2. 手算一遍："续航久" 的 2-gram 是哪几个？（续航/航久）"电池耐用"呢？（电池/池耐/耐用）——零重叠，所以 local 哈希认为它们不相似。这就是"字面 vs 语义"的亲手实证。
3. 思考：为什么两份语料不能用不同的 EmbeddingClient 混着算？（答：不同实现/维度的向量空间不可比，余弦相似度无意义——"同一系统同一模型"是 embedding 的铁律。）

---

# 第 7 章 检索链路：双通道 + RRF 融合（本章最重）

> 核心机制：[Hybrid 检索](V2内容社区与RAG详解【知识词典】.md#hybrid-检索) · [BM25](V2内容社区与RAG详解【知识词典】.md#bm25) · [余弦相似度](V2内容社区与RAG详解【知识词典】.md#余弦相似度) · [RRF](V2内容社区与RAG详解【知识词典】.md#rrf) · [Top-K 与 Candidate-K](V2内容社区与RAG详解【知识词典】.md#top-k-与-candidate-k) · [Candidate-K](V2内容社区与RAG详解【知识词典】.md#top-k-与-candidate-k)

## 7.1 先想清楚：为什么要两路

拿 5.4 的语义鸿沟接着推——两类问题在单通道下各有一死：

| | 关键词通道（ngram/BM25） | 向量通道（embedding 余弦） |
|---|---|---|
| 擅长 | **精确词**：型号"蓝牙5.3"、"IPX7"、品牌名 | **语义模糊**："戴久了舒服吗"≈"长时间佩戴体验" |
| 死穴 | 换说法就失配（"续航"搜不到"电池耐用"） | 专有名词/型号易失配（embedding 对生僻 token 不敏感） |
| 死穴场景 | 电商高频！问型号答不上 | 种草高频！问体验答不上 |

**电商两类问题都高频——所以必须两路都要，再融合**。这是 2026 年 RAG 的生产基线（业界共识：Hybrid 比单路召回率普遍高 10~20%）——"两小时工作量换双保险"。

## 7.2 检索主流程（HybridRetriever.retrieve 逐段精读）

```java
public List<RetrievedChunk> retrieve(String query) {
    if (!properties.isEnabled() || query == null || query.isBlank()) {
        return List.of();                                    // 开关 + 空查询防御
    }
    // ① 关键词通道：MySQL FULLTEXT(ngram) → Top-20
    List<KnowledgeChunk> byKeyword =
            safe(() -> knowledgeMapper.selectByKeyword(query.trim(), properties.getCandidateK()));

    // ② 向量通道：全量加载已向量化切片，内存算余弦 → Top-20
    List<KnowledgeChunk> byVector = retrieveByVector(query);

    // ③ RRF 融合两路排名
    Map<Long, Float> scores = new HashMap<>();
    Map<Long, KnowledgeChunk> uniq = new HashMap<>();
    accumulate(scores, uniq, byKeyword);                     // 按排名累加 1/(k+rank)
    accumulate(scores, uniq, byVector);

    // ④ 按融合分排序，截断 Top-5（topK）
    return scores.entrySet().stream()
            .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
            .limit(properties.getTopK())
            .map(e -> toChunk(e.getKey(), e.getValue(), uniq.get(e.getKey())))
            .toList();
}
```

三处设计点：
- **`safe()` 包装每个通道**——任一通道抛异常（比如向量键损坏）返回空列表，另一通道托底。检索链路延续"可降级"传统：**单通道故障不炸整个问答**。
- **两路各取 [Candidate-K](V2内容社区与RAG详解【知识词典】.md#top-k-与-candidate-k)=20**——融合需要足够候选池才能体现"多路确认"；最终才截断 Top-K=5（进 prompt 的预算约束）。
- **向量通道全量加载内存算余弦**——数千切片毫秒级；上万后换向量库（ANN 查询），只改 VectorRetriever 实现，业务不动（诚实边界）。

## 7.3 RRF：一行公式与三个为什么

[RRF](V2内容社区与RAG详解【知识词典】.md#rrf)（Reciprocal Rank Fusion）公式：

```
score(文档d) = Σ ( 各路 ) 1 / (k + rank_i(d))       k = 60（论文推荐值）
```

**为什么用排名不用分数（第一为什么）**——BM25 分可以上百、余弦 ∈ [0,1]，**量纲不同不能直接加权**（"拿厘米和斤相加"）。RRF 只看名次，天然免疫量纲。

**为什么双路命中自动加分（第二为什么）**——一个片段两路都进了前 20：`1/61 + 1/61` 得分叠加 → 自动排到最前。这就是**双确认机制**：字面（BM25 命中）+ 语义（向量命中）都说"相关"的片段最可信——RRF 不需要显式写"两路都命中的加权"逻辑，公式天然涌现这个性质。

**为什么 k=60（第三为什么）**——k 压平"第 1 名与第 2 名"的差距（1/61 vs 1/62，差距 1.6%）——避免某一路的第 1 名碾压另一路的所有结果；k 越小排名权重越大，k 越大越平滑。60 是原论文推荐值，实践直接用。

对比"分数加权融合"（把两路分数归一化后加权和）：需要调权重超参、两路的分数量纲问题依然存在——**RRF 用更少的假设拿到更稳的效果**，这就是它成为 Hybrid 默认选择的原因。

## 7.4 单通道质量实测口径（诚实汇报）

local 哈希向量下：向量通道只贡献"字面重叠"级别的召回——整体召回主要由关键词通道托底；接真实 embedding 后向量通道才有语义召回能力。**面试这样讲不是示弱，是"知道自己系统每一部分的边界"**——比硬吹"我做了向量检索"可信得多。

## 本章动手作业

1. 问 AI"XX 支持 IPX7 防水吗"——观察日志走了 searchDocs 且关键词通道命中（精确型号词场景）。
2. 问"戴着跑步会掉吗"——看两路召回与融合排序（日志有 RAG 检索行）。
3. 把 `aimall.rag.rrf-k` 改成 5 和 500 各试一次同一问题——感受 k 对排序的影响（k=5 第一名权重更大）。
4. 手算：片段 A 双路都排第 1（1/61+1/61≈0.0328），片段 B 单路第 1（1/61≈0.0164）——A 的分是 B 的两倍。这就是双确认。

---

# 第 8 章 Agent 分流：双工具，模型自己选

> 核心机制：[双工具路由](V2内容社区与RAG详解【知识词典】.md#双工具路由) · [工具描述工程](V2内容社区与RAG详解【知识词典】.md#工具描述工程) · [来源标注](V2内容社区与RAG详解【知识词典】.md#来源标注) · [函数调用](AI模块详解-SpringAI从零到实战【知识词典】.md#function-calling)

## 8.1 从"单手"到"双手"

V1 的模型只有 searchProduct 一只手（[函数调用](AI模块详解-SpringAI从零到实战【知识词典】.md#function-calling)），"防水吗"这类问题它只能硬编。V2 注册第二个工具：

```java
@Tool(description = "检索商品知识库（官方说明书+真实用户种草笔记）：返回与问题相关的知识片段及其来源。"
        + "当用户询问商品的使用体验、佩戴感受、功能细节、防水续航等说明性/体验性问题时调用；"
        + "价格、库存、是否有货等实时信息不要用本工具（用 searchProduct）。")
public String searchDocs(@ToolParam(description = "用户的自然语言问题") String question) {
    ...
}
```

`AiConfig` 装配（两连 defaultTools）：

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder,
                             ProductSearchTool searchTool,
                             KnowledgeSearchTool knowledgeTool) {
    return builder
            .defaultTools(searchTool)      // 结构化：价格/库存/分类 → SQL
            .defaultTools(knowledgeTool)   // 非结构化：说明书/笔记 → Hybrid RAG
            .build();
}
```

## 8.2 为什么不写 if-else（核心思想）

规则路由的思路：代码里判断"问题含'价格'→走A，含'防水'→走B"。问题：**自然语言意图无法枚举**——"这玩意儿贵吗""预算内有没有替代款""值不值"都是价格意图，规则永远挂一漏万。

Agentic 的思路：**把两份"工具说明书"（description）发给模型，让模型自己选**。模型的语义理解力就是路由器——"贵吗"它知道是问价、"戴着疼吗"它知道是问体验。代码里零意图判断：

```java
// ChatServiceImpl 里根本没有 if(isPriceQuestion) 这种东西——
// 分流发生在模型侧，代码只负责提供两只手并描述清楚各自干什么
answer = chatClient.prompt().system(textSystemPrompt()).user(...)...;
```

**system prompt 定总纲 + 工具 description 定分工**（"总-分"两级引导）：

```
system：涉及价格、库存、找商品时先调 searchProduct（实时数据）；
        涉及使用体验、佩戴感受、功能细节时先调 searchDocs（说明书+真实用户笔记）；
        基于工具返回结果如实回答并注明来源。
searchProduct 的 description：…当用户问商品、价格…时调用
searchDocs 的 description：…价格、库存等实时信息不要用本工具（用 searchProduct）★负向引导
```

注意 searchDocs 描述里的**负向引导**（"价格不要用本工具"）——防止工具"抢跑"（两个工具都能沾边时模型可能挑错）。这是 V1 踩过"该调不调"坑之后（[工具描述工程](V2内容社区与RAG详解【知识词典】.md#工具描述工程)）的对称经验：**正向说清何时调，负向说清何时不调**。

## 8.3 来源标注：RAG 从黑盒变白盒

searchDocs 的返回片段带 `source: 官方说明书/用户种草笔记` 字段 + system prompt 要求"回答注明来源"。用户看到的回答是"据官方说明，IPX7 防水；另有用户反馈……"——**可验证、可信度可感知**，这是 RAG 产品化的关键一步（[来源标注](V2内容社区与RAG详解【知识词典】.md#来源标注)）。

## 本章动手作业

1. 问 AI"降噪耳机多少钱" → 日志确认走 searchProduct；问"戴久了耳朵疼吗" → searchDocs；连续追问"那它防水吗"（同一会话）→ 模型在双手间切换。
2. 故意问一个模糊问题"这耳机怎么样"——看模型怎么选（可能两个都调——允许，它要综合信息）。
3. 思考：为什么把分流交给模型而不是更"可控"的规则？（答：可控是错觉——规则覆盖不了长尾意图，而模型的语义理解就是为这个长的。失控风险用工具描述的负向引导约束。）

---

# 第 9 章 AI 审核 + MQ：发布秒回、异步回写、幂等三防线

> 核心机制：[异步解耦](V2内容社区与RAG详解【知识词典】.md#异步解耦) · [DirectExchange](V2内容社区与RAG详解【知识词典】.md#directexchange) · [死信队列 DLX](V2内容社区与RAG详解【知识词典】.md#死信队列-dlx) · [消费端幂等](V2内容社区与RAG详解【知识词典】.md#消费端幂等) · [毒消息](V2内容社区与RAG详解【知识词典】.md#毒消息) · [消息只带 id](V2内容社区与RAG详解【知识词典】.md#消息只带-id) · [事务外发消息](V2内容社区与RAG详解【知识词典】.md#事务外发消息) · [先发后审](V2内容社区与RAG详解【知识词典】.md#先发后审-vs-先审后发)

## 9.1 为什么这个设计"企业味"足：先算一笔体验账

AI 审核一次 3~10 秒。同步做法：用户点"发布"→ 请求挂着 10 秒 → 返回结果。用户第三次点发布时就卸载了。

异步做法（本项目）：

```
用户点"发布"
  → 笔记落库 status=AUDITING ──秒回──→ 前端显示"审核中"
  → AuditService.submit()
      ├─ MQ 可用：发消息(note.audit) → Broker(durable) → AuditConsumer
      └─ MQ 不可用：本地线程池异步（降级，体验一致）
              │
              ▼
      AuditAiClient.review(低温度 0.1，强制 JSON 判定)
              │
      ┌───────┴────────┐
   pass=true          pass=false / AI 挂了
      │                 │
  流水(PASS)+状态机更新   流水(REJECT 带原因 / ERROR)
  → PUBLISHED            → REJECTED / 停在 AUDITING（不误杀）
  → RAG 索引该笔记
```

**发布接口的 RT 与审核耗时彻底无关**——用户体验由架构保证，不靠祈祷 AI 快。这就是 [异步解耦](V2内容社区与RAG详解【知识词典】.md#异步解耦)：MQ 的第一个真实业务理由。

## 9.2 MQ 拓扑与三个决策

`AuditMqConfig`：DirectExchange + 审核队列 + 死信队列（V3 延迟消息会复用 DLX 思想）。

- **为什么 DirectExchange**——当前只有一种消息，精确路由最简单；Topic 留给"note.* / order.#"通配需求的将来（按需演进）。
- **为什么有 DLX**——消费端重试耗尽的消息进死信队列而不是丢弃。**失败可被发现**优于失败被无限重试（[毒消息](V2内容社区与RAG详解【知识词典】.md#毒消息)防线）。
- **消息只带 id**（[消息只带 id](V2内容社区与RAG详解【知识词典】.md#消息只带-id)）——消息体只有 noteId，消费时回查最新内容：消息在队列躺 5 分钟、笔记被编辑过，审核的仍是最新版；且消息体小、敏感内容不进 Broker。

## 9.3 消费端：幂等三防线（本章核心）

MQ 的语义是 **at-least-once**（至少一次）——重复投递是常态不是异常。`auditOnce` 三防线：

```java
// 防线②的 SQL 本体：
UPDATE t_note SET status = #{newStatus}, audit_result = #{auditResult}
WHERE id = #{id} AND status = #{expectStatus}    -- ★ 只有 AUDITING 才能变
```

| 防线 | 机制 | 挡什么 |
|---|---|---|
| ① 流水表 uk(biz_type,biz_id,biz_version) + INSERT IGNORE | 重复消息第二次插入返回 0 行 → 直接跳过 | MQ 重投（最常见） |
| ② 笔记状态机条件更新（WHERE status='AUDITING'） | 返回 0 行=状态已被别人改过 → 忽略 | 并发审核/编辑后重审 |
| ③ AI 失败只记 ERROR 不动笔记 | 笔记停留 AUDITING 等人工 | **不误杀**（宁可晚发布，不能错杀正常用户） |

**防线③是价值观**：审核系统**宁可不作为，不可乱作为**——误杀正常用户的伤害（体验/流失）远大于晚发布几分钟。这一条讲出来，面试官知道你想过审核系统的本质。

**毒消息防护**：消费端 catch 全部异常、不重入队（`default-requeue-rejected: false`）。必然失败的消息（AI 服务挂了）重试一万次也不会成功，只会打爆日志。兜底 = ERROR 流水 + DLQ 收容 + 未来的补偿任务。"失败可发现"优于"失败无限重试"。

## 9.4 事务外发消息：V2 踩过的坑

发布在 `@Transactional` 里，审核 submit 发 MQ——**消费者可能在事务提交前回查**（查不到笔记 = 幽灵消息）。本项目把 submit 放在事务边界后调用（Controller 层）；V3 的订单延迟消息升级为 `afterCommit` 注册（同一问题的标准解法，见 [V3 详解 8.6](V3支付与Redis详解.md)）。通用原则：**MQ 不参与 DB 事务，事务内发消息永远有"回滚但消息已飞"的窗口**（[事务外发消息](V2内容社区与RAG详解【知识词典】.md#事务外发消息)）。

## 9.5 审核提示词：三个温度的第三副面孔

```java
String system = "你是电商平台的内容安全审核员。审核用户发布的种草笔记，判断是否违规。"
        + "违规类目：色情低俗 / 广告引流(联系方式、外链、代购) / 辱骂攻击 / 政治敏感 / 违禁品。\n"
        + "只输出 JSON：{\"pass\":true/false,\"reason\":\"理由(30字内)\",\"categories\":[...]}\n"
        + "轻微营销话术（如'太好用了绝绝子'）不算违规；拿不准时倾向 pass。";
// 调用时 .options(temperature 0.1)
```

三个设计点：①**分类标准显式列出**（不让模型自由发挥）；②**强制 JSON**（解析做防御——模型可能带 markdown 围栏，从文本里抠 `{...}`）；③**拿不准倾向 pass**（[宁漏勿杀](V2内容社区与RAG详解【知识词典】.md#宁漏勿杀)）。

温度对照（本项目同一模型三个温度）：**审核 0.1**（判定要确定）/ 导购 0.7（平衡）/ 文案 0.8（要灵气）——面试聊 temperature 别只会背定义，用自己项目的三处实例讲"按任务性质选"。

## 本章动手作业

1. 发布一篇笔记，掐表——接口秒回；几秒后刷新详情变"已发布"（异步回写实证）。
2. 发布一篇带"加微信 xxx 代购"的笔记 → 驳回且能看到原因（audit_result）。
3. 停掉 RabbitMQ 再发一篇——照常秒回、照常过审（线程池降级），日志有"MQ 不可用"WARN。
4. `SELECT * FROM t_audit_record WHERE biz_id=刚才那篇` ——每次审核一条流水（留痕实证）。
5. 思考：如果 AI 审核接口超时 30 秒，发布体验会怎样？（答：完全不受影响——这就是异步；受影响的是审核完成时间。）

---

# 第 10 章 AI 种草文案：RAG 参与创作的内容飞轮

> 核心机制：[内容飞轮](V2内容社区与RAG详解【知识词典】.md#内容飞轮) · [AI 不直写业务数据](V2内容社区与RAG详解【知识词典】.md#ai-不直写业务数据)

## 10.1 与普通"帮我写文案"的本质区别

```
普通做法：商品参数 → prompt → AI → 干巴巴的说明书复读
本项目：  用户 brief + 商品信息 + RAG 检索的真实用户反馈 → 有细节的文案
```

`NoteGenerateService.generate` 的素材组装（节选）：

```java
// ① RAG 检索真实用户反馈当参考素材（V2 的灵魂）
List<RetrievedChunk> chunks = ragRetrievalService.retrieve(req.getBrief());
if (!chunks.isEmpty()) {
    // 取前 3 条片段拼进 prompt："其他用户的真实体验（可参考细节，但别照抄）：..."
}

// ② 创作 prompt：文体特征显式化
String system = "你是小红书风格的种草笔记写手。写作要求：\n"
        + "1. 第一人称、场景化开头（通勤/约会/宿舍等真实场景）\n"
        + "2. 有具体细节（用了多久、什么感受、和什么对比过），不写空话\n"
        + "3. 语气真诚分享，不硬广不浮夸\n"
        + "4. 只输出 JSON：{title, content, tags}";

// ③ 温度 0.8——创作任务要发散有灵气（对照审核的 0.1）
```

生成的文案带真实使用细节（"通勤地铁上开降噪，世界瞬间安静"）——因为**喂给它的素材就是真实用户写的话**。这就是[内容飞轮](V2内容社区与RAG详解【知识词典】.md#内容飞轮)的闭环：UGC → 语料 → 更好的新 UGC。

## 10.2 红线：AI 只出草稿

接口返回 `editable: true` 提醒前端；用户编辑后走**正常发布+审核流程**。[AI 不直写业务数据](V2内容社区与RAG详解【知识词典】.md#ai-不直写业务数据)——未经审核的 AI 内容直接公开是 UGC 平台事故（AI 幻觉生成的虚假体验=虚假宣传）。与审核章的"宁漏勿杀"同一条价值观：**AI 的产出永远经过人的确认与流程的把关**。

## 本章动手作业

1. 在发布页用 AI 起草（brief 写"通勤降噪耳机 预算500"），观察生成的文案是否带场景细节。
2. 对比：同一 brief 跑两次——temperature 0.8 下两版文案明显不同（发散性实证）；再把温度临时改成 0.1，两版几乎一样。
3. 思考：为什么不直接把"AI 生成的笔记"自动发布？（答：审核管线自己也得审 AI——AI 可能编造体验；且用户责任主体不能是 AI。）

---

# 第 11 章 模拟数据：让 500 篇笔记长得像真的

> 核心机制：[幂律分布](V2内容社区与RAG详解【知识词典】.md#幂律分布)

## 11.1 为什么均匀假数据一眼假

100 商品每条评论数都是 35、每篇笔记点赞都是 200——**没有梯度、没有爆款、没有长尾**，热门榜排不出名次，RAG 语料里全是四平八稳的好评（没信息量）。真实社交内容的形态是[幂律分布](V2内容社区与RAG详解【知识词典】.md#幂律分布)：少数爆款 + 长尾大多数。

## 11.2 生成器的四个分布设计（gen_mock_data.py）

| 维度 | 做法 | 为什么 |
|---|---|---|
| 品类 | 8 类不等量（数码 22/文具 8） | 真实 mall 品类天然不均 |
| 口碑 | **55% 笔记含缺点段**（"戴久了耳道有点胀"） | 全好评语料没信息量，RAG 检索不出"真实感"；有缺点才有"值不值"的讨论 |
| 互动 | 幂律：10% 爆款(800~5000 赞) + 90% 长尾(3~300) | 热门榜要有梯度 |
| 时间 | 三角分布：越近越密 | 社区有"生长感" |

固定种子（20260906）保证**可复现**——排查问题时"同一份数据"是前提。

## 本章动手作业

1. `SELECT like_count FROM t_note ORDER BY like_count DESC LIMIT 20` 看头部；再 `ORDER BY like_count LIMIT 20` 看长尾——两极分明的幂律。
2. `SELECT COUNT(*) FROM t_note WHERE content LIKE '%但是%'` ——约 55% 含缺点段。
3. 想一个还会被均匀假设坑到的场景（如：全部笔记同一秒 created_at → 时间排序游标分页退化）。

---

# 第 12 章 验收清单 + 面试速查 + 报错速查

## 12.1 全链路验收清单

```bash
# 0. 环境：mysql(192.168.6.102) + 可选 rabbitmq(5673)；MQ 不起走线程池降级（本身是验收项）

# 1. 启动 → Flyway 执行 V2__content_and_rag.sql（10 张新表）
# 2. 灌数据：mysql < sql/mock_data.sql（幂等可重跑）
# 3. 建索引：登录后 POST /api/v1/ai/index-all（后台跑，看日志"批量索引完成"）

# 4. 社区功能
#    [ ] /notes 瀑布流 500 篇；加载更多（游标翻页）；热门/最新切换
#    [ ] 搜索"降噪"命中（ngram）；搜单字不命中（2-gram 粒度）
#    [ ] 详情：点赞红心/收藏/浏览数+1；右侧"文中好物"跳商品页
#    [ ] 并发点赞 10 次 → t_note_like 仅 1 条（幂等）
#    [ ] 下架笔记 → 搜索不再命中（RAG 索引联动移除）

# 5. AI 审核
#    [ ] 正常发布 → 秒回"审核中" → 数秒后 PUBLISHED
#    [ ] 违规内容（加微信代购）→ REJECTED 带原因
#    [ ] 停 MQ 发布 → 照常过审（降级）+ WARN 日志
#    [ ] t_audit_record 有流水

# 6. RAG 双工具分流
#    [ ] "XX多少钱" → searchProduct（SQL）
#    [ ] "XX戴久了耳朵疼吗" → searchDocs（RAG），回答注明来源
#    [ ] 同一会话连续追问，模型在双手间切换

# 7. AI 文案：发布页 AI 起草 → 编辑 → 发布 → 过审
```

## 12.2 面试速查表（30 秒一答）

| 问题 | 一句话答案 |
|---|---|
| 为什么用 MQ | AI 审核秒级耗时 vs 发布必须秒回；异步解耦后审核慢/挂都不影响发布体验 |
| 消息丢了怎么办 | 发送端 publisher-confirm + 队列/消息持久化 + 失败流水留痕可补偿 |
| 重复消费怎么办 | 流水表版本唯一键 + INSERT IGNORE + 状态机条件更新，双防线 |
| RAG 语料是什么 | 商品说明书（官方）+ 种草笔记（UGC）——参数与体验双眼 |
| 为什么不塞长上下文 | 语料动态/首字延迟/权限隔离/成本（四条背熟） |
| 为什么 Hybrid | 精确词（BM25）与语义模糊（向量）电商都高频；RRF 免量纲融合+双确认 |
| 向量存 MySQL？ | 数千切片全量加载内存算余弦为毫秒级；检索层已抽象，切片量上万时换实现不改代码 |
| embedding 为什么默认哈希 | DeepSeek 无 embedding 接口是现实约束；local 保链路可跑（诚实：字面非语义），spring-ai 可切零改业务 |
| 点赞怎么做 | 唯一索引幂等（DuplicateKey 静默）+ 原子自增计数；V3 升 Redis |
| AI 审核出错怎么办 | ERROR 流水+停留审核中+死信/补偿，宁可不作为不可乱作为 |
| 深分页怎么解决 | 游标分页（排序键配套游标，热度排序用二元组），不能跳页——按场景选型 |
| 中文搜索怎么做 | **笔记**：FULLTEXT + WITH PARSER ngram（2-gram 切词），方案完整；**商品**：仍是最简 `LIKE`，属**未完成的缺口**（别说"数据少所以够用"） |

## 12.3 报错速查表

| 症状 | 原因 | 处置 |
|---|---|---|
| 搜索任何词都 0 结果 | 建表没带 WITH PARSER ngram（旧表结构） | 检查 `SHOW CREATE TABLE t_note`；重建索引 |
| 笔记发布后一直"审核中" | MQ 未起且线程池降级失败 / AI key 失效 | 看日志"MQ 不可用"与 AI 调用错误；查 t_audit_record 是否 ERROR 流水 |
| AI 永远不走 searchDocs | 索引是空的（index-all 没跑） | `SELECT COUNT(*) FROM t_knowledge_chunk`；重跑 index-all |
| 重复点赞报 500 而非静默 | DuplicateKey 捕获位置不对/事务回滚 | 检查 try 块范围是否覆盖 insert+incr |
| Feed 翻页后出现重复笔记 | 热度排序游标只传了 id（漏 hotScore） | 检查前端 cursor 状态两个字段都带 |
| index-all 卡住不动 | 真实 embedding 模式下 API 限速 | 看 vits/应用日志；local 模式毫秒级完成 |
| 全文检索中文单字无结果 | ngram_token_size=2 的固有特性 | 换 2 字以上关键词；或调 token_size（需重启 MySQL） |

---

> 读完建议回到顶部把【易错关键点】再背一遍，然后做 12.1 验收清单——**动手过的才是你的**。
> 配套词典：[V2内容社区与RAG详解【知识词典】.md](V2内容社区与RAG详解【知识词典】.md)
> 延伸阅读：[V3支付与Redis详解.md](V3支付与Redis详解.md)（Redis 计数/延迟消息/限量发售是 V2 的续集）
