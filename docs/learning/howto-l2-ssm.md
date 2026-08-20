# 给 ai-mall 改功能：SSM 初级开发者的实操指南（How-to · L2）

> **本指南的任务**：教你用一套固定套路，给 ai-mall 后端**加新功能 / 改行为**。不是从零搭项目，也不是背面试题——是"我要改代码，按什么顺序动哪些文件、改完怎么验"。
>
> **写给谁**：懂 SSM（Spring + Spring MVC + MyBatis）的初级开发者——看得懂 Controller/Service/Mapper/XML 分层，会写 SQL，认得 `@RestController`、`@Transactional`、`@Valid`。如果你连分层都还没概念，请先看 L1 版。
>
> **本指南不做什么**：不解释框架原理，不复述三大框架的基础知识。每个演练直接给"改哪个文件 → 怎么改 → 检查哪里"。
>
> **事实来源**：本文全部代码位置、类名、方法名、接口路径、表结构均取自项目真实代码核对后的共享素材包 `00-backend-material-pack.md`。演练中的**新增代码**是示意写法（标注了 `示意`），落地时以你 clone 到的源码为准。

---

## 目录

- [第 0 步：看清分层地图与"一次改动"的标准套路](#第-0-步看清分层地图与一次改动的标准套路)
- [演练 1：新增商品分类查询接口](#演练-1新增商品分类查询接口)
- [演练 2：把购物车数量上限 99 改成 199](#演练-2把购物车数量上限-99-改成-199)
- [演练 3：给订单加"备注"字段](#演练-3给订单加备注字段)
- [半路翻车自救（L2 版 FAQ）](#半路翻车自救l2-版-faq)
- [附录 A：三个演练的改动文件清单](#附录-a三个演练的改动文件清单)
- [附录 B：验证命令集](#附录-b验证命令集)

---

## 第 0 步：看清分层地图与"一次改动"的标准套路

### 0.1 真实代码位置地图（务必看，后面全靠它指路）

```
backend/src/main/
├── java/com/aimall/
│   ├── AimallApplication.java       # 启动类；注意：没有 @MapperScan
│   ├── common/                      # R 统一返回 / ResultCode / BusinessException
│   │                                #   / GlobalExceptionHandler / PageQuery / PageResult
│   ├── config/                      # AiConfig / PasswordConfig / SaTokenConfig / WebConfig
│   ├── user/        → bean/ controller/ dto/ mapper/ service/ service/impl/
│   ├── goods/       → bean/ controller/ dto/ mapper/ service/ service/impl/   # 商品+购物车
│   ├── order/       → bean/ controller/ dto/ mapper/ service/ service/impl/
│   └── ai/          → bean/ controller/ dto/ mapper/ service/ service/impl/
└── resources/
    ├── application.yml              # 8080 / MySQL / sa-token / mybatis 配置
    └── mapper/*.xml                 # 8 个 XML：User / Cart / Product / ProductSku
                                     #   / Order / OrderItem / Conversation / Message
```

关键约定（改代码前先背下来）：

| 约定 | 出处 |
|---|---|
| 每组 Mapper **逐接口 `@Mapper` 注册**，项目里**没有 `@MapperScan`** | 素材包 §2 |
| 接口路径统一前缀 `/api/v1`；实体包叫 `bean`；Controller 命名 `XxxRestController` | 素材包 §2 |
| Mapper 为接口，SQL 全在 `resources/mapper/*.xml`，**手写 SQL**（无 MyBatis-Plus/PageHelper） | 素材包 §1/§2 |
| Service 实现类构造器注入：`@RequiredArgsConstructor` + `private final` | 素材包 §2 |
| 分页手写：`LIMIT offset,size` + `PageResult{records,total,page,pageSize}`（`PageQuery.getOffset()=(page-1)*pageSize`） | 素材包 §2/§3 |
| 统一返回 `R<T>{code,msg,data}`；业务异常抛 `BusinessException`，由全局处理器转 `R.fail` | 素材包 §3 |
| 用户身份用 `StpUtil.getLoginIdAsLong()` 拿（登录拦截由 SaTokenConfig 统一处理） | 素材包 §6 |

### 0.2 一次改动的标准套路（7 步，三个演练都按它走）

1. **读现状，不猜**：先看表结构（`SHOW CREATE TABLE`）和数据模型（`bean`），确认要动的字段到底存不存在；
2. **Mapper**：接口方法（`xxxMapper.java`）+ SQL（`resources/mapper/xxxMapper.xml`），两处必须同时改；
3. **Service**：业务逻辑在 `service/impl/`，接口在 `service/`，改/加方法；
4. **Controller**：暴露 HTTP 入口（`XxxRestController`），记得参数校验走 DTO；
5. **DTO**：请求体字段 + 校验注解（`@NotNull/@NotBlank/@Min/@Max/@Size`），Controller 参数上要有 `@Valid` 才生效；
6. **前端**：Vue3 + Element Plus 页面调新接口/传新字段（`/api` 由 Vite 代理到 8080，**前端不用改代理配置**）；
7. **SQL + 验证**：需要改表就 `ALTER`（开发库直接改），然后 curl + 查表双验证，最后**回归**（不带新参数时行为必须和改前一致）。

### 0.3 开发循环（每个演练都要重复的节奏）

```text
改 Java/XML → Ctrl+C 停后端 → mvn spring-boot:run 重启 → curl 验证
改前端    → 保存即热更新（Vite）→ 浏览器刷新验证
```

> 后端**没有热部署**：改了 `.java` 或 `mapper/*.xml` 必须重启进程才会生效。改 XML 后如果怀疑没生效，检查 `backend/target/classes/mapper/` 里有没有同步过去。

### 0.4 验证工具（就这三样）

```text
# 1) 登录拿 token（curl.exe；Windows 下 PowerShell 里 curl 是别名，务必带 .exe）
curl.exe -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"demo01\",\"password\":\"123456\"}"
# → data.token 存下来，下面所有请求都带 -H "Authorization: <token>"

# 2) 查数据库
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT ...;"

# 3) 看接口返回（R 结构：code/msg/data）
```

---

## 演练 1：新增商品分类查询接口

> **目标**：让 `GET /api/v1/products` 支持按分类过滤——传 `categoryId` 只返回该分类的在售商品，**不传时行为和现在一模一样（回归红线）**。
>
> **改动路径**：Mapper → Service → Controller → DTO/PageQuery → 前端 → 验证。
>
> **耗时**：1 小时左右（含验证）。

### 1.1 先读现状（不猜，先看）

- 表已备好分类字段：`t_product` 有 `category_id`（素材包 §7），`idx_status` 索引只覆盖 status，**没有分类索引**；
- `application.yml` 开了 `map-underscore-to-camel-case: true` → `category_id` 自动映射成 bean 属性 `categoryId`，Product bean 里已有该字段；
- 现状接口：`GET /api/v1/products`（PageQuery 分页上架商品）+ `GET /api/v1/products/{id}`（SKU 列表），**没有分类维度**（素材包 §6 goods 域）；
- 现状 Mapper 方法（`goods/mapper/ProductMapper.java` + `resources/mapper/ProductMapper.xml`）：`selectOnSalePage(offset,size)`、`countOnSale`、`selectById`、`selectAllOnSale`。

结论：**不用改表、不用加实体字段**，链路只差"条件参数从 Controller 一路传到 SQL"。

### 1.2 步骤 1：Mapper（接口 + XML 一起改）

文件 A：`backend/src/main/java/com/aimall/goods/mapper/ProductMapper.java`

```java
// 示意：给现成的两个方法加可选参数（或新增同名带分类的重载，二选一）
List<Product> selectOnSalePage(@Param("offset") int offset,
                               @Param("size") int size,
                               @Param("categoryId") Long categoryId);
long countOnSale(@Param("categoryId") Long categoryId);
```

文件 B：`backend/src/main/resources/mapper/ProductMapper.xml` —— 在现有 `selectOnSalePage` / `countOnSale` 的 `<where>` / `WHERE` 里**追加可选条件**（保持 `WHERE status=1` 和 `LIMIT offset,size` 不动）：

```xml
<!-- 示意：MyBatis 动态 SQL，categoryId 为 null 时该片段被忽略，行为与改前完全一致 -->
<if test="categoryId != null">
  AND category_id = #{categoryId}
</if>
```

**检查点**：① 接口和 XML 的 `@Param` 名称必须一致（`categoryId`）；② 改的是 `<where>` 内的**追加**条件，不是替换 `status=1`；③ 返回值类型与列映射不要动。

### 1.3 步骤 2：Service（走 count + 分页的老套路）

文件：`backend/src/main/java/com/aimall/goods/service/ProductService.java`（接口）+ `backend/src/main/java/com/aimall/goods/service/impl/ProductServiceImpl.java`

```java
// 示意：ProductServiceImpl 里加方法（参照现有 pageOnSale：count + 分页 list + toListVO）
public PageResult<ProductVO> pageOnSale(PageQuery query, Long categoryId) {
    long total = productMapper.countOnSale(categoryId);
    List<Product> records = productMapper.selectOnSalePage(
        query.getOffset(), query.getPageSize(), categoryId);
    return PageResult.of(records.stream().map(this::toListVO).toList(),
                         total, query.getPage(), query.getPageSize());
}
```

**检查点**：① `toListVO` 保持"不含 detail"（列表接口不带详情，素材包 §6）；② 构造器注入已有 `private final ProductMapper productMapper`，直接复用，别 new。

### 1.4 步骤 3：Controller（暴露可选参数）

文件：`backend/src/main/java/com/aimall/goods/controller/ProductRestController.java`

```java
// 示意：GET /api/v1/products?page=1&pageSize=10&categoryId=2
@GetMapping
public R<PageResult<ProductVO>> page(@Valid PageQuery query,
                                     @RequestParam(required = false) Long categoryId) {
    return R.ok(productService.pageOnSale(query, categoryId));
}
```

**检查点**：① `required=false`——漏了它，不传分类会直接 400；② 路径别写成 `/products/{categoryId}` 之类与现有 `GET /{id}` 冲突的路由。

### 1.5 步骤 4：DTO / PageQuery

两个方案，推荐方案 A：

- **方案 A（推荐）**：给 `common/PageQuery.java` 加一个可选字段 `categoryId`，让参数自动绑定进 `@Valid` 的 query 对象里（Controller 就不用单独声明 `@RequestParam` 了）；
- **方案 B**：Controller 用 `@RequestParam(required=false) Long categoryId` 单独接（上面 1.4 就是这么写的）。

> 注意：`PageQuery` 现有校验 `page(min=1)`、`pageSize(1..100)`——加分类字段**不要动**这两个既有校验，避免回归。

**检查点**：方案 A 加字段时记得带 Lombok `@Data`（或手写 getter/setter），否则 Spring 绑不进来。

### 1.6 步骤 5：前端（商品列表页加分类 Tab）

文件：`frontend/src/` 下商品列表页组件（名字以实际为准，示意）：

```js
// 示意：列表请求带上分类条件
const params = { page: this.page, pageSize: this.pageSize };
if (this.activeCategoryId) params.categoryId = this.activeCategoryId;
// 请求仍走 /api/v1/products，Vite 自动代理到 8080，无需改代理配置
```

用 Element Plus 的 `el-tabs` / `el-radio-group` 渲染分类，`activeCategoryId` 变化就重新请求。

**检查点**：不选中任何分类时 `params` 里**不能出现** `categoryId`（回归红线）。

### 1.7 步骤 6：验证（SQL + curl 双通道）

1. 先拿真实分类 id：

```text
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT category_id, COUNT(*) FROM t_product GROUP BY category_id;"
```

2. 带分类请求（token 换成你的）：

```text
curl.exe "http://localhost:8080/api/v1/products?page=1&pageSize=10&categoryId=<上面查到的某个值>" -H "Authorization: <token>"
```

- ✅ `records` 里所有商品都属于该分类，`total` 小于全量；
- ✅ 返回仍是 `{code,msg,data:{records,total,page,pageSize}}` 结构，`records` 里**没有** `detail` 字段。

3. 回归（红线）：不带 `categoryId`：

```text
curl.exe "http://localhost:8080/api/v1/products?page=1&pageSize=10" -H "Authorization: <token>"
```

- ✅ 结果与改动前完全一致（数量、顺序）。

4. 越界值把玩：`categoryId=99999` → 返回 `total=0`、空 `records`（**不是报错**，因为没商品命中）；`categoryId=abc` → 400（类型转换失败由全局异常兜底转 400）。

**检查点清单**：Mapper 接口/XML 同步改 ✓　`@Param` 一致 ✓　`status=1` 过滤保留 ✓　分页 LIMIT 保留 ✓　不带参数回归一致 ✓　前端不传空分类 ✓

---

## 演练 2：把购物车数量上限 99 改成 199

> **目标**：购物车单个 SKU 数量上限从 99 放宽到 199——**前提是数据库列类型装得下**。
>
> **改动路径**：先全项目搜 `99`（4 处可能）→ DTO → Service → 前端 → SQL → 验证。
>
> **耗时**：30 分钟以内。

### 2.1 先找"99"在哪（搜出来的位置逐个核对）

素材包给出的三处硬事实 + 一处待查：

| 位置 | 现状 | 改法 |
|---|---|---|
| `goods/dto/AddCartRequest.java` | `quantity` 校验 1..99 默认 1 | `@Max(199)` |
| `goods/dto/UpdateCartRequest.java` | `quantity` 校验 1..99 | `@Max(199)` |
| `goods/service/impl/CartServiceImpl.java` | 同 SKU 已有则数量相加，**封顶 99** | 常量改 199（若散落魔法数，全换） |
| 前端数量控件 | 商品详情页 / 购物车页的 `el-input-number` 的 `max` | `99` → `199` |

> ⚠️ **动手前先查数据库**——这是本演练最容易翻车的点：

```text
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SHOW CREATE TABLE t_cart\G"
```

看 `quantity` 的列类型：若是 `int` 就放心改；**若是 `tinyint`（上限 127）或 `smallint`，199 会溢出**，必须先扩列（见 2.5）。

### 2.2 步骤 1：DTO 两处校验注解

文件：`backend/src/main/java/com/aimall/goods/dto/AddCartRequest.java`、`UpdateCartRequest.java`

```java
// 示意：AddCartRequest 中 quantity 的注解
@NotNull
@Min(1)
@Max(199)   // 原来是 @Max(99)
private Integer quantity;
```

**检查点**：① `@Min(1)` 别动（下限仍 1）；② Spring Boot 3 的校验注解包名是 `jakarta.validation.constraints.*`，别引成老 `javax`；③ Controller 下单/加购参数上必须已有 `@Valid`，否则校验注解是死代码。

### 2.3 步骤 2：Service 封顶逻辑

文件：`backend/src/main/java/com/aimall/goods/service/impl/CartServiceImpl.java`

```java
// 示意：add() 里同 SKU 数量相加的封顶
private static final int MAX_CART_QUANTITY = 199;   // 原来 99
int merged = cart.getQuantity() + request.getQuantity();
if (merged > MAX_CART_QUANTITY) merged = MAX_CART_QUANTITY;  // 封顶，不报错
```

**检查点**：① 全文件搜 `99`，确认没有第二处魔法数漏网（例如前端之外后端还有别的写死点）；② 封顶策略是"截断到上限"还是"报 BusinessException"取决于现有实现——**保持原策略**，只改数字。

### 2.4 步骤 3：前端数量控件

文件：`frontend/src/` 下的商品详情页 / 购物车页（示意）：

```html
<!-- 示意：Element Plus 数字输入框的上限 -->
<el-input-number v-model="qty" :min="1" :max="199" />
```

**检查点**：前端只是体验层；**真正的防线在后端 DTO 校验**。前端漏改只是不好用，后端漏改才是漏洞。

### 2.5 步骤 4：SQL（只有列装不下才需要）

```text
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; ALTER TABLE t_cart MODIFY COLUMN quantity INT NOT NULL DEFAULT 1;"
```

（开发库直接 ALTER 即可；生产环境请走迁移脚本，本指南不展开。）

### 2.6 步骤 5：验证

1. 改后重启后端，先验"新上限合法"：

```text
curl.exe -X POST http://localhost:8080/api/v1/cart -H "Content-Type: application/json" -H "Authorization: <token>" -d "{\"skuId\":1,\"quantity\":150}"
# ✅ 期望 code=200（改前会 400）
```

2. 连续加购同 SKU 验证封顶：

```text
# 再加 100（150+100=250 → 应截断到 199）
curl.exe -X POST http://localhost:8080/api/v1/cart -H "Content-Type: application/json" -H "Authorization: <token>" -d "{\"skuId\":1,\"quantity\":100}"
curl.exe "http://localhost:8080/api/v1/cart" -H "Authorization: <token>"
# ✅ 该 SKU quantity = 199
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT user_id, sku_id, quantity FROM t_cart;"
```

3. 越界校验（应 400，且 message 里带字段名）：

```text
curl.exe -X POST http://localhost:8080/api/v1/cart -H "Content-Type: application/json" -H "Authorization: <token>" -d "{\"skuId\":1,\"quantity\":200}"
# ✅ code=400，msg 形如 "quantity ... 最大 199"（字段错误由全局异常处理器返回第一条）
```

4. 回归：`quantity=0` 仍 400；`quantity=99` 现在合法（改前也合法）；**注意区分两种失败**——校验失败是 400，库存不够是 `2004 STOCK_NOT_ENOUGH`，别混为一谈。

**检查点清单**：两处 DTO 都改 ✓　Service 封顶数字全换 ✓　列类型放得下（不够已 ALTER）✓　200 通过 400 拒绝 ✓　封顶 199 ✓　库存 2004 与校验 400 区分清楚 ✓

---

## 演练 3：给订单加"备注"字段

> **目标**：下单时可选填一句备注（如"红色包装送人"），订单详情能看到它。这是一个**加字段的全链路打洞**演练：表 → Mapper → DTO → Service → VO → 前端。
>
> **改动路径**：SQL → Mapper（insert + 查询）→ DTO → Service → VO/Controller → 前端 → 验证。
>
> **耗时**：1 小时左右。

### 3.1 先读现状（决定从哪层起手）

- 表 `t_order` 现有字段（素材包 §7）：`order_no`/`total_amount`/`status`/`pay_type`/`pay_time`/`cancel_time`/`version`，**没有备注列** → 第一步就是加表字段；
- `CreateOrderRequest` 现有：`items`（@NotEmpty @Valid）、`receiverName`/`receiverPhone`/`receiverAddress`（@NotBlank）→ DTO 加可空字段；
- 详情 VO 的组装方式是 `BeanUtils.copyProperties(order, vo)`（素材包 §6 order 域）→ **OrderVO 里必须有 `remark` 字段，否则拷不过去**；
- `OrderMapper.insert` 用 useGeneratedKeys 回填 id，SQL 在 `mapper/OrderMapper.xml` → 插入列必须显式加。

### 3.2 步骤 1：SQL 加列（向后兼容：可空）

```text
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; ALTER TABLE t_order ADD COLUMN remark VARCHAR(255) DEFAULT NULL COMMENT '订单备注';"
```

**检查点**：① 加 `DEFAULT NULL`，**旧的 8 行种子/已有订单不受影响**；② 列名 `remark` 驼峰映射成 `remark`（单字段无需下划线）；③ `SHOW COLUMNS FROM t_order;` 确认列已加。

### 3.3 步骤 2：Mapper（接口 + XML 同步）

文件 A：`backend/src/main/java/com/aimall/order/mapper/OrderMapper.java` —— `insert(Order)` 的入参对象已含新字段，**接口签名不用变**（除非你用的是裸参数）。

文件 B：`backend/src/main/resources/mapper/OrderMapper.xml` —— **必须改两处**：

```xml
<!-- 示意①：insert 语句的列清单与 VALUES 同步加 remark -->
INSERT INTO t_order (order_no, total_amount, status, ..., remark)
VALUES (#{orderNo}, #{totalAmount}, #{status}, ..., #{remark})

<!-- 示意②：selectById / selectByOrderNo / selectByUserIdPage 的列清单加 remark -->
SELECT id, order_no, total_amount, status, ..., remark FROM t_order WHERE ...
```

**检查点**：① insert 的**列数与 `#{}` 个数一一对应**，少一个就是 SQL 语法错；② 所有查订单的 SQL（详情/列表/按单号查）**都要加列**，漏一个列表就取不到 remark；③ `updateStatus`（状态机 CAS）**不用加**——它只改 status/cancel_time/version。

### 3.4 步骤 3：DTO（请求体加可空字段）

文件：`backend/src/main/java/com/aimall/order/dto/CreateOrderRequest.java`

```java
// 示意：可空备注，最长 255（和列宽对齐）
@Size(max = 255)
private String remark;
```

**检查点**：① 不加 `@NotBlank`（可空是需求本身）；② 字段是 `String`，Lombok `@Data` 自动生成 getter/setter。

### 3.5 步骤 4：Service（组装 Order 时赋值）

文件：`backend/src/main/java/com/aimall/order/service/impl/OrderServiceImpl.java`

```java
// 示意：create() 第 3 步"组 Order"处，和收货信息一起赋值
Order order = new Order();
order.setOrderNo(generateOrderNo(userId));
order.setStatus(OrderStatus.PENDING_PAY);
order.setReceiverName(request.getReceiverName());
order.setReceiverPhone(request.getReceiverPhone());
order.setReceiverAddress(request.getReceiverAddress());
order.setRemark(request.getRemark());        // 新增这一行
```

**检查点**：① 赋值位置在 `insert` 之前（组 Order 阶段）；② 不用动事务边界——`@Transactional(rollbackFor=Exception.class)` 已包住整个 create；③ 不用动防超卖 SQL，备注与扣库存无关。

### 3.6 步骤 5：VO + Controller（让详情能透出）

文件：`backend/src/main/java/com/aimall/order/bean/OrderVO.java`

```java
// 示意：VO 加同名字段，BeanUtils.copyProperties(order, vo) 才会拷进来
private String remark;
```

文件：`backend/src/main/java/com/aimall/order/controller/OrderRestController.java` —— **大概率一行都不用改**：下单请求体由 DTO 自动绑定，详情返回由 `copyProperties` 自动带出。改完先别动，验证时发现缺了再回来查 VO。

**检查点**：① `BeanUtils` 是浅拷贝、按同名属性走 setter——所以 **VO 必须有字段**；② 确认 `OrderVO` 是用 Lombok `@Data`（或手写 setter），否则 copy 静默失败。

### 3.7 步骤 6：前端（结算页填、详情页展示）

文件：`frontend/src/` 下结算页 / 订单详情页组件（示意）：

```html
<!-- 结算页：备注输入框，选填 -->
<el-input v-model="orderForm.remark" maxlength="255" placeholder="选填：订单备注" />

<!-- 详情页：有值才展示，没值为 null 时要兜底 -->
<div v-if="orderDetail.remark">{{ orderDetail.remark }}</div>
```

**检查点**：① 旧订单 `remark` 为 null，前端展示必须判空（`v-if`）；② 下单请求 `items` 之外多了 `remark` 字段不影响后端解析（可空字段）。

### 3.8 步骤 7：验证

1. 带备注下单：

```text
curl.exe -X POST http://localhost:8080/api/v1/orders -H "Content-Type: application/json" -H "Authorization: <token>" -d "{\"items\":[{\"skuId\":1,\"quantity\":1}],\"receiverName\":\"张三\",\"receiverPhone\":\"13800138000\",\"receiverAddress\":\"上海市xx路1号\",\"remark\":\"红色包装送人\"}"
```

2. 三处核对：

```text
# 接口层：详情返回里出现 remark
curl.exe "http://localhost:8080/api/v1/orders/<订单id>" -H "Authorization: <token>"
# 数据库层：主表能查到
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT order_no, total_amount, remark FROM t_order ORDER BY id DESC LIMIT 3;"
# 前端层：订单详情页能看到备注，旧订单不显示空备注
```

3. 回归：不带 `remark` 下单 → 200、`remark` 为 null、**订单照常生成**（明细、清购物车、扣库存都不受影响）。

**检查点清单**：列已加（可空）✓　insert XML 列数对齐 ✓　三个查询 SQL 全加列 ✓　DTO 可空校验 ✓　Service 组 Order 时赋值 ✓　OrderVO 有字段 ✓　旧订单 null 前端兜底 ✓　不带 remark 回归通过 ✓

---

## 半路翻车自救（L2 版 FAQ）

**Q1：改了 Java 重启后还是老行为？**
确认你重启的是 backend 进程（`Ctrl+C` → `mvn spring-boot:run`），不是只刷新了浏览器。后端没有热部署。

**Q2：改了 `mapper/*.xml` 却不生效或直接启动失败？**
XML 在 `resources` 下，重启会重新加载。若报 MyBatis 绑定/语法错：检查 `target/classes/mapper/` 是否同步；检查接口方法名与 XML `id` 是否一致（`mapper-locations: classpath:mapper/**/*.xml` 已配好）。

**Q3：校验注解没生效，400 没触发？**
三层都查：① 注解引的是 `jakarta.validation`（Boot 3）不是 `javax`；② DTO 字段有 getter（`@Data`）；③ Controller 参数上是 `@Valid` + `@RequestBody`（如 `RegisterRequest` 就是 `@Valid` 用法的现成参考）；④ 全局异常处理器已覆盖 `MethodArgumentNotValidException` → 400，不用你自己写。

**Q4：`BeanUtils.copyProperties` 拷不出新字段？**
目标 VO 上没这个字段或有字段没 setter。给 VO 加同名属性 + `@Data`。它是浅拷贝、按名匹配，拼写必须一字不差。

**Q5：事务好像没回滚？**
`@Transactional` 要落在 **public** 的 ServiceImpl 方法上（`OrderServiceImpl.create` 就是 `@Transactional(rollbackFor=Exception.class)`）；且只有在**方法内抛出异常**才回滚，吞掉异常（try-catch 不 rethrow）就回滚不了。测试时可以故意传一个不存在的 skuId（`2002 SKU_NOT_FOUND`）下单，验证 `t_order` 没插入。

**Q6：一直 401「未登录或登录已过期」？**
请求头没带 `Authorization: <token>`，或 token 过期（7 天）。白名单只有 `/api/v1/auth/login`、`/api/v1/auth/register`、`/error`——想先测新接口，就先登录拿 token。

**Q7：改完 `application.yml` 没生效？**
配置只在启动时读一次，重启后端。另注意 MySQL 连接串、AI Key 都是环境变量缺省值（`MYSQL_USERNAME`/`MYSQL_PASSWORD`/`DEEPSEEK_API_KEY`），环境变量优先级高于 yml。

**Q8：报警告/错误与本次改动无关也要管吗？**
先分主次：启动日志里 Tomcat started 就说明能跑；406/404 多半是路由写错（`/api/v1` 前缀丢了）；其余无关警告可略过，别在验证时被日志刷屏带偏。

---

## 附录 A：三个演练的改动文件清单

| 演练 | 层 | 文件（相对 `backend/`） |
|---|---|---|
| 1 分类查询 | Mapper 接口 | `src/main/java/com/aimall/goods/mapper/ProductMapper.java` |
| 1 分类查询 | Mapper XML | `src/main/resources/mapper/ProductMapper.xml` |
| 1 分类查询 | Service | `src/main/java/com/aimall/goods/service/ProductService.java` + `.../service/impl/ProductServiceImpl.java` |
| 1 分类查询 | Controller | `src/main/java/com/aimall/goods/controller/ProductRestController.java` |
| 1 分类查询 | DTO | `src/main/java/com/aimall/common/PageQuery.java`（方案 A） |
| 1 分类查询 | 前端 | `frontend/src/` 商品列表页（示意位置） |
| 2 数量上限 | DTO ×2 | `src/main/java/com/aimall/goods/dto/AddCartRequest.java`、`UpdateCartRequest.java` |
| 2 数量上限 | Service | `src/main/java/com/aimall/goods/service/impl/CartServiceImpl.java` |
| 2 数量上限 | 前端 | `frontend/src/` 商品详情页 + 购物车页数量控件 |
| 2 数量上限 | SQL（条件性） | `t_cart.quantity` 扩列 `ALTER TABLE` |
| 3 订单备注 | Mapper XML | `src/main/resources/mapper/OrderMapper.xml`（insert + 3 个查询） |
| 3 订单备注 | DTO | `src/main/java/com/aimall/order/dto/CreateOrderRequest.java` |
| 3 订单备注 | Service | `src/main/java/com/aimall/order/service/impl/OrderServiceImpl.java` |
| 3 订单备注 | VO | `src/main/java/com/aimall/order/bean/OrderVO.java` |
| 3 订单备注 | Controller | `OrderRestController`（通常零改动，验证缺了再回看） |
| 3 订单备注 | 前端 | `frontend/src/` 结算页 + 订单详情页 |
| 3 订单备注 | SQL | `ALTER TABLE t_order ADD COLUMN remark ...` |

## 附录 B：验证命令集

```text
# 通用：登录拿 token
curl.exe -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"username\":\"demo01\",\"password\":\"123456\"}"

# 演练 1：分类过滤 + 回归
curl.exe "http://localhost:8080/api/v1/products?page=1&pageSize=10&categoryId=<id>" -H "Authorization: <token>"
curl.exe "http://localhost:8080/api/v1/products?page=1&pageSize=10" -H "Authorization: <token>"
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT category_id, COUNT(*) FROM t_product GROUP BY category_id;"

# 演练 2：数量上限
curl.exe -X POST http://localhost:8080/api/v1/cart -H "Content-Type: application/json" -H "Authorization: <token>" -d "{\"skuId\":1,\"quantity\":150}"
curl.exe -X POST http://localhost:8080/api/v1/cart -H "Content-Type: application/json" -H "Authorization: <token>" -d "{\"skuId\":1,\"quantity\":200}"   # 期望 400
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT quantity FROM t_cart;"

# 演练 3：订单备注
curl.exe -X POST http://localhost:8080/api/v1/orders -H "Content-Type: application/json" -H "Authorization: <token>" -d "{\"items\":[{\"skuId\":1,\"quantity\":1}],\"receiverName\":\"张三\",\"receiverPhone\":\"13800138000\",\"receiverAddress\":\"上海市xx路1号\",\"remark\":\"红色包装送人\"}"
curl.exe "http://localhost:8080/api/v1/orders/1" -H "Authorization: <token>"
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT order_no, remark FROM t_order;"

# 收尾：整体冒烟（9 项全过 = 环境没被改坏）
pwsh -File scripts/smoke-test.ps1
```

---

*三个演练做完，你就掌握了在 ai-mall 里改功能的完整套路：表不骗人、Mapper 两处同步、VO 缺字段拷不过去、回归红线不能破。这套打法换到任何 SSM 项目都能复用。*