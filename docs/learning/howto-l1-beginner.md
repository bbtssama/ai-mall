# 把 ai-mall 跑起来：零基础走通一次完整下单（How-to 操作指南 · L1）

> **本指南的类型**：一篇"操作指南"（How-to）。它的任务只有一个——**带你把 ai-mall 后端项目跑起来，并亲手走通一次完整的电商闭环：注册 → 登录 → 加购 → 下单 → 查订单 → 问 AI**。
>
> **写给谁**：零基础读者——会用电脑、会用鼠标、能照着命令一个字一个字敲就行。不需要任何 Java / 数据库 / 编程背景。
>
> **本指南不做什么**：不讲原理、不解释框架、不科普概念（那是别的文档的事）。这里全是"按顺序做"的步骤，像菜谱一样：照着做，就能出锅。
>
> **唯一事实来源**：本指南所有命令、接口、表名、字段均取自项目真实代码核对后的共享素材包 `00-backend-material-pack.md`，未凭空添加任何环节。

---

## 目录

- [开始之前：3 分钟看清全局](#开始之前3-分钟看清全局)
- [任务 1：安装环境（JDK、Maven、MySQL、Node.js）](#任务-1安装环境jdk-maven-mysql-nodejs)
- [任务 2：建库（导入 init.sql，得到 ai_mall 和 8 张表）](#任务-2建库导入-initsql得到-ai_mall-和-8-张表)
- [任务 3：启动后端（8080 端口）](#任务-3启动后端8080-端口)
- [任务 4：启动前端（5173 端口）](#任务-4启动前端5173-端口)
- [任务 5：注册账号 + 登录](#任务-5注册账号--登录)
- [任务 6：浏览商品 + 加购物车](#任务-6浏览商品--加购物车)
- [任务 7：下单](#任务-7下单)
- [任务 8：验证订单（页面 + 数据库）](#任务-8验证订单页面--数据库)
- [任务 9：问 AI（普通问答 + 流式对话）](#任务-9问-ai普通问答--流式对话)
- [收尾：一键冒烟测试（可选但推荐）](#收尾一键冒烟测试可选但推荐)
- [挑战任务：取消订单（可选）](#挑战任务取消订单可选)
- [附录 A：接口速查表](#附录-a接口速查表)
- [附录 B：8 张表速查](#附录-b8-张表速查)
- [附录 C：常见卡点排查（FAQ）](#附录-c常见卡点排查faq)

---

## 开始之前：3 分钟看清全局

### 你将要得到什么

把下面这条闭环完整跑通（这也是项目的一句话简介）：

```
注册 → 登录 → 浏览商品 → 加购物车 → 下单（防超卖）→ AI 问答（流式）
```

做完后你会拥有：一台在跑的 Spring Boot 后端（端口 8080）、一个在跑的前端（端口 5173）、一个填了数据的 MySQL 库 `ai_mall`、你自己的账号、一单"待支付"订单、和一次真实 AI 对话。

### 你需要准备的东西

1. **一台电脑**（Windows 10/11 均可，本指南按 Windows 写；Mac 只是安装包不同，命令通用）；
2. **能联网**（装软件、下载 Maven 依赖都要联网）；
3. **ai-mall 的项目文件夹**（里面有 `backend`、`frontend`、`sql`、`scripts`、`docs` 等子文件夹）。如果你不确定根目录在哪：**`docs` 文件夹的上一层就是项目根目录**。下面所有"项目根目录"都指这一层。

### 三个约定，先记住

**约定 1：怎么开一个"终端"（命令行窗口）**

- 在 Windows 上：按 `Win + R`，输入 `powershell`，回车。
- 更推荐的做法：打开项目根目录的文件夹窗口，在地址栏输入 `cmd` 再回车——这样终端打开时**就在项目根目录**，不用到处 `cd`。
- 本指南里凡是你需要输入的命令，都放在灰色代码块里，一行一条，**原样照抄**即可。带有 `#` 的行是注释，不用敲。

**约定 2：三个窗口的工作法**

你后面要同时跑好几个东西，建议开 **3 个终端窗口**，各司其职：

| 窗口 | 干什么 | 停不下来就留这儿 |
|---|---|---|
| 窗口 1 | 数据库命令（导入、查询） | 随时用 |
| 窗口 2 | 跑后端（一启动就占住这个窗口） | 一直开着，别关 |
| 窗口 3 | 跑前端（同上） | 一直开着，别关 |

像后端、前端这种"跑起来就一直运行"的程序，会让当前窗口"卡住"——这不是坏了，是它在工作。想停掉它，按 `Ctrl + C`。

**约定 3：Token（令牌）是什么**

登录成功后，系统会还给你一串字符（Token）。以后所有"需要登录才能干"的操作，都要把这串字符随身带着（放在请求头 `Authorization` 里）。记不住没关系，后面每个用到它的地方我都会写清楚。

---

## 任务 1：安装环境（JDK、Maven、MySQL、Node.js）

> **目标**：电脑具备跑这个项目的 4 件套。之后每个工具都要验证一下，全部通过才算完成本任务。
>
> **前置条件**：能联网；会下载和双击安装包。
>
> **预计耗时**：30–60 分钟（取决于下载速度）。

> 安装原则只有一条：**一路默认点"下一步"，但把"加入 PATH 环境变量"这类勾选打开**。

### 1.1 装 JDK 17（跑 Java 后端必需的运行环境）

1. 浏览器打开官网：`https://adoptium.net`（Eclipse Temurin 发行版，免费）→ 点页面上的 **Download**。
2. 下载 **17**（注意看版本标签选 17，不是 21 或 8）的 Windows 安装包（`.msi`）。
3. 双击安装，一路下一步。**关键**：安装过程中看到 "Add to PATH" / "Set JAVA_HOME" 之类的选项，一定要勾上。
4. **验证**：新开一个终端窗口（见"开始之前·约定 1"），敲：

```text
java -version
```

输出里出现 `openjdk version "17.0.x"`（或 `1.8` 之外的大版本号为 17）就算成功。

> 卡住：提示"java 不是内部或外部命令"→ 说明 PATH 没配上，重新安装一遍并勾上 Add to PATH，然后**新开**终端再试（旧窗口不会刷新环境变量）。

### 1.2 装 Maven（Java 项目的构建工具）

1. 浏览器打开 `https://maven.apache.org/download.cgi` → 下载 **Binary zip archive** 那个压缩包（如 `apache-maven-3.9.x-bin.zip`）。
2. 解压到一个固定位置，例如 `C:\maven`（解压后里面应有一个 `bin`、一个 `conf` 文件夹）。
3. 配置环境变量（不会就照抄）：
   - `Win + R` → 输入 `sysdm.cpl` → 回车 → "高级"选项卡 → "环境变量"；
   - 在"系统变量"里新建：变量名 `MAVEN_HOME`，变量值填你的解压目录（如 `C:\maven`）；
   - 找到 `Path` 变量 → 编辑 → 新建一行输入 `%MAVEN_HOME%\bin` → 确定。
4. **验证**：新开终端，敲：

```text
mvn -version
```

出现 `Apache Maven 3.x.x` 和一串 Java 版本信息即为成功。

### 1.3 装 MySQL 8（存储数据的数据库）

1. 浏览器打开 `https://dev.mysql.com/downloads/installer/` → 下载 **MySQL Installer**（Windows 版）。
2. 安装时选 **Server only**（只装服务器就够了）→ 一路默认。
3. **关键一步**：设置 root 账号密码时，**建议直接设成 `root123`**（和项目默认配置一致，后面最省事）。如果你设了自己的密码，也没关系，任务 2 和附录 C 会告诉你怎么改配置。
4. 安装完成后，确保 MySQL 服务在运行：`Win + R` → 输入 `services.msc` → 找到 `MySQL80` 这项 → 状态应为"正在运行"。
5. **验证**：新开终端，敲：

```text
mysql --version
```

输出 `mysql Ver 8.x ...` 即为成功。

> 卡住："mysql 不是内部或外部命令"→ MySQL 默认不会加入 PATH，先别管它，任务 2 里用完整命令方式连接；如果你已经会改 PATH，把 `C:\Program Files\MySQL\MySQL Server 8.0\bin` 加进去即可。

### 1.4 装 Node.js（跑前端 Vite 开发服务器）

1. 浏览器打开 `https://nodejs.org` → 下载 **LTS**（长期支持版）Windows 安装包。
2. 双击安装，一路下一步（npm 会随 Node.js 一起装好）。
3. **验证**：新开终端，分别敲：

```text
node -v
npm -v
```

两个都输出版本号（如 `v20.x.x` 和 `10.x.x`）即为成功。

**本任务验收清单**：`java -version` ✓　`mvn -version` ✓　`mysql --version` ✓　`node -v` + `npm -v` ✓

---

## 任务 2：建库（导入 init.sql，得到 ai_mall 和 8 张表）

> **目标**：在 MySQL 里创建项目需要的数据库 `ai_mall`，建好 8 张表，并塞入商品种子数据——这样后面才有东西可查、可买。
>
> **前置条件**：任务 1 完成；MySQL 服务在运行；你的 root 密码已知。
>
> **预计耗时**：5 分钟。

### 步骤

1. 打开**终端窗口 1**，先 `cd` 到项目根目录：

```text
cd C:\Users\user\Desktop\note\Projects\ai-mall
```

> 如果你的项目在别的盘/别的路径，把上面的路径换成你自己的项目根目录即可。

2. 确认当前目录里能看到 `sql` 文件夹（用 `dir` 看一眼）。

3. 执行导入命令（**把整行原样照抄**；`<` 是"把文件内容喂给 mysql 命令"的意思）：

```text
mysql -h192.168.6.102 -uroot -proot123 < sql/init.sql
```

- 如果你的 root 密码不是 `root123`，改用：`mysql -h192.168.6.102 -uroot -p < sql/init.sql`，然后按提示手动输入密码。
- 命令没有输出、直接回到提示符 = 导入成功（MySQL 静默执行就是成功）。如果出现 `ERROR ...`，去附录 C 查"mysql 连不上"。

> **PowerShell 用户注意**：如果你用的是 PowerShell 而不是 cmd，`<` 重定向可能不被支持。这时改用这条等价的命令：
>
> ```text
> Get-Content sql/init.sql -Raw | mysql -h192.168.6.102 -uroot -proot123
> ```

### 验证（看数据库里有什么）

在同一个窗口继续敲（`-e` 表示"执行完这条 SQL 就退出"）：

```text
mysql -h192.168.6.102 -uroot -proot123 -e "SHOW DATABASES LIKE 'ai_mall';"
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SHOW TABLES;"
```

- 第一条能看到一行 `ai_mall`（说明库建好了）；
- 第二条能看到 **8 张表**：`t_user`、`t_product`、`t_product_sku`、`t_cart`、`t_order`、`t_order_item`、`t_conversation`、`t_message`。

再看种子数据（4 个商品、8 个 SKU）：

```text
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT COUNT(*) FROM t_product;"
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT COUNT(*) FROM t_product_sku;"
```

- 第一个显示 `4`，第二个显示 `8` 即成功。种子商品是：**AirSound Pro 耳机 ¥399 / 闪充宝 65W GaN ¥89-129 / 云朵唇釉 ¥69 / 轻氧手环 ¥199-249**。

**本任务验收**：`SHOW TABLES` 看到 8 张表；两个 `COUNT(*)` 分别是 4 和 8。

---

## 任务 3：启动后端（8080 端口）

> **目标**：让 Spring Boot 后端跑起来，监听 8080 端口。
>
> **前置条件**：任务 1、2 完成（数据库已就绪很重要——后端启动时连不上 MySQL 会直接报错退出）。
>
> **预计耗时**：首次 5–15 分钟（要下载依赖），以后每次 1–2 分钟。

### 步骤

1. 打开**终端窗口 2**，`cd` 到项目根目录，然后进入 `backend` 文件夹，启动：

```text
cd C:\Users\user\Desktop\note\Projects\ai-mall\backend
mvn spring-boot:run
```

2. 等待。首次运行 Maven 会下载所有依赖（本项目启用了阿里云镜像加速，速度有保障），屏幕上会滚动大量日志——都是正常的，**耐心等**。

3. 看到下面这两行中的任意一行，就说明后端起来了：

```text
Tomcat started on port 8080
Started AimallApplication
```

### 验证（后端活着没）

打开浏览器，访问：

```text
http://localhost:8080/api/v1/products
```

- 如果看到一段 JSON，内容是 `{"code":401,"msg":"未登录或登录已过期",...}` —— **这正是"后端运行正常"的铁证**：项目对 `/api/**` 做了登录拦截，你还没登录，所以它礼貌地拒绝了你。能返回 401，说明：服务起来了、拦截器在工作、数据库也连上了。
- 如果浏览器显示"无法访问此网站"或"连接被拒绝"——后端没起来，回去看终端报错（最常见：连不上 MySQL，见附录 C）。

### 说明

- 这个窗口会一直"占住"，**不要关**。想让后端停掉就按 `Ctrl + C`。
- 改任何配置文件（如 `application.yml`）后，必须 `Ctrl + C` 停掉再重新 `mvn spring-boot:run` 才会生效。

**本任务验收**：浏览器访问 `/api/v1/products` 返回 401 JSON。

---

## 任务 4：启动前端（5173 端口）

> **目标**：让 Vue3 前端页面跑起来，监听 5173 端口，并且能通过代理把 `/api` 请求转发到后端的 8080。
>
> **前置条件**：任务 3 完成（前端依赖后端，后端不在的话页面会请求失败）。
>
> **预计耗时**：首次 5–10 分钟（安装 npm 依赖），以后每次 10 秒。

### 步骤

1. 打开**终端窗口 3**，进入 `frontend` 文件夹，安装依赖并启动：

```text
cd C:\Users\user\Desktop\note\Projects\ai-mall\frontend
npm install
npm run dev
```

2. 等 `npm install` 跑完（出现大量 `added xxx packages`），再执行 `npm run dev`。

3. 看到如下输出即为成功：

```text
Local:   http://localhost:5173/
```

### 验证（页面出来了没）

1. 打开浏览器，访问 `http://localhost:5173`。
2. 你应该能看到 ai-mall 的商城页面（商品列表 / 登录注册入口等）。

> 前端页面里凡是 `/api` 开头的请求，Vite 都会自动代理转发到 `http://localhost:8080`——所以你不需要在前端里配置后端地址，前后端各跑各的就行。

**本任务验收**：浏览器打开 5173 看到商城页面；终端里两个窗口都没有报错堆栈。

---

## 任务 5：注册账号 + 登录

> **目标**：成为 ai-mall 的正式用户，拿到你的专属 Token。有了 Token，后面加购、下单、问 AI 才能做。
>
> **前置条件**：任务 3、4 完成（后端 8080 和前端 5173 都在跑）。
>
> **预计耗时**：5 分钟。

> 用户名规则先记一下，注册时用的到：**用户名 3–20 位，只能是字母、数字、下划线（`^[a-zA-Z0-9_]{3,20}$`）**；**密码 6–32 位**；昵称可以不填（不填会自动生成「种草用户+4位随机数」）。

### 方式 A：在网页上注册登录（零基础推荐）

1. 打开 `http://localhost:5173`，找到"注册"入口。
2. 按上面的规则填用户名、密码，提交。
3. 注册成功后会自动跳去登录（或提示去登录），用刚注册的用户名密码登录。
4. 登录成功后页面应进入"已登录"状态（能看到你的昵称/头像区域）。

### 方式 B：用命令注册登录（学 API 的同学选这个）

在**终端窗口 1**（这个窗口可以随便用来敲命令）执行：

```text
curl.exe -X POST http://localhost:8080/api/v1/auth/register -H "Content-Type: application/json" -d "{\"username\":\"demo01\",\"password\":\"123456\"}"
```

看到 `{"code":200,...}` 即注册成功（`data` 里是用户信息）。

再登录（**登录返回的 `data.token` 就是你的令牌，请复制保存**）：

```text
curl.exe -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"username\":\"demo01\",\"password\":\"123456\"}"
```

返回结构大致是：`{"code":200,"msg":"...","data":{"token":"一串uuid样式的字符","userVO":{...}}}`。请把 `token` 的值记下来（也建议存个临时文件），后面所有接口都要在请求头里带它：

```text
Authorization: <你把token贴在这里>
```

> **Windows 提示**：命令里的 `curl.exe` 故意带 `.exe` —— 因为 PowerShell 里 `curl` 是旧版别名，会做奇怪的事；带 `.exe` 才是 Windows 自带的真 curl。不用 PowerShell 而用 cmd 窗口的话，`curl` 也行。

### 验证（去数据库看你的账号）

```text
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT id, username, nickname, role, status FROM t_user;"
```

你会看到：
- 自己的账号出现在 `t_user` 表里，`role=0`、`status=1`（普通用户、正常状态）；
- 同一条命令里加一个字段确认密码是加密存的：

```text
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT username, LEFT(password_hash,7) FROM t_user;"
```

- 密码列以 `$2a$` 开头——说明密码经过 BCrypt 加密，数据库里存的是密文而不是明文。

> **想故意看报错长什么样？**（强烈建议试，能帮你建立对错误码的直觉）
> - 用同一个用户名再注册一次 → 返回 `code:1001`（`USERNAME_EXISTS` 用户名已存在）；
> - 登录时密码故意打错 → 返回 `code:1003`（`PASSWORD_ERROR`，项目统一提示密码错误，不暴露"用户名是否存在"）。

**本任务验收**：注册返回 200 → 登录拿到 `token` → `t_user` 表出现你的账号。

---

## 任务 6：浏览商品 + 加购物车

> **目标**：查商品列表和详情，挑一个 SKU（具体规格，如"AirSound Pro 耳机·白色"），把它加进购物车，并确认购物车里能看到它。
>
> **前置条件**：任务 5 完成，手上有 Token。
>
> **预计耗时**：5 分钟。

### 步骤 1：看商品列表（找到商品 id）

网页方式：前端 5173 首页就有商品列表，点任意商品进详情页。

命令方式：

```text
curl.exe "http://localhost:8080/api/v1/products?page=1&pageSize=10" -H "Authorization: <token>"
```

> 重点：**这一步开始，每个请求都要带 `Authorization` 头**。不带的话你会收到 `401 未登录或登录已过期`。

返回是一个分页结构 `{records, total, page, pageSize}`，`records` 里每个商品有 `id`、名称、起售价（`minPrice`）等基础信息。随便记下一个商品的 `id`。

### 步骤 2：看商品详情（拿到 SKU 的 id 和库存）

```text
curl.exe "http://localhost:8080/api/v1/products/1" -H "Authorization: <token>"
```

返回里除了商品详情，还有 `skus` 数组——每个 SKU 有 `id`（这就是你要的 **skuId**）、`skuName`、`price`、`stock`。**记一个 skuId 和它的单价**，后面下单要用来对账。

### 步骤 3：加购物车

网页方式：在商品详情页点"加入购物车"，选好数量。

命令方式（`quantity` 取值范围 1–99，不传默认 1）：

```text
curl.exe -X POST http://localhost:8080/api/v1/cart -H "Content-Type: application/json" -H "Authorization: <token>" -d "{\"skuId\":1,\"quantity\":1}"
```

把 `skuId` 换成你记下的那个值。返回 `{"code":200,...}` 即成功。

### 验证（购物车里有东西了）

1. 查购物车（命令）：

```text
curl.exe "http://localhost:8080/api/v1/cart" -H "Authorization: <token>"
```

每个条目会显示 `skuName`、`price`、`quantity`、`subtotal`（小计 = `price × quantity`）。检查小计和你手算的单价×数量一致。

2. 查数据库 `t_cart` 表（先查出你的 user_id）：

```text
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT id FROM t_user WHERE username='demo01';"
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT user_id, sku_id, quantity FROM t_cart;"
```

3. **再往购物车加一次同一个 SKU**，再看一次购物车——数量会**相加**（比如 1 + 1 = 2），上限 99。这是项目对同一个 SKU 的处理规则（数据库里靠 `uk_user_sku` 唯一约束保证同 SKU 只有一行）。

> **失败的常见样子**（都试试，建立直觉）：
> - skuId 不存在 → `2002 SKU_NOT_FOUND`；商品已下架 → `2003 SKU_OFF_SHELF`；库存不足 → `2004 STOCK_NOT_ENOUGH`。

**本任务验收**：`GET /api/v1/cart` 返回了条目，小计 = 单价×数量；`t_cart` 表有你的记录。

---

## 任务 7：下单

> **目标**：把购物车里的商品变成一张"待支付"订单——系统会扣库存、写订单、写明细、清购物车，全程一个事务。
>
> **前置条件**：任务 6 完成（购物车里有至少一个 SKU，或你已记下 skuId）。
>
> **预计耗时**：5 分钟。

### 步骤

网页方式：在前端购物车页点"去结算"，填写**收货人姓名、手机号、收货地址**（三项必填），提交订单。

命令方式（`items` 里的 `skuId` / `quantity` 填你自己的；收货三项缺一不可，缺了会报 400 字段校验错误）：

```text
curl.exe -X POST http://localhost:8080/api/v1/orders -H "Content-Type: application/json" -H "Authorization: <token>" -d "{\"items\":[{\"skuId\":1,\"quantity\":1}],\"receiverName\":\"张三\",\"receiverPhone\":\"13800138000\",\"receiverAddress\":\"上海市浦东新区xx路1号\"}"
```

### 你应该看到的返回

`{"code":200,...}`，`data` 里至少能看到两样东西：

- **`orderNo`（订单号）**：一串约 20 位的数字——由 `yyyyMMddHHmmssSSS` 时间戳 + 4 位随机数 + 你的用户 id 尾号拼成，全局唯一（数据库 `uk_order_no` 兜底）；
- **`totalAmount`（订单总额）**：所有明细 `price × quantity` 累加的结果。**手算一遍核对**（如 1 件 ¥399 = 399.00）。

> 下单的同时，系统内部依次完成了：逐项扣库存（`stock=stock-?`，防超卖的关键一步）→ 写订单主表 → 写订单明细（含商品名/SKU 名/价格的**快照**）→ 清掉购物车里本次下单的 SKU。这四步在**一个数据库事务**里，任何一步失败全部回滚，库存会恢复。

### 验证

1. 再去查一次购物车——本次下单的 SKU **已经从购物车消失了**：

```text
curl.exe "http://localhost:8080/api/v1/cart" -H "Authorization: <token>"
```

2. 查订单表（看看状态是不是"待支付"）：

```text
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT order_no, total_amount, status, created_at FROM t_order ORDER BY created_at DESC LIMIT 3;"
```

3. 查明细快照表（注意 `product_name` / `sku_name` / `price` 这三列是把下单那一刻的商品信息**抄了一份**存下来的）：

```text
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT order_id, sku_id, product_name, sku_name, price, quantity FROM t_order_item ORDER BY order_id DESC LIMIT 5;"
```

4. 顺手对着 SKU 表看一眼库存是不是少了：

```text
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT id, sku_name, stock, sales FROM t_product_sku WHERE id=1;"
```

（`stock` 应比你下单前少 1，`sales` 多 1。）

> **订单状态小辞典**（记住这些英文，后面验证、取消都用得上）：`PENDING_PAY 待支付 → PAID 已支付 → SHIPPED 已发货 → COMPLETED 已完成`，另有 `CANCELLED 已取消`。刚下的单一定是 `PENDING_PAY`。
>
> **失败的样子**：`items` 为空 → `2006 CART_EMPTY`；库存被抢光 → `2004 STOCK_NOT_ENOUGH`（可以看到防超卖逻辑在工作）。

**本任务验收**：下单返回 200 且有 `orderNo`/`totalAmount`；`t_order` 有这单且状态为待支付；`t_cart` 里本次 SKU 没了；`t_product_sku.stock` 减了。

---

## 任务 8：验证订单（页面 + 数据库）

> **目标**：站在"用户视角"和"数据库视角"各确认一遍：这单确实存在、金额正确、明细完整、属于你本人。
>
> **前置条件**：任务 7 完成。
>
> **预计耗时**：5 分钟。

### 步骤

1. **页面视角**：前端 5173 打开"我的订单"页。应该能看到刚下的这单，状态"待支付"，金额与下单时一致。

2. **接口视角**：查我的订单列表（分页，按时间倒序，最新在前）：

```text
curl.exe "http://localhost:8080/api/v1/orders?page=1&pageSize=10" -H "Authorization: <token>"
```

3. 查单个订单详情（把 `{id}` 换成订单数据里的 `id`，注意是订单表主键 `id`，不是 `orderNo`）：

```text
curl.exe "http://localhost:8080/api/v1/orders/1" -H "Authorization: <token>"
```

详情里会带出这单的明细（items：商品名、SKU 名、价格、数量快照）。

### 验证核对单（照着打勾）

- ☐ 订单列表第一行是本任务 7 下的单（时间倒序）；
- ☐ `totalAmount` = 每一项 `price × quantity` 之和（可在纸上手算验证，金额全程是 decimal 精确计算，不会出现 0.1+0.2 那种浮点误差）；
- ☐ 数据库 `t_order` 主表：`order_no` 唯一、状态待支付、收货三件套已入库；
- ☐ 数据库 `t_order_item` 明细：快照字段齐全；
- ☐ 数据库 `t_cart`：本次下单 SKU 已清除（任务 7 查过）；
- ☐ **归属校验**：订单详情接口只返回"我自己的"订单。拿你的 token + 一个不属于你的 order id 去查，会得到 `2007 ORDER_NOT_FOUND`——这是故意设计的安全行为。

> 想更直观的话，也可以直接一条 SQL 把主表明细连起来看：
>
> ```text
> mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT o.order_no, o.total_amount, o.status, i.product_name, i.quantity FROM t_order o JOIN t_order_item i ON i.order_id=o.id ORDER BY o.id DESC LIMIT 5;"
> ```

**本任务验收**：列表+详情接口都返回正确的单；数据库主表/明细/购物车三处对得上账。

---

## 任务 9：问 AI（普通问答 + 流式对话）

> **目标**：让 Spring AI 基于**商品库的真实数据**回答你关于商品的问题——并且体验一把 SSE 流式输出（字一个一个蹦出来）。
>
> **前置条件**：任务 5 完成（已登录）；后端能访问 AI 中转服务（`https://opencode.ai/zen/go`）。默认配置里带了一个占位 API Key——如果 AI 报错，去附录 C 查"AI 报错"一项。
>
> **预计耗时**：5 分钟。

> 重要背景（决定你能问出什么答案）：后端每次提问都会把**当前在售商品的 id、名称、副标题、起售价、详情**打包成上下文给 AI，并要求它"基于商品库如实回答，没有就坦诚说不知道，不编造"。所以你问**和这 4 个种子商品相关**的问题，回答质量最高。

### 步骤 1：前端页面普通问答

1. 前端 5173 打开"AI 助手"（或类似入口）。
2. 提问，比如："**帮我推荐一款降噪耳机**" 或 "**闪充宝多少钱，有什么规格**"。
3. 页面会显示 AI 的回答。

### 步骤 2（可选但推荐）：命令行走一遍，看数据结构

普通问答（`conversationId` 不传或传 null 时，系统会自动帮你创建会话，会话标题 = 你的问题前 20 个字）：

```text
curl.exe -X POST http://localhost:8080/api/v1/chat -H "Content-Type: application/json" -H "Authorization: <token>" -d "{\"message\":\"帮我推荐一款降噪耳机\",\"conversationId\":null}"
```

返回 `{"code":200,...,"data":"AI 的回答文本"}`。

再看流式版（SSE：服务器边生成边推送，体会"逐字输出"）：

```text
curl.exe -N -X POST http://localhost:8080/api/v1/chat/stream -H "Content-Type: application/json" -H "Authorization: <token>" -d "{\"message\":\"轻氧手环和闪充宝哪个适合送人\",\"conversationId\":null}"
```

`-N` 表示不等全部完成，边收边显示。你会看到内容分多行陆续刷出来（`text/event-stream` 格式）。

### 步骤 3：查会话记录（历史都被存进数据库了）

查会话列表（接口按新→旧返回你的会话）：

```text
curl.exe "http://localhost:8080/api/v1/conversations" -H "Authorization: <token>"
```

查某个会话里的全部消息（旧→新，`role` 是 `user`/`assistant` 成对出现）：

```text
curl.exe "http://localhost:8080/api/v1/conversations/1/messages" -H "Authorization: <token>"
```

数据库侧（`t_conversation` 存会话，`t_message` 存消息，一个会话有多条消息）：

```text
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT id, user_id, biz_type, title FROM t_conversation ORDER BY id DESC;"
mysql -h192.168.6.102 -uroot -proot123 -e "USE ai_mall; SELECT conversation_id, role, LEFT(content,50) FROM t_message ORDER BY id;"
```

你会看到：`t_conversation` 里 `title` 约等于你的问题（前 20 字）；`t_message` 里每条你问的后面跟着一条 `assistant` 的回答——**即使流式回答中途断网，系统也会保证成对落库**（提问先存、回答完毕后存，失败则写入兜底文案）。

> **失败的样子**：AI 服务不可用时，普通问答返回 `code:3001 AI_SERVICE_ERROR`；流式接口不会裸断，而是补一句兜底文案 `[AI 服务暂时不可用，请稍后再试]`。这两种都不是你操作错了，是 API Key / 中转网络的问题（见附录 C）。

**本任务验收**：问商品相关的问题得到像样的回答；`t_conversation` + `t_message` 里有成对的记录；体验过一次流式输出。

---

## 收尾：一键冒烟测试（可选但推荐）

> **目标**：让脚本替你从头到尾跑一遍完整闭环（注册 → 登录 → 商品列表 → 详情 → 加购 → 购物车 → 下单 → 订单 → AI 问答，共 9 项），全部通过 = 整套环境验收合格。
>
> **前置条件**：后端 8080 正在运行；可用 token 无所谓（脚本自己会注册）。
>
> **预计耗时**：1–2 分钟。

在项目根目录开一个终端窗口，执行：

```text
pwsh -File scripts/smoke-test.ps1
```

看到 **9/9 全部通过**（或"9 项全部 PASS"之类字样）就完工了。

> 如果只有 AI 那一项失败，先别急：那多是 API Key/中转的问题，与技术链路无关。

---

## 挑战任务：取消订单（可选）

做完主线想做点"危险操作"，就取消一单试试（顺便验证库存回补和状态机）：

1. 拿一个**待支付**订单的 id：`POST http://localhost:8080/api/v1/orders/{id}/cancel`（带 token）：

```text
curl.exe -X POST http://localhost:8080/api/v1/orders/1/cancel -H "Authorization: <token>"
```

2. 验证：
   - `t_order` 里该单状态变为已取消（`CANCELLED`），`cancel_time` 被回填；
   - `t_product_sku.stock` 恢复了（回到下单前的数字）——取消会逐条回补库存；
   - 把同一单**再取消一次** → 返回 `2008 ORDER_STATUS_INVALID`（状态机 CAS 生效，防并发重复取消）；
   - 已支付/已发货的订单不能取消（同样返回 `2008`）。

> 顺手可试：拿一个**不属于你**的订单 id 取消 → 返回 `2007 ORDER_NOT_FOUND`（归属校验，防越权）。

---

## 附录 A：接口速查表

所有接口统一前缀 `/api/v1`，除注册/登录/`/error` 外都需要请求头：`Authorization: <token>`

| 模块 | 方法 & 路径 | 干什么 | 关键参数 |
|---|---|---|---|
| 认证 | `POST /auth/register` | 注册 | username(3-20位字母数字下划线), password(6-32位), nickname(可选) |
| 认证 | `POST /auth/login` | 登录，返回 token | username, password |
| 认证 | `POST /auth/logout` | 退出登录 | — |
| 认证 | `GET /auth/me` | 查自己信息 | — |
| 商品 | `GET /products?page=&pageSize=` | 在售商品分页列表 | page≥1, pageSize 1..100 |
| 商品 | `GET /products/{id}` | 商品详情 + SKU 列表 | — |
| 购物车 | `GET /cart` | 购物车列表 | — |
| 购物车 | `POST /cart` | 加购 | skuId 必填, quantity 1..99 默认 1 |
| 购物车 | `PUT /cart/{id}` | 改数量 | quantity 1..99 |
| 购物车 | `DELETE /cart/{id}` | 删条目 | — |
| 购物车 | `DELETE /cart` | 清空购物车 | — |
| 订单 | `POST /orders` | 下单 | items[{skuId,quantity}], receiverName/Phone/Address 必填 |
| 订单 | `GET /orders?page=&pageSize=` | 我的订单分页 | — |
| 订单 | `GET /orders/{id}` | 订单详情（含明细） | — |
| 订单 | `POST /orders/{id}/cancel` | 取消订单（仅待支付） | — |
| AI | `POST /chat` | 普通问答 | message 必填, conversationId 可空 |
| AI | `POST /chat/stream` | SSE 流式问答 | 同上 |
| AI | `POST /chat/conversations` | 新建会话 | body 可空 |
| AI | `GET /chat/conversations` | 会话列表 | — |
| AI | `GET /chat/conversations/{id}/messages` | 会话消息 | — |

**错误码速查**：通用 `200 成功 / 400 参数错 / 401 未登录 / 403 无权限 / 404 不存在 / 500 服务器错`；用户域 `1001 用户名已存在 / 1002 用户不存在 / 1003 密码错误 / 1004 用户被禁用`；电商域 `2001 商品不存在 / 2002 SKU 不存在 / 2003 SKU 已下架 / 2004 库存不足 / 2005 购物车条目不存在 / 2006 购物车为空 / 2007 订单不存在 / 2008 订单状态不合法`；AI 域 `3001 AI 服务错误`。

## 附录 B：8 张表速查

| 表 | 装什么 | 关键字段 / 约束 |
|---|---|---|
| `t_user` | 用户 | `username` 唯一(`uk_username`), `password_hash` BCrypt 密文, `role`/`status` |
| `t_product` | 商品 SPU | 名称/副标题/主图/详情文本/上架状态 |
| `t_product_sku` | 商品 SKU（规格+库存） | `price DECIMAL(10,2)`, `stock`, `sales`, `version`(乐观锁预留) |
| `t_cart` | 购物车 | `user_id+sku_id` 唯一(`uk_user_sku`), `quantity` |
| `t_order` | 订单主表 | `order_no` 唯一(`uk_order_no`), `total_amount`, `status`, `pay_time`, `cancel_time`, `version` |
| `t_order_item` | 订单明细 | 商品名/SKU 名/价格**快照**, `quantity` |
| `t_conversation` | AI 会话 | `user_id`, `biz_type`, `title` |
| `t_message` | AI 消息 | `conversation_id`, `role`(user/assistant), `content` |

**种子数据**：4 个商品（AirSound Pro 耳机 ¥399 / 闪充宝 65W GaN ¥89-129 / 云朵唇釉 ¥69 / 轻氧手环 ¥199-249）、8 个 SKU。

## 附录 C：常见卡点排查（FAQ）

**Q1：`mvn spring-boot:run` 起不来，报 MySQL 连接错误**
后端连的是 `jdbc:mysql://192.168.6.102:3306/ai_mall`，用户名/密码默认 `root` / `root123`（来自 `application.yml` 的环境变量缺省值）。按顺序排查：① MySQL 服务有没有启动（services.msc 看 MySQL80）；② root 密码是不是 `root123`，不是的话改 `backend/src/main/resources/application.yml` 把 `root123` 换成你的密码（改完重启后端）；③ 你这台机器能不能访问 `192.168.6.102`（能不能 ping 通 / 那是开发机地址，本地装 MySQL 的话可改成 `localhost`）。

**Q2：提示"端口 8080 或 5173 已被占用"**
多半是之前有残留进程。`Ctrl + C` 关掉旧窗口；还不行就在终端里 `netstat -ano | findstr 8080` 找到 PID，再 `taskkill /PID <pid> /F`。

**Q3：请求返回 401「未登录或登录已过期」**
请求头没带 `Authorization`，或带的 token 过期/被篡改。token 有效期 7 天；重新登录拿新 token 即可。登录/注册两个接口是白名单，不带 token 也能通。

**Q4：前端页面调接口报 CORS / 跨域错**
正常不需要管 CORS——前端页面只在 `localhost:5173` 打开、且请求都走 `/api` 代理。如果你开着 5173 访问的却是奇怪来源，确认你用的地址是 `http://localhost:5173` 或 `http://127.0.0.1:5173`（后端 CORS 正是放行这两个来源）。

**Q5：`npm run dev` 报版本/依赖错误**
升级 Node.js 到 LTS 版（20.x 或更高），然后删掉 `frontend/node_modules` 和 `package-lock.json` 重新 `npm install`。

**Q6：AI 问答报 3001，或流式输出兜底文案「[AI 服务暂时不可用…]」**
不是你的操作问题。`application.yml` 里 `spring.ai.openai.api-key` 是环境变量缺省占位值，需要真实的 Key 才能调通 `https://opencode.ai/zen/go` 中转服务。要么配置环境变量 `DEEPSEEK_API_KEY`，要么把 yml 里的 key 换成真实 Key，然后重启后端。

**Q7：mvn 下载依赖特别慢**
本项目已配置阿里云镜像加速（仅本仓库生效），首次下载几分钟属正常；如果走的是公网仓库很慢，确认你的 `backend` 下存在 Maven 镜像配置且没有被全局 setting.xml 覆盖。

**Q8：改了 `application.yml` 不生效**
配置文件只在启动时读一次。改完必须 `Ctrl + C` 停掉后端再重新 `mvn spring-boot:run`（前端同理，`npm run dev` 有热更新可以不重启）。

**Q9：数据库里中文乱码 / 日期显示怪**
连接命令或客户端字符集问题。项目后端已配置 Jackson 日期格式 `yyyy-MM-dd HH:mm:ss`、时区 `Asia/Shanghai`；只是命令行查看时可在 `mysql` 命令后加 `--default-character-set=utf8mb4` 试试。

---

*祝你一次跑通。跑通了，你就是能亲手"点亮"一个完整电商闭环的人了。*