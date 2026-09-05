# AI 种草商城（ai-mall）

> 面向初级 Java 求职的核心实战项目：内容社区为流量入口、电商交易为变现主干、AI 为智能引擎。
> 完整设计文档：`求职2026-8/项目设计文档.md`（本仓库为 `Projects/ai-mall`）。

## 技术栈（V1）

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.4.5 · Java 17 · MyBatis（XML 手写 SQL）· Sa-Token（无状态 Token） |
| 数据 | MySQL 8（192.168.6.102:3306 / ai_mall） |
| AI | Spring AI 1.0 + OpenCode Go 中转（OpenAI 兼容，模型 `deepseek-v4-pro`，可切换官方 DeepSeek） |
| 语音/动效（可选） | `com.aimall.voice`（纯派蒙 TTS 引擎）+ 前端 `src/voice/`（Live2D 皮套 + TTS 播放队列） |
| 前端 | Vue 3 + Vite + Element Plus + Pinia + axios（SSE 流式对话） |

## 目录结构

```
ai-mall/
├── backend/                  # Spring Boot 单体（按域分包，为 V4 拆服务埋伏笔）
│   └── src/main/java/com/aimall/
│       ├── common/           # R 统一返回 / ResultCode / BusinessException / 全局异常 / 分页
│       ├── config/           # MyBatis / Sa-Token / CORS / BCrypt / Spring AI
│       ├── user/             # 注册/登录/当前用户
│       ├── goods/            # 商品列表/详情/SKU / 购物车（V1 MySQL 版）
│       ├── order/            # 下单（乐观扣库存+事务）/ 订单列表/详情/取消
│       ├── ai/               # 会话管理 / AI 问答（Agent 商品搜索工具 + 图片识别 + SSE 流式）
│       └── voice/            # 独立派蒙 TTS 引擎（可选，POST /api/v1/voice/tts）
├── frontend/                 # Vue3 + Vite 前端
│   └── src/voice/            # Live2D 皮套组件 + TTS 播放队列（可选）
├── sql/init.sql              # 建库建表 + 种子商品
└── scripts/                  # smoke-test.ps1 冒烟测试
```

## 快速启动

```bash
# 1. 初始化数据库（192.168.6.102，root/root123，可改）
mysql -h192.168.6.102 -uroot -proot123 < sql/init.sql

# 2. 启动后端（8080；API Key 从环境变量读取，无默认值）
cd backend && mvn spring-boot:run

# 3. 启动前端（5173，/api 代理到 8080）
cd frontend && npm install && npm run dev
```

打开 http://localhost:5173 ，注册/登录后体验完整闭环。

## 派蒙语音动效助手（可选，依赖受限第三方资产）

V1 基础上新增了"AI 助手语音 + Live2D 动效"：AI 回复后，前端逐句请求 TTS 并播放派蒙音色，同时派蒙 Live2D 皮套做口型/表情/动作。

> ⚠️ **合规红线（务必先读）**
> - 派蒙 Live2D 皮套模型、游戏语音、派蒙 6k VITS 音色模型，均为**第三方 / 他人（miHoYo IP + 社区 Paimon6k 音色）的受限资产**，许可含限制项：**仅限个人 / 学习 / 演示，禁止再分发、公开、商用**。
> - **公开仓库不含这些资产**：`.gitignore` 已忽略 `frontend/public/assets/`（皮套模型）、`frontend/public/vendor/`（Live2D 核心库）与 `voice-tts/paimon6k_*.pth`、`voice-tts/paimon6k.json`、`voice-tts/MoeGoe/`（派蒙 VITS 模型/库），克隆后不会带入。
> - 若未来要**公开或商用**，请先经"人设层"（`frontend/src/voice/voiceStage.vue` 可配置的 `modelUrl` / 表情表 / 动作组 / 音色）替换为**自主授权或自有**角色 / 音色。

**无资产也能正常运行（默认）**
- 克隆后若不提供皮套：前端**自动降级** —— 不显示派蒙舞台（或显示轻量"皮套未安装"提示），**聊天、图片、工具检索、会话全部正常**，页面不白屏。
- 若机器**无 Python / 无内嵌派蒙模型 / 无依赖**：后端**照常启动**（不拉起 VITS），**TTS 自动回退**到本地纯 Java Edge-TTS（仍能出声，只是非派蒙音色），聊天不受影响。

**启用派蒙声音 + 动效（需自行获取上述受限资产）**

1. **皮套资产**：把派蒙 Live2D 模型放入 `frontend/public/assets/model/`（含 `*.model3.json / *.moc3 / *.physics3.json / expressions/ / motions/ / 贴图`），并把 `live2dcubismcore.min.js` 放入 `frontend/public/vendor/`。（来源：`PaimonLiveWeb5` 项目，或自行获取授权/自有模型。）
2. **派蒙音色**：后端已**自包含派蒙 VITS 服务**（`voice-tts/`），启动时会**自动拉起**，无需手动启动外部 PaimonLiveWeb5：
   - 仓库内已含 `voice-tts/vits_server.py` + `voice-tts/requirements.txt`（入库）；**模型与 MoeGoe 库为本地受限资产、不入库**（见 `.gitignore`），需自行放到 `voice-tts/`：
     - `voice-tts/paimon6k_390k.pth`（派蒙 6k VITS 模型，~429MB）
     - `voice-tts/paimon6k.json`（模型配置）
     - `voice-tts/MoeGoe/`（MoeGoe 推理库）
   - **运行前提（Python 依赖不随 ai-mall 打包，需用户自装）**：本机需装 **Python 3.x**，并运行 `pip install -r voice-tts/requirements.txt`（torch/fastapi/uvicorn/soundfile 等，版本见该文件）；再把 `aimall.voice.tts.vits.python` 配成装好依赖的解释器命令（如 `G:\tts\env\python.exe`）。缺任一项（Python/依赖/模型）时后端**照常启动**、**TTS 自动回退 Edge-TTS**。
   - 配置（`backend/src/main/resources/application.yml`）默认已指向本机内嵌 VITS：
     ```yaml
     aimall:
       voice:
         tts:
           backend-url: http://127.0.0.1:9944/tts     # 本机内嵌 VITS（后端自动拉起）
           vits:
             port: 9944
             python: python                            # 换成装好依赖的解释器命令（如 G:\tts\env\python.exe）
     ```
   - 后端启动时 `VitsLifecycle` 检测到 Python + 模型即自动 spawn `voice-tts/vits_server.py`，轮询 `/health` 就绪后把 TTS 指向本机 VITS；**无 Python/模型/依赖时自动跳过并回退 Edge-TTS**。
3. 前后端照常启动（见"快速启动"），聊天即自动带派蒙语音 + 动效。

## V1 验收清单（已全部通过）

| 项 | 结果 |
|---|---|
| 注册 → 登录（Sa-Token Token + BCrypt） | ✅ |
| 商品列表（含起售价聚合）/ 详情（SKU） | ✅ |
| 加购物车 → 购物车列表/改数量/删除 | ✅ |
| 下单（乐观扣库存 `stock>=?` + @Transactional + 订单快照） | ✅ |
| 订单列表 / 详情 / 取消（回补库存） | ✅ |
| AI 问答：Agent 商品搜索工具（Function Calling）+ 图片识别 + SSE 流式输出 | ✅（deepseek-v4-pro / vision） |
| 全链路冒烟测试 `scripts/smoke-test.ps1` 9/9 | ✅ |
| 派蒙语音动效助手（皮套/音色资产就位时） | ✅ |

## 面试可讲点（V1）

- 请求链路：Controller → Service（构造器注入）→ Mapper 接口（@Mapper）→ XML SQL
- 统一返回 `R{code,msg,data}` + `BusinessException` + 全局异常兜底（不泄漏堆栈）
- MyBatis 手写分页 `LIMIT offset,size` + `PageResult`（为何不引分页插件）
- 下单防超卖：`UPDATE sku SET stock=stock-? WHERE id=? AND stock>=?`（CAS 式乐观扣减）
- Sa-Token 无状态 Token + 拦截器白名单
- Spring AI 流式响应（SSE text/event-stream，前端 fetch 逐块渲染）
- **Agent Function Calling**：AI 不确定商品时按需调 `searchProduct` 工具（复用业务 Service，与前端搜索同源），不编造
- **多模态图片识别**：canvas 压缩 → base64 → `UserMessage.builder().media()` → 视觉模型
- **TTS + Live2D 动效（派蒙可选）**：前端按句切分 → 逐句 `/api/v1/voice/tts` → 顺序播放 + Live2D 口型/表情/动作；模块独立（`com.aimall.voice` + `src/voice/`），低耦合、可插拔

## 演进预告

- **V2**：RAG 导购（商品文档分块+向量检索）、支付宝/微信沙箱支付、内容笔记发布
- **V3**：Redis（缓存三兄弟/购物车迁移/排行榜）、RabbitMQ（审核异步/订单事件）、秒杀
- **V4**：按域拆微服务（user/content/goods/order/ai）+ Nacos/Gateway/Feign + 分布式事务
- **V5**：Agent 客服（Function Calling）、推荐系统、NL2SQL 数据分析

> ⚠️ `application.yml` 的 AI 中转 Key 已改为**环境变量注入**（`${DEEPSEEK_API_KEY:}`，无默认值）；本地运行请先设置该环境变量，勿把真实 Key 提交入库。
