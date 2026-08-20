# AI 种草商城（ai-mall）

> 面向初级 Java 求职的核心实战项目：内容社区为流量入口、电商交易为变现主干、AI 为智能引擎。
> 完整设计文档：`求职2026-8/项目设计文档.md`（本仓库为 `Projects/ai-mall`）。

## 技术栈（V1）

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.4.5 · Java 17 · MyBatis（XML 手写 SQL）· Sa-Token（无状态 Token） |
| 数据 | MySQL 8（192.168.6.102:3306 / ai_mall） |
| AI | Spring AI 1.0 + OpenCode Go 中转（OpenAI 兼容，模型 `deepseek-v4-pro`，可切换官方 DeepSeek） |
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
│       └── ai/               # 会话管理 / AI 问答（预置商品知识 + SSE 流式）
├── frontend/                 # Vue3 + Vite 前端
├── sql/init.sql              # 建库建表 + 种子商品
└── scripts/                  # smoke-test.ps1 冒烟测试
```

## 快速启动

```bash
# 1. 初始化数据库（192.168.6.102，root/root123，可改）
mysql -h192.168.6.102 -uroot -proot123 < sql/init.sql

# 2. 启动后端（8080）
cd backend && mvn spring-boot:run

# 3. 启动前端（5173，/api 代理到 8080）
cd frontend && npm install && npm run dev
```

打开 http://localhost:5173 ，注册/登录后体验完整闭环。

## V1 验收清单（已全部通过）

| 项 | 结果 |
|---|---|
| 注册 → 登录（Sa-Token Token + BCrypt） | ✅ |
| 商品列表（含起售价聚合）/ 详情（SKU） | ✅ |
| 加购物车 → 购物车列表/改数量/删除 | ✅ |
| 下单（乐观扣库存 `stock>=?` + @Transactional + 订单快照） | ✅ |
| 订单列表 / 详情 / 取消（回补库存） | ✅ |
| AI 问答：预置商品知识注入 + SSE 流式输出 | ✅（deepseek-v4-pro） |
| 全链路冒烟测试 `scripts/smoke-test.ps1` 9/9 | ✅ |

## 面试可讲点（V1）

- 请求链路：Controller → Service（构造器注入）→ Mapper 接口（@Mapper）→ XML SQL
- 统一返回 `R{code,msg,data}` + `BusinessException` + 全局异常兜底（不泄漏堆栈）
- MyBatis 手写分页 `LIMIT offset,size` + `PageResult`（为何不引分页插件）
- 下单防超卖：`UPDATE sku SET stock=stock-? WHERE id=? AND stock>=?`（CAS 式乐观扣减）
- Sa-Token 无状态 Token + 拦截器白名单
- Spring AI 流式响应（SSE text/event-stream，前端 fetch 逐块渲染）

## 演进预告

- **V2**：RAG 导购（商品文档分块+向量检索）、支付宝/微信沙箱支付、内容笔记发布
- **V3**：Redis（缓存三兄弟/购物车迁移/排行榜）、RabbitMQ（审核异步/订单事件）、秒杀
- **V4**：按域拆微服务（user/content/goods/order/ai）+ Nacos/Gateway/Feign + 分布式事务
- **V5**：Agent 客服（Function Calling）、推荐系统、NL2SQL 数据分析

> ⚠️ `application.yml` 内含 AI 中转 Key（OpenCode Go 订阅），仅本地开发使用；推送到公开仓库前务必改为环境变量注入。