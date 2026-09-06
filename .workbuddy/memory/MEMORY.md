# ai-mall 项目长期记忆

## 项目定位与铁律

- 求职实战项目「AI 种草商城」(对标小红书+得物)，**必须"真实业务驱动演进、绝不塞技术"**——每个技术决策要能答出"砍掉它会怎样"。用户性格:讲不出道理的东西没自信吹,所以文档/代码注释里业务理由必须显式。
- 数据规模前提:**无真实用户,AI 生成模拟数据(~100商品/~500笔记/模拟行为)**。选型按这个量级:MySQL 全文索引够(不上ES)、ItemCF 矩阵稀疏(主推基于内容+热门榜,CF 仅做离线对比)。

## 版本状态(2026-09-06)

- **V1 ✅** 单体闭环(10表,冒烟9/9):注册/商品/京东式购物车/订单(行锁CAS扣库存)/AI问答(FC+识图+SSE+会话)/派蒙TTS(自包含VITS:9944)。
- **V1.5 ✅** 工程基建:Flyway(baseline-on-migrate接存量库)/traceId三件套(Filter+MDC+TaskDecorator)/对象存储抽象(local默认|minio懒初始化预签名)/AsyncConfig(有界队列+CallerRuns)/15个单测(mockStatic钉StpUtil)/docker-compose四件套/GitHub Actions CI。讲解文档:`V1.5工程基建详解.md`+词典。
- **V2 ✅ 代码全量落地(待编译验证)**:内容社区(t_note等10表,ngram全文索引,游标分页含二元组游标,点赞唯一索引幂等)+AI审核MQ异步(DirectExchange+DLQ,幂等三防线:流水uk INSERT IGNORE/状态机条件更新/ERROR不误杀;毒消息吞异常ACK;MQ不可用降级@Async)+Hybrid RAG(语料=商品说明书+种草笔记;BM25+向量双通道RRF融合;EmbeddingClient可插拔:local哈希256维默认/spring-ai可切;searchDocs与searchProduct双工具Agent分流,模型自选不写if-else)+AI种草文案(RAG参与创作,温度0.8,只出草稿)+模拟数据脚本(gen_mock_data.py种子20260906→100商品/500笔记幂律分布)。讲解文档:`V2内容社区与RAG详解.md`+词典。V1.5曾有一处编译错(addHandler→addResourceHandler已修,用户已验证编译通过)。
- **V3 规划中**:支付沙箱/Redis(缓存+点赞计数+zset榜)/延迟消息取消订单/限量发售(非秒杀!)。V4:只拆 ai-service(理由:耗时差异抢线程池+故障隔离+密钥隔离)。V5可选:护栏NL2SQL/相似推荐+ItemCF离线对比。

## 口径红线(面试勿错)

- 扣库存=**行锁+WHERE stock>=? 条件更新(CAS思想)**,version 是预留字段从未参与比对——**不说"乐观锁"**。
- AI 通道=官方 DeepSeek 直连(api.deepseek.com),模型 `deepseek-v4-flash-vision-exp`(文本视觉同一模型两条ChatClient链路);OpenCode Go 中转在配置中已注释为备用。
- RAG 只管文档不管数据:价格库存走SQL,说明书+UGC笔记走RAG;必答"为何不塞长上下文"(语料动态/C端首字延迟/权限隔离/成本)。
- 派蒙资产本机已就位(public/assets/model+vendor),.gitignore不入库;TTS主路径本机VITS:9944自拉起,缺Python才降级Edge-TTS。
- 表名 t_user_address(非t_address);收货地址相关。

## 工程约定

- 表结构唯一权威=backend/src/main/resources/db/migration/(Flyway,只增不改);sql/init.sql仅便利脚本。
- 所有基建可降级可插拔(零依赖能启动是硬约束):voice如此,storage亦如此(local默认)。
- 开发环境注意:本机**无 Maven 命令**(V1.5 代码未编译验证过,首次需 IDEA/mvn test 验证);MySQL 在 192.168.6.102;conda py313 可做 YAML 校验。
- 讲解文档风格(用户强烈偏好):【易错关键点】前置+章节式+每章配"面试怎么讲"+独立【知识词典】;概念=一句话核心+比喻+代码对应。
