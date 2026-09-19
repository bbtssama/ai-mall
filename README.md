# AI 种草商城（ai-mall）

> 面向初级 Java 求职的核心实战项目：内容社区为流量入口、电商交易为变现主干、AI 为智能引擎。
> 完整设计文档：`项目设计文档.md`（总纲蓝图）。各版本实现讲解见 `docs/版本详解/`。

## 技术栈（V1 → V4 全景）

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.4.5 · Java 17 · MyBatis（XML 手写 SQL）· Sa-Token（无状态 Token） |
| 数据 | MySQL 8（192.168.6.102:3306 / ai_mall）· **Flyway 版本管理**（V1.5，迁移至 V4） |
| 缓存/中间件 | Redis（缓存三兄弟/计数增量桶/排行榜 zset/分布式锁/限流）· RabbitMQ（审核异步/延迟取消/削峰建单） |
| AI | Spring AI 1.0 + 官方 DeepSeek（OpenAI 兼容，`application.yml` 当前模型 `deepseek-v4-flash-vision-exp`；可切换 OpenCode Go 中转，该 base-url 在配置中已注释备用） |
| 服务化（V4） | Nacos 注册发现 + Spring Cloud Gateway + Resilience4j 熔断（仅 AI 独立成服务，主体保持单体） |
| 语音/动效（可选） | `com.aimall.voice`（纯派蒙 TTS 引擎）+ 前端 `src/voice/`（Live2D 皮套 + TTS 播放队列） |
| 前端 | Vue 3 + Vite + Element Plus + Pinia + axios（SSE 流式对话） |

## 目录结构

```
ai-mall/
├── backend/                  # Spring Boot 单体（按域分包，为 V4 拆服务埋伏笔）
│   └── src/main/java/com/aimall/
│       ├── common/           # R 统一返回 / 全局异常 / 分页 / traceId 链路 / Redis 门面 / 补偿任务
│       ├── config/           # MyBatis / Sa-Token / CORS / BCrypt / Spring AI / 线程池 / 调度 / MQ 生产者回调
│       ├── user/             # 注册/登录/当前用户/地址簿
│       ├── goods/            # 商品列表/详情/SKU / 购物车 / 商品详情缓存（缓存三兄弟）
│       ├── content/          # 笔记/Feed/点赞收藏 / 审核MQ / 计数增量桶 / 热榜重建任务
│       ├── order/            # 下单（行锁 CAS+幂等token）/ 订单列表/取消 / 延迟消息(TTL+DLX)
│       ├── pay/              # 支付闭环（验签/幂等/金额核对）+ 限量发售（Redis预减+MQ削峰）
│       ├── ai/               # 会话管理 / AI 问答（Agent 工具 + 图片识别 + SSE 流式）/ RAG
│       └── voice/            # 独立派蒙 TTS 引擎（可选，POST /api/v1/voice/tts）
├── frontend/                 # Vue3 + Vite 前端
│   └── src/voice/            # Live2D 皮套组件 + TTS 播放队列（可选）
├── backend/src/main/resources/db/migration/   # ★ 表结构唯一权威（Flyway V1~V4，只增不改）
├── docs/
│   ├── 版本详解/             # ★ 各版本工程实现讲解（V1.5~V4 详解 + 知识词典，共 8 份）
│   ├── 归档/                 # 历史审核产出（规模借口审核 / 搜索功能梳理）
│   ├── 00-项目文档地图与存废裁决.md  # 全项目文档怎么读、哪些过时（唯一权威索引）
│   └── learning/             # （已归档，见 _archive/）
├── 后端求职/                 # ★ 求职主线：八股试卷 / 面试弹药库 / 简历 C 及超链学习目录
├── 项目设计文档.md           # 总纲蓝图（V1~V5 规划与选型理由）
├── AI模块详解-SpringAI从零到实战.md  # AI 模块教学专题
├── docker-compose.yml        # V1.5：mysql/redis/rabbitmq/minio 一键起
├── .github/workflows/ci.yml  # V1.5：push 自动 build+test
└── scripts/                  # smoke-test.ps1 冒烟测试
```

> ⚠️ **文档位置约定**（2026-09-19 归类）：版本详解统一在 `docs/版本详解/`，求职材料统一在 `后端求职/`。
> 根目录只保留 `README.md` / `AGENTS.md` / `项目设计文档.md` / `AI模块详解-SpringAI从零到实战.md` 四份。
> 找文档先看 `docs/00-项目文档地图与存废裁决.md`。

## 快速启动

```bash
# 0.（可选）一键起依赖中间件：mysql / redis / rabbitmq / minio
docker compose up -d

# 1. 初始化数据库（192.168.6.102，root/root123，可改）
#    ★ 直接启动后端即可：Flyway 自动建表/基线化，无需手动执行任何 SQL
#    （历史上曾有一个 sql/init.sql 便利脚本，因与 Flyway 严重脱节已于 2026-09-19 删除）

# 2. 启动后端（8080；API Key 从环境变量读取，无默认值）
cd backend && mvn spring-boot:run

# 3. 启动前端（5173，/api 代理到 8080）
cd frontend && npm install && npm run dev

# 4.（可选）跑单元测试（V1.5 新增，纯内存毫秒级，无需数据库）
cd backend && mvn test
```

打开 http://localhost:5173 ，注册/登录后体验完整闭环。

## 派蒙语音动效助手（可选，依赖受限第三方资产）

V1 基础上新增了"AI 助手语音 + Live2D 动效"：AI 回复后，前端逐句请求 TTS 并播放派蒙音色，同时派蒙 Live2D 皮套做口型/表情/动作。

### 请求链路（谁调用谁，一图看完）

```
浏览器
  │ fetch('/api/v1/voice/tts')            ← 前端只认 /api 入口，与其他接口无异
  ▼
Vite 代理 (/api → localhost:8080)
  ▼
Spring Boot (8080)  VoiceChatController → VoiceEngineImpl
  │
  ├─ 主路径: POST http://127.0.0.1:9944/tts      ← 内嵌派蒙 VITS（本机 Python 子进程）
  │          由 VitsLifecycle 随后端启动自拉起、关闭时销毁；
  │          只绑 127.0.0.1 回环，外部/浏览器不可见
  └─ 兜底:   Edge-TTS（后端本地合成，非派蒙音色） ← VITS 缺资产/缺依赖/未就绪时自动降级
```

要点：**9944 不是独立部署的服务**，而是后端 `ProcessBuilder` 拉起的内嵌子进程（模型推理依赖 Python 生态，故以子进程内嵌而非独立微服务，不增加部署单元）；前端对它完全无感，VITS 故障不穿透（自动降级）。解释器路径必须显式配置（`aimall.voice.tts.vits.python`），裸写 `python` 会被 PATH 漂移坑（曾因 PATH 上的 python 缺 soundfile 静默降级）。

> ⚠️ **合规红线（务必先读）**
> - 派蒙 Live2D 皮套模型、游戏语音、派蒙 6k VITS 音色模型，均为**第三方 / 他人（miHoYo IP + 社区 Paimon6k 音色）的受限资产**，许可含限制项：**仅限个人 / 学习 / 演示，禁止再分发、公开、商用**。
> - ⚠️ **当前本机工作副本已就位这些资产**（`frontend/public/assets/model/`、`frontend/public/vendor/`、`voice-tts/` 内模型均存在，派蒙音色与动效可完整体验）。
> - **但若要公开仓库/开源分发，必须先移除**：`.gitignore` 已忽略 `frontend/public/assets/`（皮套模型）、`frontend/public/vendor/`（Live2D 核心库）与 `voice-tts/paimon6k_*.pth`、`voice-tts/paimon6k.json`、`voice-tts/MoeGoe/`（派蒙 VITS 模型/库）。换言之：这些资产**在本机有、但不入库**，克隆一份干净仓库不会带入。
> - 若未来要**公开或商用**，请先经"人设层"（`frontend/src/voice/voiceStage.vue` 可配置的 `modelUrl` / 表情表 / 动作组 / 音色）替换为**自主授权或自有**角色 / 音色。

**无资产也能正常运行（默认）**
- 克隆后若不提供皮套：前端**自动降级** —— 不显示派蒙舞台（或显示轻量"皮套未安装"提示），**聊天、图片、工具检索、会话全部正常**，页面不白屏。
- 若机器**无 Python / 无内嵌派蒙模型 / 无依赖**：后端**照常启动**（不拉起 VITS），**TTS 自动回退**到本地纯 Java Edge-TTS（仍能出声，只是非派蒙音色），聊天不受影响。

**恢复派蒙资产（下载来源 + 步骤，可从 GitHub 拉取后补齐到完整）**

1. **皮套 Live2D 模型**（社区同人，⚠️ **禁止再分发/公开/商用**，需自行获取）：
   - 派蒙是 miHoYo 角色，**官方无开放 Live2D**；市面上均为**社区同人资源**，实例来源：
     - B站「YAYA药团子」《派蒙live2d模型分享》`BV1eA4y1X7he`（百度网盘，vts / l2dviewer 可用）
     - B站「根瘤菌rkzj」（社区分享）
     - Steam 创意工坊 Live2DViewerEX「御神灯Goshinto」《【Genshin/原神】派蒙》item `2703407280`（需客户端订阅）
     - 详细来源与许可：`PaimonLiveWeb5/docs/live2d-model-notes.md`
   - ⚠️ 原分享页限定**个人使用、禁止再分发/公开/商业**；请勿将这些模型资产拷出仓库二次分发。
   - **放入**：`frontend/public/assets/model/`（`model.model3.json` + `*.moc3` + `*.physics3.json` + `expressions/` + `motions/` + 贴图 `paimon.4096/`）。
   - `live2dcubismcore.min.js`：Live2D 官方 **CDN 免费分发版**（Proprietary SDK License，仅随运行时使用勿二次分发），放入 `frontend/public/vendor/`。
2. **派蒙 6k VITS 音色模型**（**MIT** 授权可下载，但为派蒙语音衍生 → 仅**个人、非商用**，需保留 MIT 版权声明并标注来源）：
   - **下载**：[HuggingFace `caojiachen1/paimon_tts`](https://huggingface.co/caojiachen1/paimon_tts) → `paimon6k_390k.pth`（约 429MB）+ `paimon6k.json`。
   - **MoeGoe 推理库**：[GitHub `CjangCjengh/MoeGoe`](https://github.com/CjangCjengh/MoeGoe)（MIT，VITS 推理）。
   - **放入**：`voice-tts/paimon6k_390k.pth`、`voice-tts/paimon6k.json`、`voice-tts/MoeGoe/`（这些在 `.gitignore`，不入库）。
   - 派蒙语音数据集（敏感勿公开）：`https://huggingface.co/datasets/umoubuton/paimon`。
3. **运行时前提（Python 依赖不随 ai-mall 打包，需用户自装）**：本机需 **Python 3.x** + `pip install -r voice-tts/requirements.txt`（torch/fastapi/uvicorn/soundfile 等）＋把 `aimall.voice.tts.vits.python` 配成装好依赖的解释器命令（如 `G:\tts\env\python.exe`）。缺任一项（Python/依赖/模型）→ 后端**照常启动**、**TTS 自动回退 Edge-TTS**。
4. **后端启动自动拉起**：`VitsLifecycle` 检测到 Python + 模型，即自动 `spawn voice-tts/vits_server.py`（:9944）并轮询 `/health` 就绪、把 TTS 指向本机 VITS；**无需手动启动外部 PaimonLiveWeb5**。
5. 前后端照常启动（见"快速启动"），聊天即自动带派蒙语音 + 动效。

## V1 验收清单（已全部通过）

| 项 | 结果 |
|---|---|
| 注册 → 登录（Sa-Token Token + BCrypt） | ✅ |
| 商品列表（含起售价聚合）/ 详情（SKU） | ✅ |
| 加购物车 → 购物车列表/改数量/删除 | ✅ |
| 下单（行锁 CAS 式扣库存 `stock>=?` + @Transactional + 订单快照） | ✅ |
| 订单列表 / 详情 / 取消（回补库存） | ✅ |
| AI 问答：Agent 商品搜索工具（Function Calling）+ 图片识别 + SSE 流式输出 | ✅（文本与视觉**共用同一模型** `deepseek-v4-flash-vision-exp`，由 `AiConfig` 装配成 `chatClient` / `visionChatClient` 两条链路） |
| 全链路冒烟测试 `scripts/smoke-test.ps1` 9/9 | ✅ |
| 派蒙语音动效助手（皮套/音色资产就位时） | ✅ |

## V1.5 工程基建（已完成）

> 详见 `docs/版本详解/V1.5工程基建详解.md`（讲解）+ `V1.5工程基建详解【知识词典】.md`（速查）。

| 项 | 内容 |
|---|---|
| **Flyway** | 表结构版本化管理；存量库 `baseline-on-migrate` 接入（新库自动建表、老库自动基线化） |
| **traceId** | MDC + Filter（入口生成/回写响应头 `X-Trace-Id`）+ TaskDecorator（异步线程传递），日志全链路可 grep |
| **日志文件** | logback-spring.xml：按天+50MB 滚动、30天/2GB 封顶、压缩归档 |
| **Actuator** | `/actuator/health`（含 liveness/readiness 探针）+ metrics |
| **对象存储** | `StorageService` 抽象：本地磁盘（默认，零依赖）⇄ MinIO（懒初始化+预签名URL），配置一行切换；UUID 重命名+MIME 白名单（防路径穿越/存储型XSS） |
| **异步线程池** | 有界队列 200 + CallerRuns 背压 + MDC 传递 + 优雅停机（为 V2 AI 审核异步备好） |
| **测试** | 15 个用例：下单扣库存/状态机/越权/上传安全/MDC/分页（Mockito mockStatic 处理 StpUtil，纯内存毫秒级） |
| **环境/CI** | docker-compose 四件套 + GitHub Actions（push 自动 build+test） |

## 面试可讲点（V1）

- 请求链路：Controller → Service（构造器注入）→ Mapper 接口（@Mapper）→ XML SQL
- 统一返回 `R{code,msg,data}` + `BusinessException` + 全局异常兜底（不泄漏堆栈）
- MyBatis 手写分页 `LIMIT offset,size` + `PageResult`（为何不引分页插件）
- 下单防超卖：`UPDATE t_product_sku SET stock=stock-? , sales=sales+? WHERE id=? AND stock>=?`
  —— 靠 **InnoDB 行排他锁 + `WHERE stock>=?` 的 CAS 式条件扣减**（原子、无超卖）。
  ⚠️ 注意：`t_product_sku.version` 目前**只是预留字段、并未用作乐观锁**（代码注释已注明），面试请讲"行锁 CAS"，不要讲"乐观锁"
- Sa-Token 无状态 Token + 拦截器白名单
- Spring AI 流式响应（SSE text/event-stream，前端 fetch 逐块渲染）
- **Agent Function Calling**：AI 不确定商品时按需调 `searchProduct` 工具（复用业务 Service，与前端搜索同源），不编造
- **多模态图片识别**：canvas 压缩 → base64 → `UserMessage.builder().media()` → 视觉模型
- **TTS + Live2D 动效（派蒙可选）**：前端按句切分 → 逐句 `/api/v1/voice/tts` → 顺序播放 + Live2D 口型/表情/动作；模块独立（`com.aimall.voice` + `src/voice/`），低耦合、可插拔

## V2 内容社区 + Hybrid RAG（已完成）

> 详见 `docs/版本详解/V2内容社区与RAG详解.md`（讲解）+ `V2内容社区与RAG详解【知识词典】.md`。

| 项 | 内容 |
|---|---|
| **内容社区** | 笔记发布/Feed（游标分页+MySQL ngram 全文检索）/点赞收藏（唯一索引幂等）/种草清单（笔记关联商品） |
| **AI 审核 + MQ** | 发布秒回→RabbitMQ 异步审核→回写状态；幂等三防线（流水 uk/状态机条件更新/ERROR 不误杀）；MQ 不可用自动降级线程池 |
| **Hybrid RAG** | 语料=商品说明书+种草笔记；BM25+向量双通道，RRF 融合；`searchDocs` 与 `searchProduct` 双工具 Agent 分流（模型自选）；embedding 可插拔（local 哈希默认 / spring-ai 可切） |
| **AI 种草文案** | RAG 检索真实用户反馈参与创作，只出草稿绝不自动发布 |
| **模拟数据** | `scripts/gen_mock_data.py` 生成 100 商品 + 500 笔记（幂律分布/含缺点段），`sql/mock_data.sql` 幂等导入 |

## 演进路线（当前进度）

- **V1.5 工程基建**：✅ 已完成（Flyway / traceId 可观测 / 对象存储抽象 / 测试 / Compose / CI，见上文）
- **V2 内容社区+RAG**：✅ 已完成（笔记/MQ 审核异步/Hybrid RAG/AI 文案/模拟数据，见上文）
- **V3 支付+Redis+MQ**：✅ 已完成（详见 `docs/版本详解/V3支付与Redis详解.md`）
  - Redis：商品详情缓存三兄弟（空值缓存/互斥锁/随机TTL）、计数增量桶（HINCRBY 攒增量+定时落库）、热门榜 zset、AI 限流（Lua 原子固定窗口）
  - RabbitMQ：审核异步、订单超时取消（TTL+DLX 队列级延迟）、限量发售削峰建单；生产者 Confirm/Returns 回调落地
  - 支付闭环：验签/幂等（uk_order_id 一订单一行）/金额核对/主动查单
  - 限量发售：**Redis 预减（Lua 判重+扣减）+ MQ 异步下单削峰**，前端轮询结果
- **V4 服务化**：✅ 已完成（详见 `docs/版本详解/V4服务化详解.md`）：仅独立 ai-service + Gateway + Nacos + Resilience4j 熔断，主体保持单体
- **V5**：Agent 客服（查订单/物流）、相似推荐+热门榜（ItemCF 离线对比）、受限 NL2SQL 商家看板

## 可靠性大修缮（2026-09-09，P0/P1/P2 系统性修复）

一次从"传统后端岗位"视角的全面评审后修复，8 个独立 commit：

| 修复 | 内容 |
|---|---|
| **抢购参数校验** | `quantity` 裸 Map 接参可传负数反向刷库存 → DTO `@Min/@Max` 拦死 |
| **预热竞态** | check-then-set 并发首访可重置库存 → `SET NX` 原子预热 |
| **削峰重构** | 限量发售从"同步建单"改为经典 **Redis 预减 + MQ 异步下单**：Lua 判重+扣减同脚本 → 投递 → 消费端事务建单（CAS+uk）→ 失败三分流（回补/标记/DLQ）；新增 `/drops/{id}/result` 轮询 |
| **秒杀单超时取消** | 抢购订单此前永不超时（死单占库存）→ `OrderDelayMessageSender` 统一出口，与普通下单同款 30min 延迟取消 |
| **定时补偿三合一** | `CompensationTask`：订单超时兜底 / 审核堆积重送 / 支付查单对账——异步链路的最终一致性收敛 |
| **MQ 生产者确认** | confirm/returns 配了但没人消费（摆设）→ `RabbitTemplateCustomizer` 注册回调；取消队列补死信 |
| **消费端事务边界** | `@Transactional` 内吞异常导致半截事务提交 → 异常冒出代理回滚 + DLQ |
| **写放大治理** | 计数（浏览/点赞/收藏）从"每次互动 3 次 DB 写"改为 Redis HINCRBY 攒增量 + 60s 定时落库；热度刷新收敛到落库路径 |
| **热门榜** | Top50 循环逐条查（N+1）→ IN 批量；zset 定时全量重建（冷启动自愈） |
| **支付幂等** | "查完再插"并发产生两张 PAYING 单 → `uk_order_id` 一订单一行 + 冲突回查复用 |
| **下单幂等** | 双击提交=两张单 → 一次性 token（签发/DEL 原子消费，Redis 不可用 fail-open） |

> ⚠️ `application.yml` 的 AI 中转 Key 已改为**环境变量注入**（`${DEEPSEEK_API_KEY:}`，无默认值）；本地运行请先设置该环境变量，勿把真实 Key 提交入库。
> ⚠️ V4 迁移改了 `t_payment` 唯一索引、取消队列加了 DLX 参数：旧环境需先 `rabbitmqadmin delete queue name=aimall.order.cancel.queue` 再启动（队列参数不可变）。
