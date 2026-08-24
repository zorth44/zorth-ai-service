# AI Platform

AI Platform 是一个基于 Spring Boot 和 Spring AI 构建的企业 AI 应用基础平台。本阶段同时提供与具体模型 Provider 解耦的同步聊天能力和 Spring AI Tool Calling 基础，使模型可以自主选择并执行确定性的应用 Tool。

## 当前能力

- Spring Boot 4.0.x + Spring AI 2.0.x 基础工程
- OpenAI-Compatible Chat Model 接入
- Provider-neutral `AiChatService`
- 同步聊天接口 `POST /api/v1/ai/chat`
- Provider-neutral `AiAgentService`
- Tool Calling 接口 `POST /api/v1/ai/agent`
- Date、Date Difference、Calculator 和 System Info Tool
- Database Agent Tools：`listTables`、`getTableSchema`、`checkSql`、`executeQuery`
- 只读 SQL 校验、查询行数/超时/结果大小限制，以及 SQL 审计日志
- Spring AI `ToolCallingAdvisor` 管理的多步 Tool Calling
- Server-controlled `ToolContext`（request ID、conversationId、userId、datasourceId）
- Tool 执行耗时、结果状态和安全异常处理
- 输入校验和安全的统一异常响应
- Actuator 健康检查
- 不依赖真实模型的自动测试

## 尚未实现

以下能力属于后续阶段，当前均为 **NOT IMPLEMENTED YET**：

- Semantic Metadata、searchTables、业务知识库
- RAG 和 Vector Store
- MCP
- Chat Memory 和 Conversation 持久化
- SSE / Streaming
- 多模型动态路由
- 用户权限和持久化 Audit

## 项目结构

```text
ai-platform
├── ai-core         # Provider-neutral 聊天契约和 Spring AI ChatClient 适配
├── ai-agent        # Agent 契约、Spring AI Runtime、Tool、ToolContext 和执行日志
├── ai-datasource   # Datasource 注册、元数据、SQL 校验和只读查询执行
└── ai-server       # Spring Boot、REST、配置、异常处理和 Actuator
```

调用链：

```text
Client
  → AiChatController
  → AiChatService
  → SpringAiChatService
  → ChatClient
  → ChatModel
  → OpenAI-Compatible API
```

Tool Calling 调用链：

```text
Client
  → AiAgentController
  → AiAgentService
  → ChatClient + ToolCallingAdvisor
  → LLM decides whether to call a tool
  → Spring Boot executes Date / Calculator / System / Database Tool
  → Spring AI returns structured Tool Result to the LLM
  → next Tool Call or final answer
```

应用没有自行实现 Agent `while` 循环；连续 Tool Call、Tool Result 回传和后续模型调用由 Spring AI `ToolCallingAdvisor` 负责。

## 环境要求

- Java 17 或更高版本
- Maven 3.6.3 或更高版本
- 一个可用的 OpenAI-Compatible Chat Model

当前固定版本：

- Spring Boot `4.0.7`
- Spring AI `2.0.0`

## 模型配置

启动服务前设置以下环境变量：

| 环境变量 | 必需 | 说明 | 默认值 |
| --- | --- | --- | --- |
| `AI_API_KEY` | 是 | Provider API Key | 无 |
| `AI_MODEL` | 否 | Chat Model 名称 | `deepseek-v4-flash` |
| `AI_BASE_URL` | 否 | OpenAI-Compatible API 地址 | `https://api.deepseek.com` |
| `AI_PLATFORM_ENVIRONMENT` | 否 | System Info Tool 返回的运行环境 | `local` |
| `AI_PLATFORM_VERSION` | 否 | System Info Tool 返回的应用版本 | `0.0.1-SNAPSHOT` |

示例中的值均为占位符，请勿将真实 API Key、Token 或密码提交到 Git。

```bash
export AI_API_KEY="your-api-key"
export AI_MODEL="your-model-name"
export AI_BASE_URL="https://your-provider.example.com"
export AI_PLATFORM_ENVIRONMENT="development"
export AI_PLATFORM_VERSION="0.0.1-SNAPSHOT"
```

Spring AI 2.0 使用的主要配置键位于 [application.yml](ai-server/src/main/resources/application.yml)：

```text
spring.ai.openai.api-key
spring.ai.openai.base-url
spring.ai.openai.chat.model
spring.ai.openai.chat.temperature
```

## 构建与启动

在项目根目录执行：

```bash
mvn clean package
java -jar ai-server/target/ai-server-0.0.1-SNAPSHOT.jar
```

服务默认监听 `http://localhost:8080`。和本机 `zorth-web-sql-service` 一起跑时改用 `8081`，步骤见 [docs/local-web-sql.md](docs/local-web-sql.md)。

## 聊天接口

请求：

```bash
curl \
  -X POST \
  http://localhost:8080/api/v1/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "什么是数据库索引？"
  }'
```

成功响应：

```json
{
  "content": "数据库索引是……"
}
```

`message` 不能为空、不能只包含空白字符，最大长度为 10,000 个字符。无效请求返回 HTTP 400：

```json
{
  "code": "INVALID_REQUEST",
  "message": "The request is invalid"
}
```

模型调用失败时，客户端只会收到经过清理的错误，不会获得 Provider SDK 异常、堆栈或敏感配置。

## Tool Calling Agent 接口

请求：

```bash
curl \
  -X POST \
  http://localhost:8080/api/v1/ai/agent \
  -H "Content-Type: application/json" \
  -d '{
    "message": "今天是几号？"
  }'
```

成功响应保持最小结构：

```json
{
  "content": "今天是 2026 年 8 月 21 日。"
}
```

当前 Agent 固定注册以下基础 Tool：

| Tool | 用途 | 结构化结果 |
| --- | --- | --- |
| `getCurrentDate` | 获取服务器当前日期和星期 | `date`, `dayOfWeek` |
| `calculateDaysBetween` | 计算已知起止日期间的有符号天数 | `days` |
| `calculate` | `ADD`、`SUBTRACT`、`MULTIPLY`、`DIVIDE` | `result` |
| `getSystemInfo` | 获取当前服务名称、环境和版本 | `applicationName`, `environment`, `version` |

默认 `ai.datasource.provider=web-sql`：元数据和 `executeQuery` 走 zorth-web-sql-service，`checkSql` 留在 AI 侧。本地和 SQL 编辑器联调见 [docs/local-web-sql.md](docs/local-web-sql.md)。请求带 `datasourceId` 时必须同时带 `Authorization` 和 `database`。`provider=jdbc` 只用于测试和本机 H2。

当请求包含 `datasourceId` 时，额外注册 Database Tools：

| Tool | 用途 | 结构化结果 |
| --- | --- | --- |
| `listTables` | 列出当前数据源中的表 | 表名列表 |
| `getTableSchema` | 获取一张或多张表的字段、类型、主键和备注 | `TableSchema` |
| `checkSql` | 校验 SQL 是否为单条只读 SELECT | `valid`, `errorType`, `message` |
| `executeQuery` | 执行已校验的只读查询，并限制行数与结果大小 | `columns`, `rows`, `rowCount`, `truncated` |

`datasourceId`、`conversationId`、`userId` 由服务端写入 `ToolContext`，不会出现在 Tool JSON Schema 中，模型不能伪造数据源。`executeQuery` 会再次做安全校验，不依赖模型一定先调用 `checkSql`。

可选请求示例：

```bash
curl \
  -X POST \
  http://localhost:8080/api/v1/ai/agent \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv-1",
    "datasourceId": "demo",
    "userId": "user-1",
    "message": "数据库里有哪些用户相关的表？"
  }'
```

仅发送 `{ "message": "..." }` 的旧客户端仍然有效，此时不会注册 Database Tools。`POST /api/v1/ai/chat` 保持 `{ "message": "..." }` → `{ "content": "..." }`，不会调用数据库 Tool。

是否调用 Tool、调用哪个 Tool、以及是否继续调用其他 Tool，由 LLM 根据用户请求、Tool Description 和 Tool Schema 决定。普通知识问题允许直接回答，不会强制调用 Tool。

### Datasource 与查询限制

在 `application.yml` 中按 id 声明 JDBC 数据源，并配置查询边界：

```yaml
ai:
  datasources:
    demo:
      jdbc-url: jdbc:postgresql://localhost:5432/demo
      username: demo
      password: ${DEMO_DB_PASSWORD:}
      driver-class-name: org.postgresql.Driver
  agent:
    database:
      max-rows: 200
      query-timeout-seconds: 10
      max-result-bytes: 1048576
      max-sql-length: 10000
      max-complexity: 12
      include-views: false
```

对应的 JDBC 驱动需要自行放到运行时 classpath。默认配置不包含任何数据源，应用可以空注册表启动。只允许单条 `SELECT` / `WITH ... SELECT`；`INSERT`、`UPDATE`、`DELETE`、`DROP` 等写操作和 DDL 会被拒绝。

### Tool Arguments 与 ToolContext

两类输入有明确边界：

| 类型 | 来源 | LLM 是否可见 | 示例 |
| --- | --- | --- | --- |
| Tool Arguments | LLM 根据用户目标生成 | 是 | `left`, `right`, `operation`, `startDate`, `endDate` |
| `ToolContext` | Spring Boot 在服务端生成 | 否 | `requestId`, `conversationId`, `userId`, `datasourceId` |

`requestId` 和 `datasourceId` 不属于任何 Tool JSON Schema，模型无法生成或覆盖它们。

### Tool 执行日志

Tool 是否真正执行应通过服务端日志确认，不能仅根据最终回答推断。日志记录 request ID、Tool 名称、耗时和状态，但不会通用地打印完整参数或结果：

```text
Agent request started requestId=... status=STARTED
Tool execution started requestId=... toolName=getCurrentDate status=STARTED
Tool execution completed requestId=... toolName=getCurrentDate durationMs=2 status=SUCCESS
Agent request completed requestId=... durationMs=850 status=SUCCESS
```

Tool 或模型执行失败会保留服务端诊断日志；HTTP 客户端只会获得清理后的稳定错误，不会收到堆栈、Tool 参数、Provider 细节或内部配置。

## 健康检查

```bash
curl http://localhost:8080/actuator/health
```

预期响应：

```json
{
  "status": "UP"
}
```

健康检查仅验证应用状态，不会调用模型或产生 Provider 请求费用。

## 自动测试

```bash
mvn clean test
```

默认测试使用 Mock、Fake、H2 或脚本化 `ChatModel`，不需要真实 `AI_API_KEY`，也不会访问模型服务。测试包括离线的基础 Tool Calling 场景，以及 `listTables → getTableSchema → checkSql → executeQuery` 和 SQL 失败后修正的 Database Agent 多步场景。

## 可选的真实 Provider Tool Calling 验证

真实模型验证不属于默认 Maven 测试生命周期。需要人工验证时：

1. 在当前 shell 中提供真实的 `AI_API_KEY`、`AI_MODEL`、适用的 `AI_BASE_URL`，以及希望 System Info Tool 返回的 `AI_PLATFORM_ENVIRONMENT` 和 `AI_PLATFORM_VERSION`。
2. 执行 `mvn clean package`。
3. 执行 `java -jar ai-server/target/ai-server-0.0.1-SNAPSHOT.jar`。
4. 调用 `/actuator/health`，确认服务状态为 `UP`；健康检查本身不会调用模型。
5. 依次向 `POST /api/v1/ai/agent` 提交以下请求，并同时观察服务端日志。

### Case 1：Single Tool

```json
{ "message": "今天是几号？" }
```

确认日志出现 `toolName=getCurrentDate` 和 `status=SUCCESS`。

### Case 2：Calculator

```json
{ "message": "12345 乘以 6789 是多少？请使用可用的确定性计算能力。" }
```

确认日志出现 `toolName=calculate`；预期计算结果为 `83810205`。

### Case 3：Structured System Result

```json
{ "message": "当前 AI Platform 运行在什么环境，版本是什么？" }
```

确认日志出现 `toolName=getSystemInfo`，回答内容与 `AI_PLATFORM_ENVIRONMENT` 和 `AI_PLATFORM_VERSION` 一致。

### Case 4：Multi-Step Tool Calling

```json
{ "message": "距离 2027 年 1 月 1 日还有多少天？" }
```

一种预期路径是 `getCurrentDate → calculateDaysBetween → final answer`。不同模型可能选择其他合理等价路径；验收重点是系统能够连续执行 Tool 并将中间结果返回模型，不要求所有 Provider 每次生成完全相同的调用序列。

### Case 5：No Tool

```json
{ "message": "解释一下什么是 Redis。" }
```

确认请求有 Agent start/completed 日志，但没有 Date、Calculator 或 System Tool execution 日志。

### Case 6：Tool Error

```json
{ "message": "请使用计算 Tool 计算 10 除以 0。" }
```

确认日志记录 `toolName=calculate`、`status=FAILURE` 和 request ID。请求必须保持受控：模型可能基于 Tool 错误生成安全说明，或者 HTTP 边界返回清理后的 `AI_SERVICE_ERROR`；两种情况下都不得泄露堆栈、参数或内部配置，且后续请求仍应正常处理。

### Case 7：Database Agent

先配置一个只读可达的 `ai.datasources.<id>`，再请求：

```json
{
  "conversationId": "conv-1",
  "datasourceId": "demo",
  "message": "查询今年每个月的订单金额。"
}
```

一种预期路径是 `listTables → getTableSchema → checkSql → executeQuery → final answer`。日志应出现对应 `toolName` 和 `Database tool audit` 记录，包含 `datasourceId` 和 SQL。要求删除表或执行 `DELETE`/`DROP` 时必须被拒绝，且数据库中的数据不能被修改。

完成验证后，清除 shell 中的敏感环境变量；不要把真实凭据写入 README、`application.yml`、测试代码或其他受版本控制的文件。
