# Database Agent 接入 zorth-web-sql-service 方案

状态：方案已定，等待对方补齐只读模式和测试环境
关联：`plan/database-agent-integration-questions.md`（问题清单）、`openspec/changes/archive/2026-08-24-database-agent-tools/`

已确认的三个决策：

- 认证走**用户 Token 透传**（3.6 方案 A）
- 第一期**元数据和 `executeQuery` 一起接**，但只接白名单内确认为只读账号的数据源
- JDBC 直连路径**保留但默认关闭**，仅用于本地开发和测试

---

## 1. 结论先说

对方回答后，四个 Tool 的归属如下：

| Tool | 接入方式 | 说明 |
| --- | --- | --- |
| `listTables` | 改调对方 | `GET /api/v1/data-sources/{id}/tables`，游标分页，单页 200 |
| `getTableSchema` | 改调对方 | `GET .../table-detail`，一次一张表，需循环 |
| `checkSql` | **留在 AI 侧** | 对方没有 SQL Guard，只有防多语句的扫描器 |
| `executeQuery` | 改调对方，但必须加护栏 | `POST /api/v1/sql/executions` 当前允许任意单条 SQL |

一句话：**元数据搬过去，SQL 安全留下来。**

AI 侧不再自己维护数据源账号和 JDBC 连接，避免和他们的权限、连接池、执行历史各做一套。

---

## 2. 目标架构

```text
用户
  │  Authorization: Bearer <user-token>
  ▼
AI Platform  /api/v1/ai/agent
  │  {message, conversationId?, datasourceId?, database?}
  ▼
SpringAiAgentService ──► ToolContext（requestId / conversationId / datasourceId / database / 用户凭据）
  │
  ▼
DatabaseTools
  ├── checkSql ────────────► SqlValidationService（JSqlParser，AI 侧）
  └── listTables / getTableSchema / executeQuery
             │
             ▼
      WebSqlServiceClient（HTTP）
             │  Authorization 透传 + X-Request-Id
             ▼
   zorth-web-sql-service ──► 数据源权限 / 连接池 / 执行历史
             ▼
          目标数据库
```

`executeQuery` 内部顺序固定为：**先跑我方 SQL Guard，再发 HTTP**。Guard 不通过就不发请求。

---

## 3. 必须处理的六个差异

对方的回答里有几处和我们现在的实现对不上，这是本方案的主要工作量。

### 3.1 多了一个必填的 `database`

他们的元数据和执行接口都要求 `database`（MySQL 是 catalog，PostgreSQL 实际是 schema），而我们现在的上下文里只有 `datasourceId`。

处理：

- `AgentRequest` 增加可选 `database`，和 `datasourceId` 一样由服务端写进 `ToolContext`，**不给模型生成**。
- 前端已经有 `?dataSourceId=...&database=...`，可以直接沿用同一对值。
- 缺 `database` 时，Tool 返回结构化 `MISSING_DATABASE`，让模型提示用户先选库，而不是瞎猜。
- 以后可以加 `listDatabases` Tool（他们有 `/databases` 接口），但不进第一期。

### 3.2 只读没有任何一层保证

他们明确说：没有只读账号要求、没有 parser 拦截、没有 `Connection.setReadOnly(true)`，实际是 `autoCommit=true`，允许 `SELECT / INSERT / UPDATE / DELETE / DDL / CALL`。

这直接冲突我们已归档的规格：`executeQuery` 自身必须是安全边界，不能假设模型先调过 `checkSql`。

分三层处理，**不能只靠第一层**：

1. AI 侧 `SqlValidationService`：只允许单条只读 `SELECT` / `WITH`（已实现）。
2. 数据源层面：给 Agent 用的数据源配**只读数据库账号**。这是唯一不依赖代码正确性的一层。
3. 对方服务端：新增强制只读模式（下面第 6 节的请求项）。

在第 2、3 层落地之前，第一期建议只接**已确认为只读账号**的数据源，配置里显式白名单，避免我方 Guard 成为唯一防线。

### 3.3 结果格式不一样

他们返回 `rows: [["9007199254740993", "12.50"]]`，是二维数组；我们的 `QueryResult.rows` 是 `List<Map<String, Object>>`。

处理：在 client 里按 `columns[].label` 组装成 Map，对外的 `QueryResult` 契约不变，Tool 和模型都不受影响。

另外 BIGINT / DECIMAL 他们返回字符串（避免精度丢失）。我们保持字符串，不做数字转换。副作用是模型不应该自己对结果做算术——本来就该在 SQL 里聚合，system prompt 里补一句。

BLOB 他们返回 `{binary:true,size,base64:null}`，直接透传给模型即可，不需要特殊处理。

### 3.4 行数限制交给他们，我方不再改写 SQL

他们支持请求传 `rowLimit`，默认 1000、上限 100000，并返回 `truncated`。

处理：

- 传 `rowLimit = ai.agent.database.max-rows`（默认 200），直接用他们的 `truncated`。
- **停用 `SqlLimitApplier`**（我方给 SQL 补 `LIMIT` 的逻辑）。他们支持 MySQL / PostgreSQL / GBase 8a，方言改写交给数据库自己处理更稳，也少一个改错 SQL 的风险点。JDBC 直连路径保留该逻辑。
- 结果体积：他们默认 100 MiB 且不可覆盖，远大于我们的 1 MiB。因为 `rowLimit=200` 已经把量压住了，我方保留收到响应后的体积检查作为兜底。

### 3.5 超时对不上

他们的 SQL 超时服务端固定，默认 60 秒，**调用方不能传**；我们的配置是 10 秒。

Agent 场景里 60 秒会把整轮对话卡死。处理：

- 第一期：HTTP client 读超时设 70 秒（他们建议 70~75），接受最坏情况慢。
- 同时提请他们支持按请求传 `timeoutSeconds`，或给 Agent 通道配更低默认值（见第 6 节）。
- 我方 `query-timeout-seconds` 在走他们接口时不再直接生效，配置项加注释说明，避免误解。

### 3.6 认证：我们现在没有用户身份

他们要求每次请求带用户 Bearer Token，且 `userId` 必须从 Token 派生，不接受调用方自己传。我们现在 `userId` 只是个可选字段。

两个方向：

**方案 A：透传用户 Token —— 已选定**

- 客户端调 `/api/v1/ai/agent` 时带上他们体系的 Bearer Token。
- AI 服务把该 Token 原样转发给 web-sql-service。
- 好处：权限、可见性、执行历史全部落在真实用户身上；对方零改动。
- 代价：AI 服务需要处理凭据。约束写死三条：**只在内存中传递、不进日志、不进 `AgentRequest` 记录、不进任何 Tool Schema**。
- 并发限制「单用户 3」按真实用户分摊，不会全局打架。

**方案 B：服务身份 + 用户委托**

- 需要他们新增 server-to-server 认证和委托头。
- 而且如果 Agent 用单一服务账号，所有请求共享「单用户并发 3」，会直接成为瓶颈——他们自己也点出了这一点。
- 结论：不采纳。

透传带来的接口变化：`POST /api/v1/ai/agent` 在请求带 `datasourceId` 时，要求同时带 `Authorization` 头。不带数据源的普通 Agent 请求和 `/api/v1/ai/chat` 不受影响，保持匿名可用。缺 Token 时 Tool 返回结构化 `AUTH_ERROR`，不抛 HTTP 500。

---

## 4. 代码改动

### 4.1 端口与适配器

`ai-datasource` 现在是「JDBC 实现」，要拆成「接口 + 两个实现」：

```text
ai-datasource
├── port
│   ├── DatabaseMetadataPort      listTables / getTableSchemas
│   └── QueryExecutionPort        execute
├── jdbc/        现有 JDBC 实现（保留，供本地和测试）
├── websql/      新增 HTTP 实现（对接 zorth-web-sql-service）
├── sql/         SqlValidationService（两条路径共用，不变）
└── model/       TableSchema / ColumnSchema / QueryResult / SqlCheckResult（不变）
```

`DatabaseTools` 只依赖 port，四个 `@Tool` 的签名和返回结构**不变**，已归档的 `database-agent-tools` 规格不用改。

用 `ai.datasource.provider=jdbc|web-sql` 选实现，默认 `web-sql`。`jdbc` 只用于本地开发和测试：启动时若 provider 为 `jdbc` 且非本地环境，打 WARN；生产 profile 直接拒绝启动，避免留出绕过对方权限和审计的通道。

### 4.2 新增

- `WebSqlServiceClient`：基于 `RestClient`，负责 URL、请求头、超时、错误映射。
- `WebSqlMetadataAdapter`：
  - `listTables`：翻页取到我方上限（建议 200 条）就停，超出时在结果里说明「仅列出前 N 张表」，防止撑爆上下文。
  - `getTableSchemas`：**串行**循环调 `table-detail`（他们不支持批量），单次请求最多 5 张表，超出直接拒绝并让模型收敛范围。串行是为了不撞「单用户并发 3」。
  - 表备注：`table-detail` 里没有，需要时用 `keyword=<表名>` 从列表接口补一次。
- `WebSqlQueryAdapter`：
  - 先 `SqlValidationService.requireValid(sql)`。
  - 生成 `executionId`（UUID），随请求发出，并写进我方审计日志，和他们的执行历史对得上。
  - 带 `X-Request-Id` = 我方 `requestId`。
  - 响应二维数组转 `QueryResult`。

### 4.3 改动

- `AgentRequest`：加可选 `database`。
- `ToolContextKeys` / `AgentContext`：加 `DATABASE`，加凭据键。
- `DatabaseToolAudit`：加 `executionId`、`database`；**明确不打印凭据**，补一条断言测试。
- `AiConfiguration`：按 provider 选 bean。
- `application.yml`：新增 web-sql 段。

```yaml
ai:
  datasource:
    provider: web-sql
    web-sql:
      base-url: ${WEB_SQL_BASE_URL:http://localhost:8080}
      connect-timeout-seconds: 5
      read-timeout-seconds: 70
      max-tables-per-schema-call: 5
      max-listed-tables: 200
      # 只读兜底：对方补上强制只读模式前，只允许这些确认为只读账号的数据源
      allowed-datasource-ids: []
```

`allowed-datasource-ids` 为空表示不放行任何数据源，属于 fail closed。Phase 2 之后可以取消该限制。

### 4.4 错误映射

他们用真实 HTTP 状态码 + `{requestId, code, message, details}`。映射到我方 `DatabaseToolFailure.errorType`：

| 他们 | 我方 `errorType` | 模型能否自行修复 |
| --- | --- | --- |
| 404 `DATA_SOURCE_NOT_FOUND` | `DATASOURCE_NOT_FOUND` | 否，让用户换数据源 |
| 404 `DATABASE_NOT_FOUND` | `DATABASE_NOT_FOUND` | 否 |
| 404 `TABLE_NOT_FOUND` | `TABLE_NOT_FOUND` | 是，重新 `listTables` |
| 422 `SQL_EXECUTION_FAILED` | `SQL_EXECUTION_ERROR` | **是**，主要修复路径 |
| 504 `SQL_EXECUTION_TIMEOUT` | `SQL_TIMEOUT` | 是，缩小范围重试 |
| 429 并发超限 | `RATE_LIMITED` | 否，直接告知用户 |
| 401 / 503 鉴权 | `AUTH_ERROR` | 否 |
| 400 `MULTI_STATEMENT_NOT_SUPPORTED` | `SQL_VALIDATION_ERROR` | 正常情况下我方 Guard 已先拦掉 |

422 要把他们的 `message`、`sqlState`、`vendorErrorCode` 带给模型——`Unknown column 'order_time'` 正是 Case 4 的修复依据。他们提醒这段文本随引擎变化，所以我方只做长度截断，不解析文本。

---

## 5. 分期

**Phase 0 — 不依赖对方，可立即做**

抽 port、保留 JDBC 实现、加 `database` 上下文、审计加字段。测试仍用 H2。

**Phase 1 — 接入现有接口（Token 透传，含 executeQuery）**

三个 Tool 改调他们；`checkSql` 不动。控制器接 `Authorization` 并透传。用打桩的 HTTP 服务做契约测试。
限制：只读**仅靠我方 Guard + 只读数据库账号**，因此 `allowed-datasource-ids` 白名单必须非空且逐个人工确认账号只读。白名单外的数据源返回 `DATASOURCE_NOT_ALLOWED`。

**Phase 2 — 等他们补只读模式**

改用只读执行 API / `readOnly=true`，带上 `source=AI_AGENT`。之后才解除 `allowed-datasource-ids` 白名单。

**Phase 3 — 体验优化**

批量表结构、按备注搜表、`listDatabases`、表数量过多时的 `searchTables`。

---

## 6. 需要对方做的事（按优先级）

1. **强制只读执行模式**（阻塞 Phase 2）。请求参数或专用 API 都行；同时建议数据源支持配只读账号。
2. **明确 Token 透传是否被接受**（阻塞 Phase 1）。AI 服务作为中间人转发用户 Token，是否符合他们的安全要求。
3. **审计加 `source=WEB_SQL_EDITOR|AI_AGENT`**，并把 `client_ip` 真正填上。
4. **测试环境 + 固定测试 `dataSourceId` + 测试账号**（阻塞联调）。
5. **按请求传 `timeoutSeconds`**，或给 Agent 通道更低默认值。60 秒对话场景太长。
6. 并发：确认单用户 3 是否够。Agent 一轮问答最多 4~8 次串行调用，正常够用；多用户并发时要看整体 50 的上限。
7. 次要：`table-detail` 返回表备注；支持批量表结构；表搜索支持备注。

---

## 7. 已知取舍

- **只读在 Phase 1 是单点。** 我方 JSqlParser Guard 是唯一的代码防线，所以配只读数据库账号并用白名单限制数据源。这是本方案最大的残留风险，Phase 2 才真正解决。
- **JDBC 直连路径保留但默认关闭。** 本地和测试不依赖对方服务；生产 profile 禁止启用，避免影子通道。
- **不支持 Oracle / SQL Server。** 他们只注册了 MySQL、PostgreSQL、GBase 8a。
- **表多时仍会撑上下文。** 单页 200 且不支持按备注搜索，`searchTables` 依然要留到后面。
- **结果是字符串数值。** 模型不要自己算，聚合放 SQL 里。
- **我方的 `query-timeout-seconds` 在 web-sql 路径下不生效。**
