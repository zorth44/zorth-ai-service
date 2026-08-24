# 本地联调 Database Agent ↔ zorth-web-sql-service

默认生产路径是 AI 透传用户 Token，调用 web-sql 的元数据和只读执行接口。JDBC 直连只给单测和本机 H2。

SQL 编辑器自己的启动步骤见 sibling 仓库 `zorth-web-sql-editor/docs/local-development.md`。本文只补两边接到一起时多出来的部分。

## 本地拓扑

```text
假授权 auth-service     8090
SQL service             8080
AI Platform             8081   （8080 已被 SQL 占用）
元数据库 MySQL          127.0.0.1:3306 / sqleditor
目标库                  数据源里配置的 host（须在 SQL 的 CIDR 白名单内）
```

不要把 `AI_API_KEY`、Bearer Token、数据源密码写进 git。白名单 ID 用启动参数或 gitignore 的 `application-local.yml`。

## 1. 先起 SQL 这一侧

1. 本机 MySQL 已有 `sqleditor` 库，并且 SQL 编辑器里已经建过数据源（推荐走编辑器 UI，不要手写密文）。
2. 目标机要能从这台电脑连上。上次联调失败就是目标 `host` 关机，路由变成 `REJECT`。
3. 目标 IP 必须出现在 SQL service 的 `SQL_EDITOR_NETWORK_ALLOWED_CIDRS` 里。仓库默认只有 `127.0.0.0/8` 和 `10.118.247.8/32`，局域网地址要显式加，例如 `10.175.42.8/32`。
4. 启动假授权和 SQL service（密钥用他们文档里的本地示例即可）：

```bash
cd ../zorth-web-sql-editor/auth-service
AUTH_SERVICE_INTERNAL_KEY=local-sql-editor-key node server.js
```

```bash
cd ../zorth-web-sql-editor/service
SQL_EDITOR_METADATA_URL='jdbc:mysql://127.0.0.1:3306/sqleditor?serverTimezone=UTC' \
SQL_EDITOR_METADATA_USERNAME=sqleditor_user \
SQL_EDITOR_METADATA_PASSWORD=sqleditor_password \
SQL_EDITOR_AUTH_CONTEXT_URL='http://127.0.0.1:8090/internal/api/v1/auth/context' \
SQL_EDITOR_AUTH_INTERNAL_SERVICE_KEY='local-sql-editor-key' \
SQL_EDITOR_NETWORK_ALLOWED_CIDRS='127.0.0.0/8,10.175.42.8/32,10.118.247.8/32' \
SQL_EDITOR_NETWORK_DENIED_CIDRS='169.254.0.0/16,::1/128,fe80::/10' \
mvn spring-boot:run
```

CIDR 按你的目标 host 改，不要照抄 `10.175.42.8`。

## 2. 拿到联调三元组

假授权登录任意用户名、长度大于 12 的密码，产品 ID 默认是 `local-product`，和本地数据源的 `product_id` 一致才能看见库。

```bash
TOKEN=$(curl -sS http://127.0.0.1:8090/ldap/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"zor","password":"localdevpassword123","productType":"chinaBank"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')

curl -sS "http://127.0.0.1:8080/api/v1/data-sources" \
  -H "Authorization: Bearer $TOKEN"
```

记下：

| 值 | 来源 | 说明 |
| --- | --- | --- |
| `datasourceId` | 数据源 `id` | 放进 AI 白名单 |
| `database` | `GET .../databases` | 很多数据源 `default_database` 为空，Agent 请求必须自己带 |
| Token | 假授权 | 调 `/api/v1/ai/agent` 时原样放 `Authorization` |

先直连 SQL 确认目标库可用，再起 AI：

```bash
curl -sS "http://127.0.0.1:8080/api/v1/data-sources/${DATASOURCE_ID}/tables?database=${DATABASE}&types=TABLE&pageSize=10" \
  -H "Authorization: Bearer $TOKEN"
```

这里失败就还没轮到 AI。常见原因：目标机不通、CIDR 未放行、凭据解密 key 和写入时不一致。

## 3. 起 AI

不要在仓库根目录跑 `spring-boot:run`，父 POM 没有 main class。先安装模块，再用 fat jar，并换端口：

```bash
cd /path/to/zorth-ai-service
mvn -pl ai-server -am install -DskipTests

export AI_API_KEY='your-api-key'
java -jar ai-server/target/ai-server-0.0.1-SNAPSHOT.jar \
  --server.port=8081 \
  --ai.datasource.provider=web-sql \
  --ai.datasource.web-sql.base-url=http://127.0.0.1:8080 \
  --ai.datasource.web-sql.allowed-datasource-ids="${DATASOURCE_ID}"
```

`allowed-datasource-ids` 默认是空列表，fail closed。不配这个 ID，Tool 会返回 `DATASOURCE_NOT_ALLOWED`，不会发 HTTP。

## 4. 打一枪 Agent

```bash
curl -sS http://127.0.0.1:8081/api/v1/ai/agent \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"message\": \"这个数据库里有哪些表？只需要列出表名。\",
    \"datasourceId\": \"${DATASOURCE_ID}\",
    \"database\": \"${DATABASE}\",
    \"conversationId\": \"local-sql-connect-1\"
  }"
```

成功时 HTTP 200，`content` 里是表名。服务端日志应出现：

```text
Agent request started ... datasourceId=... database=...
Tool execution started ... toolName=listTables
Database tool audit ... toolName=listTables toolResultStatus=SUCCESS
```

只看模型回答不够，表名必须和上一步 SQL 接口返回的一致。

仓库里的冒烟脚本（假设三个服务已起来）：

```bash
export AI_API_KEY DATASOURCE_ID DATABASE
./scripts/smoke-database-agent.sh
```

## 踩过的坑

| 现象 | 原因 |
| --- | --- |
| 数据源详情 200，列出库 500 / `Communications link failure` | 目标 MySQL 没起来，或本机到该 IP 路由 `REJECT` |
| `DATASOURCE_NOT_ALLOWED` | AI 白名单仍是空的 |
| `MISSING_DATABASE` | 请求没带 `database` |
| `AUTH_ERROR` | 没带 `Authorization`，或假授权重启后 Token 失效 |
| 404 `DATA_SOURCE_NOT_FOUND` | Token 里的 `productId` 和数据源不属于同一产品 |
| 父工程 `Unable to find a suitable main class` | 在仓库根目录跑了 `spring-boot:run` |
| AI 起不来或占错端口 | 和 SQL service 都默认 8080 |

## 不写进文档的东西

- 真实 `AI_API_KEY`
- 数据源密码、密文、IV
- 把白名单写死进 `application.yml` 提交
