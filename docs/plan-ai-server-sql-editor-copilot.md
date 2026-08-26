# Plan：SQL 编辑器 Copilot（AI 服务配合）

状态：草案。本仓库配合项。编辑器侧计划在 sibling `zorth-web-sql-editor/docs/plan-web-sql-editor-copilot.md`。本文与那边 `docs/plan-ai-server-sql-editor-copilot.md` 同文。

Web SQL 编辑器要在工作台右侧接 Copilot：生成 SQL、插入当前页签、执行失败后一键修复。浏览器 **只调现有** `POST /api/v1/ai/agent`，带用户 Bearer、`datasourceId`、`database`。不调 `/api/v1/ai/chat`（没有 Database Tools）。

编辑器不自建 LLM，也不替 Agent 跑 SQL。元数据、`checkSql`、只读 `executeQuery`（`readOnly=true`、`source=AI_AGENT`）维持现状。本文件只列 **编辑器 Copilot 要你们改或确认的部分**。SQL service 契约不改。

## 1. 编辑器怎么调你们

与 [local-web-sql.md](./local-web-sql.md) 里的 curl 相同，只是调用方变成 Vue：

```http
POST /api/v1/ai/agent
Authorization: Bearer <用户 Token，与调 web-sql 的是同一个>
Content-Type: application/json

{
  "message": "<前端拼好的上下文 + 用户原话>",
  "conversationId": "<SQL 页签 id，给审计>",
  "datasourceId": "<当前页签数据源>",
  "database": "<当前 NAMESPACE，MySQL 库名 / PostgreSQL schema>"
}
```

前端会在 `message` 里附带：方言、数据源名、当前编辑器 SQL（或选区）、最近一次失败语句和错误。用户可见的只有自己打的那句。

成功响应继续用 `{ "content", "conversationId" }` 即可。编辑器用 `content` 里的 Markdown **` ```sql ` 代码块** 做「插入 / 插入并运行」。没有代码块就没有插入按钮。

本地拓扑不变：SQL `8080`，AI `8081`。编辑器开发代理会把 `/ai-api` 转到 `8081`。

## 2. 必须做：数据源白名单不能挡编辑器用户

当前 `ai.datasource.web-sql.allowed-datasource-ids` 默认空列表，fail closed，Tool 返回 `DATASOURCE_NOT_ALLOWED`。这对早期联调合理，**不能**作为编辑器 Copilot 的产品行为：用户能在编辑器里看见并查询的数据源，Copilot 必须都能用。

**建议**

- 编辑器 / 生产路径：不要再用静态 ID 列表当鉴权。用户能不能碰这个库，由 web-sql 凭 Bearer + 产品可见性决定。你们已经透传 Token；`404 DATA_SOURCE_NOT_FOUND` 就是「看不见」。
- 静态白名单若保留，只作为本地/应急开关（例如 `allowed-datasource-ids` 非空才启用限制），默认对 web-sql provider **不限制 ID**。
- 继续禁止生产环境 `provider=jdbc`。

不改这一项，编辑器 Copilot 只能打配置里那几个库，功能无法上线。

## 3. 必须做：Copilot 口径的系统提示

Database Agent 今天的指导是：发现表 → 看 schema → 生成只读 SQL → 校验 → **执行 → 用结果回答**。这是「问数 Agent」。编辑器 Copilot 的交付物是 **可插入的 SQL**，结果网格以用户在 Monaco 里跑的为准。

在 `datasourceId` 存在时的 Database 系统提示中增加（或按请求分模式，见第 6 节）：

1. 提议或修复的 SQL **必须**放在 Markdown ` ```sql ` 代码块里，一块一条完整语句（或用户要的一组只读语句）。不要只在散文里写表名。
2. **可以**调用 `executeQuery` 做只读验证和自我修复（这是准确率来源，不要关掉）。
3. 对用户的最终回答以 SQL 代码块为主：简短说明这条语句在干什么、是否已验证、大约多少行。**不要**把 200 行结果格子贴进 `content` 当答案。
4. 用户在修报错时：给出改正后的完整语句，不要只给 diff 片段。
5. 继续：只读、禁止写；数字单元格可能是字符串，聚合放在 SQL 里做。

不改提示词的后果：插入按钮经常没有；或聊天变成「答案在气泡里」，用户不会往编辑器里放。

前端 v1 也会在 `message` 里重复这些要求，只能当兜底。权威口径应在系统提示。

## 4. 强烈建议：Agent 多轮记忆

`/api/v1/ai/chat` 有 `MessageChatMemoryAdvisor`；`/api/v1/ai/agent` 的 `conversationId` 目前只进 ToolContext 审计，每次只有这一句 `user`。

编辑器 v1 的补偿：每次带上当前编辑器 SQL。因此「先插入再追问」可用。用户 **不插入** 就说「改成按月」，模型看不到上一轮代码块。

建议：当 Agent 请求带了 `conversationId` 时，为这次调用 **opt-in** 同一套 Chat Memory（不要注册成共享 `ChatClient` 的默认 advisor，以免语义抽取等被污染）。记忆窗口保持现有 chat 配置。

这不是编辑器 v1 的上线阻断项，但做了之后追问会稳很多。Chat 与 Agent 的 `conversationId` 空间应继续隔离，或给 Agent 用单独前缀，避免两套产品抢同一段记忆。

## 5. 建议确认、v1 先不改的

| 项 | 说明 |
| --- | --- |
| 同步 Agent | 编辑器 v1 用 loading + 取消（Abort）。一次 10–30 秒可接受。Agent SSE 可后做；若做，事件形态最好与 `/chat/stream` 的 `start` / `delta` / `completed` / `error` 对齐。 |
| 结构化 `sqlBlocks` | v1 解析 fence。以后若响应增加 `sql: string[]` 更稳，编辑器可以改读字段。 |
| `message` 10,000 上限 | 前端会截断上下文。不要为 Copilot 单独加长，除非截断后修复杂脚本不够用。 |
| `checkSql` / JSqlParser | 继续作为 AI 侧只读闸。web-sql 仍是首词分类 + `readOnly=true`。两层都留。 |
| 工具集 | v1 不需要新 Tool。`listTables` 截断、`getTableSchema` 最多 5 张表，保持即可。 |
| 审计 | 继续打 `conversationId`、`datasourceId`、`database`、SQL、`executionId`。不要把 Bearer 和结果行写入审计。 |
| CORS / 同源 | 与 SQL、Auth 一样走网关同源；本地由编辑器 Vite 代理。不要把 AI 配成浏览器直连另端口还要开宽松 CORS。 |

## 6. 可选：`mode` 区分问数与 Copilot

若希望 **同一套 Agent** 继续服务「问数」（结果在对话里）和「编辑器 Copilot」（SQL 在代码块里），建议在 `AgentRequest` 增加可选字段，例如：

```text
mode?: "default" | "sql_copilot"
```

省略 = 今天的 Database Agent。`sql_copilot` = 第 3 节那套提示。编辑器固定传 `sql_copilot`。

v1 也可以不加字段、直接改 Database 系统提示（问数产品会一起变口径）。**有单独问数入口时再加 `mode`，避免两套产品抢一个 prompt。**

不要让模型生成 `datasourceId` / `database` / Token。这些继续只从请求和 Header 进 ToolContext。

## 7. 建议实现顺序（AI 仓库）

1. **白名单策略**：web-sql provider 默认不按静态 ID 拦截；文档和 `local-web-sql.md` 改成「鉴权 = 用户 Token」。本地若仍要锁几个 ID，做成显式开关。
2. **系统提示**：Database prompt 加上代码块 + 回答以 SQL 为主 + 允许只读试跑。补一个断言回复形态的测试（fixture 模型或 prompt 快照）。
3. **联调**：按 [local-web-sql.md](./local-web-sql.md) 起 SQL `8080` + AI `8081`；用编辑器将使用的同一 Token 打「列出相关表并给出 SELECT」；确认 `content` 里有 ` ```sql `，且表名来自真实 `listTables`，不是幻觉。
4. （可后做）Agent 按 `conversationId` 读写 Chat Memory。
5. （可后做）`mode=sql_copilot` 或 Agent SSE。

## 8. 联调验收（AI 侧）

用编辑器即将发出的那类请求（带 `datasourceId`、`database`、Authorization、`message` 里含当前 SQL）：

- 用户对该数据源可见：Tool 能 `listTables` / `getTableSchema`，不再出现「在白名单里才行」。
- 用户不可见的 `datasourceId`：web-sql `404`，映射为现有 `DATASOURCE_NOT_FOUND`（或等价），不 500。
- 生成类问题：`content` 至少一块 ` ```sql `，语句针对真实表名。
- 修复类问题（message 带失败 SQL + 未知列错误）：同一请求内允许 Tool 重试，最终代码块是可执行的改正语句。
- `executeQuery` 审计有 SQL 和 `executionId`，无 Token、无结果行。
- 无 `datasourceId` 的旧 `{ "message" }` Agent 请求行为不变。
- `/api/v1/ai/chat` 行为不变。

## 9. 和编辑器仓库的分工

| | `zorth-ai-service` | `zorth-web-sql-editor` |
| --- | --- | --- |
| 模型与 Tool | 是 | 否 |
| 只读试跑、查 schema | 是（经 web-sql） | 否 |
| 右侧面板、插入/替换 Monaco | 否 | 是 |
| 「用 AI 修复」按钮 | 否 | 是 |
| 静态数据源白名单 | **改策略** | 无法绕过 |
| SQL 必须在 ` ```sql ` 里 | **系统提示** | message 里再写一遍兜底 |
| 多轮记忆 | 建议做 | v1 用编辑器 SQL 补偿 |
| SQL service `readOnly` / `source` | 已接好 | 用户手跑不传 Agent 字段 |

编辑器计划的实现顺序里，MSW 可先做完 UI；**对着真库点「插入」之前，第 2、3 节应已落地。**
