# MyBatis Mapper XML → Semantic JSON PoC 实施方案

## 1. 任务背景

当前项目已经具备 Spring AI 基座，并且已经能够通过 Tool 操作数据库。

下一步需要验证一个关键思路：

> 能否从现有 Java / MyBatis 项目的 Mapper XML 中，自动提取数据库相关语义，并生成结构化 JSON，供后续 Database Agent / Text-to-SQL Agent 使用。

本次变更只实现第一阶段 PoC：

```text
一批 mapper.xml
        ↓
扫描 XML
        ↓
逐个调用大模型
        ↓
Spring AI Structured Output
        ↓
MapperSemantic Java Object
        ↓
*.semantic.json
```

本次目标不是构建完整语义层平台，而是先验证：

1. 大模型是否能稳定识别 Mapper 中使用的表、字段、JOIN、WHERE、动态 SQL 等信息；
2. 是否能按照统一 Schema 输出结构化 JSON；
3. 是否可以批量处理一个真实 Java 项目中的 Mapper XML；
4. 输出结果是否具备后续合并为项目级语义模型的基础。

---

# 2. 本次变更范围

## 2.1 必须实现

实现一个 `Mapper Semantic Generator`，支持：

- 指定一个 Mapper XML 根目录；
- 递归扫描目录下所有 `*.xml`；
- 对每一个 XML 独立调用 LLM；
- 要求 LLM 按固定 Java Schema 返回结构化结果；
- 将结果保存成对应的 `*.semantic.json`；
- 保留原始 XML 文件路径作为 evidence；
- 单个 Mapper 失败不能中断整个批次；
- 输出成功、失败、跳过数量；
- 支持重复执行；
- 输出必须能够被 Jackson 再次反序列化；
- 提供至少一个可手工触发的执行入口；
- 提供基本单元测试和集成测试。

## 2.2 暂不实现

本次不要实现以下内容：

- 不实现向量数据库；
- 不实现 RAG；
- 不实现 Agent 查询语义层；
- 不实现数据库 Schema 自动读取；
- 不实现 Java Entity / DTO / Enum 分析；
- 不实现 Service 层分析；
- 不实现项目级复杂业务概念归并；
- 不实现 Web UI；
- 不实现复杂任务调度；
- 不引入新的微服务；
- 不大规模重构现有 Spring AI 基座。

本次只完成：

> Mapper XML → Mapper Semantic JSON

---

# 3. 开发原则

Codex 开始修改代码前，先检查当前仓库：

1. Spring Boot 版本；
2. Spring AI 版本；
3. 当前已有的 `ChatClient` / `ChatModel` 配置方式；
4. 当前包结构；
5. 当前配置文件风格；
6. 当前 Jackson 使用方式；
7. 是否已经存在统一异常处理、日志、DTO、Service 规范。

优先复用现有实现，不要创建第二套 AI 配置。

如果当前项目已经存在：

```java
ChatClient
```

则直接注入已有 `ChatClient`。

如果已有统一的 LLM Service，则优先扩展该 Service，而不是重复封装 Provider。

---

# 4. 推荐目录结构

根据当前项目真实 package 调整，不要机械照抄 package 名。

建议新增：

```text
semantic/
├── config/
│   └── MapperSemanticProperties.java
│
├── model/
│   ├── MapperSemantic.java
│   ├── MapperStatementSemantic.java
│   ├── TableRef.java
│   ├── ColumnRef.java
│   ├── RelationshipSemantic.java
│   ├── FilterSemantic.java
│   ├── DynamicFilterSemantic.java
│   ├── BusinessMeaning.java
│   └── SemanticEvidence.java
│
├── service/
│   ├── MapperSemanticGenerator.java
│   ├── MapperSemanticExtractor.java
│   └── MapperFileScanner.java
│
├── prompt/
│   └── MapperSemanticPromptBuilder.java
│
└── controller/
    └── MapperSemanticController.java
```

如果现有项目不适合 Controller，可以使用 `CommandLineRunner`、测试入口或现有管理接口。

核心要求是存在一个明确的可执行入口。

---

# 5. 配置

新增类似配置：

```yaml
semantic:
  mapper:
    enabled: true
    source-directory: /path/to/project/src/main/resources/mapper
    output-directory: ./semantic-output
    overwrite: true
    max-file-size: 200KB
```

对应：

```java
@ConfigurationProperties(prefix = "semantic.mapper")
public class MapperSemanticProperties {

    private boolean enabled;

    private Path sourceDirectory;

    private Path outputDirectory;

    private boolean overwrite = true;

    private DataSize maxFileSize = DataSize.ofKilobytes(200);
}
```

具体写法请服从当前项目 Spring Boot 版本。

---

# 6. Semantic JSON Schema

这是本次实现最重要的部分。

不要让 LLM 自由决定 JSON 字段。

必须先创建 Java 类型，然后通过 Spring AI Structured Output 映射为 Java Object。

---

## 6.1 MapperSemantic

建议定义：

```java
public record MapperSemantic(

        String mapperName,

        String namespace,

        String sourceFile,

        String summary,

        List<MapperStatementSemantic> statements

) {
}
```

字段说明：

### mapperName

Mapper 简单名称，例如：

```text
OrderMapper
```

### namespace

来自：

```xml
<mapper namespace="com.example.order.mapper.OrderMapper">
```

### sourceFile

原始 Mapper 相对路径，例如：

```text
mapper/order/OrderMapper.xml
```

不要只保存文件名。

### summary

对整个 Mapper 用途的简短描述，例如：

```text
订单查询与订单状态维护相关 Mapper
```

这是推断字段。

### statements

当前 Mapper 中所有：

```xml
<select>
<insert>
<update>
<delete>
```

的语义信息。

---

# 7. MapperStatementSemantic

建议定义：

```java
public record MapperStatementSemantic(

        String id,

        SqlOperation operation,

        String description,

        List<TableRef> tables,

        List<ColumnRef> columns,

        List<RelationshipSemantic> relationships,

        List<FilterSemantic> fixedFilters,

        List<DynamicFilterSemantic> dynamicFilters,

        List<String> groupBy,

        List<String> orderBy,

        List<BusinessMeaning> businessMeanings,

        List<SemanticEvidence> evidence

) {
}
```

---

# 8. SqlOperation

定义枚举：

```java
public enum SqlOperation {

    SELECT,
    INSERT,
    UPDATE,
    DELETE,
    UNKNOWN
}
```

LLM 无法判断时必须使用：

```text
UNKNOWN
```

禁止编造。

---

# 9. TableRef

```java
public record TableRef(

        String table,

        String alias,

        TableKind kind

) {
}
```

`kind` 取值为 `PHYSICAL`、`DERIVED`、`CTE` 或 `UNKNOWN`。物理表和 CTE 的 `table` 是可见名称；派生表的 `table` 必须为 `null`，`alias` 必须保留子查询的可见别名。未知关系至少保留一个源代码中可见的 `table` 或 `alias`，禁止发明 `derived_union` 一类描述性表名。

示例：

```json
{
  "table": "t_order",
  "alias": "o",
  "kind": "PHYSICAL"
}
```

如果没有 alias：

```json
{
  "table": "t_order",
  "alias": null,
  "kind": "PHYSICAL"
}
```

派生关系示例：

```json
{
  "table": null,
  "alias": "bn",
  "kind": "DERIVED"
}
```

---

# 10. ColumnRef

```java
public record ColumnRef(

        String table,

        String column,

        String alias,

        String usage

) {
}
```

`usage` 可以出现：

```text
SELECT
JOIN
FILTER
GROUP_BY
ORDER_BY
UPDATE
INSERT
UNKNOWN
```

如果 SQL 无法确定字段属于哪张表，则：

```text
table = null
```

不要猜测。

示例：

```json
{
  "table": "t_order",
  "column": "amount",
  "alias": null,
  "usage": "SELECT"
}
```

---

# 11. RelationshipSemantic

用于表达 JOIN 或明确的字段关系：

```java
public record RelationshipSemantic(

        String leftTable,

        String leftColumn,

        String rightTable,

        String rightColumn,

        String joinType,

        String expression,

        double confidence

) {
}
```

示例：

SQL：

```sql
LEFT JOIN t_user u
    ON o.user_id = u.id
```

生成：

```json
{
  "leftTable": "t_order",
  "leftColumn": "user_id",
  "rightTable": "t_user",
  "rightColumn": "id",
  "joinType": "LEFT_JOIN",
  "expression": "o.user_id = u.id",
  "confidence": 1.0
}
```

如果是从明确 SQL JOIN 中解析出来：

```text
confidence = 1.0
```

如果存在推断：

```text
confidence < 1.0
```

---

# 12. FilterSemantic

固定 SQL 条件：

```java
public record FilterSemantic(

        String expression,

        String table,

        String column,

        String operator,

        String value,

        double confidence

) {
}
```

例如：

```sql
WHERE o.status = '03'
```

必须至少生成：

```json
{
  "expression": "o.status = '03'",
  "table": "t_order",
  "column": "status",
  "operator": "=",
  "value": "03",
  "confidence": 1.0
}
```

`FilterSemantic` 只保存事实，不再保存推断字段。如果 Mapper 方法名、注释或命名片段支持非平凡业务推断，应只写入 `BusinessMeaning`，并提供独立的 `INFERENCE` evidence。普通 CRUD 转述应省略。

如果 Mapper 方法叫：

```text
queryCompletedOrders
```

模型在证据充分时可能推断：

```json
{
  "name": "已完成订单",
  "description": "该查询可能用于查询已完成状态的订单",
  "derivedFrom": "statement id=queryCompletedOrders and fixed filter status='03'",
  "confidence": 0.75
}
```

注意：

> 所有推断只允许出现在 `BusinessMeaning`；置信度达到 `0.9` 必须有注释或命名 SQL 片段等明确强证据。

绝对不能因为看到：

```text
status = 1
```

就无证据地输出：

```text
1 = 正常
```

---

# 13. DynamicFilterSemantic

用于 MyBatis 动态 SQL：

```java
public record DynamicFilterSemantic(

        String parameter,

        String expression,

        String table,

        String column,

        String operator,

        String condition,

        double confidence

) {
}
```

例如：

```xml
<if test="startTime != null">
    AND o.create_time <![CDATA[ >= ]]> #{startTime}
</if>
```

生成：

```json
{
  "parameter": "startTime",
  "expression": "o.create_time >= #{startTime}",
  "table": "t_order",
  "column": "create_time",
  "operator": ">=",
  "condition": "startTime != null",
  "confidence": 1.0
}
```

这里 `expression` 只能包含动态标签产生的 SQL 片段，`condition` 只能包含 `test` 中的 MyBatis/OGNL 条件；完整 `<if>`、`<when>` 或 `<foreach>` XML 只能保留在 `DYNAMIC_XML` evidence 中，不能放进这两个字段。

必须支持识别常见 MyBatis 标签：

```xml
<if>
<where>
<choose>
<when>
<otherwise>
<foreach>
<trim>
<set>
```

本次不要求程序真正解析所有动态 SQL。

可以由 LLM 阅读完整 XML 后提取。

---

# 14. BusinessMeaning

所有“业务语义推断”必须和确定事实分开。

定义：

```java
public record BusinessMeaning(

        String name,

        String description,

        String derivedFrom,

        double confidence

) {
}
```

例如：

```json
{
  "name": "已完成订单",
  "description": "该查询可能用于查询已完成状态的订单",
  "derivedFrom": "statement id=queryCompletedOrders and fixed filter status='03'",
  "confidence": 0.8
}
```

规定：

```text
confidence >= 0.9
```

表示存在强代码证据。

```text
0.7 <= confidence < 0.9
```

表示高概率推断。

```text
confidence < 0.7
```

原则上不要输出该 BusinessMeaning。

宁可少，也不要编造。

---

# 15. SemanticEvidence

所有 statement 至少包含一条 evidence。

```java
public record SemanticEvidence(

        String sourceFile,

        String statementId,

        String evidenceType,

        String evidence

) {
}
```

例如：

```json
{
  "sourceFile": "mapper/order/OrderMapper.xml",
  "statementId": "queryUserOrders",
  "evidenceType": "SQL",
  "evidence": "LEFT JOIN t_user u ON o.user_id = u.id"
}
```

`evidence` 不需要保存整个 XML。

保存关键证据即可。

---

# 16. 最终 JSON 示例

输入：

```xml
<mapper namespace="com.example.order.mapper.OrderMapper">

    <select id="queryUserOrders" resultType="OrderVO">
        SELECT
            o.order_no,
            o.amount,
            u.user_name
        FROM t_order o
        LEFT JOIN t_user u ON o.user_id = u.id
        WHERE o.status = '03'

        <if test="userId != null">
            AND u.id = #{userId}
        </if>

        <if test="startTime != null">
            AND o.create_time <![CDATA[ >= ]]> #{startTime}
        </if>

        ORDER BY o.create_time DESC
    </select>

</mapper>
```

期望输出类似：

```json
{
  "mapperName": "OrderMapper",
  "namespace": "com.example.order.mapper.OrderMapper",
  "sourceFile": "mapper/order/OrderMapper.xml",
  "summary": "订单查询相关 Mapper",
  "statements": [
    {
      "id": "queryUserOrders",
      "operation": "SELECT",
      "description": "查询指定用户的订单",
      "tables": [
        {
          "table": "t_order",
          "alias": "o",
          "kind": "PHYSICAL"
        },
        {
          "table": "t_user",
          "alias": "u",
          "kind": "PHYSICAL"
        }
      ],
      "columns": [
        {
          "table": "t_order",
          "column": "order_no",
          "alias": null,
          "usage": "SELECT"
        },
        {
          "table": "t_order",
          "column": "amount",
          "alias": null,
          "usage": "SELECT"
        },
        {
          "table": "t_user",
          "column": "user_name",
          "alias": null,
          "usage": "SELECT"
        }
      ],
      "relationships": [
        {
          "leftTable": "t_order",
          "leftColumn": "user_id",
          "rightTable": "t_user",
          "rightColumn": "id",
          "joinType": "LEFT_JOIN",
          "expression": "o.user_id = u.id",
          "confidence": 1.0
        }
      ],
      "fixedFilters": [
        {
          "expression": "o.status = '03'",
          "table": "t_order",
          "column": "status",
          "operator": "=",
          "value": "03",
          "confidence": 1.0
        }
      ],
      "dynamicFilters": [
        {
          "parameter": "userId",
          "expression": "u.id = #{userId}",
          "table": "t_user",
          "column": "id",
          "operator": "=",
          "condition": "userId != null",
          "confidence": 1.0
        },
        {
          "parameter": "startTime",
          "expression": "o.create_time >= #{startTime}",
          "table": "t_order",
          "column": "create_time",
          "operator": ">=",
          "condition": "startTime != null",
          "confidence": 1.0
        }
      ],
      "groupBy": [],
      "orderBy": [
        "t_order.create_time DESC"
      ],
      "businessMeanings": [
        {
          "name": "用户订单查询",
          "description": "根据用户和可选时间条件查询订单",
          "derivedFrom": "statement id=queryUserOrders",
          "confidence": 0.95
        }
      ],
      "evidence": [
        {
          "sourceFile": "mapper/order/OrderMapper.xml",
          "statementId": "queryUserOrders",
          "evidenceType": "SQL",
          "evidence": "LEFT JOIN t_user u ON o.user_id = u.id"
        }
      ]
    }
  ]
}
```

不要要求输出和示例逐字符一致。

只要求符合 Java Schema 和语义规则。

---

# 17. Prompt 设计

新增：

```text
MapperSemanticPromptBuilder
```

不要把 Prompt 散落在 Service 中。

建议 System Prompt：

```text
You are a semantic metadata extractor for Java MyBatis projects.

Your task is to extract database semantic metadata from MyBatis Mapper XML.

Rules:

1. Extract facts from the provided XML.
2. Do not invent database tables, columns, relationships, enum meanings, or business rules.
3. Distinguish facts from inferred business meanings.
4. Facts directly visible in SQL should normally have confidence 1.0.
5. Inferred meanings must include confidence.
6. Do not emit inferred business meanings below confidence 0.7.
7. If a table or column cannot be determined, use null instead of guessing.
8. Preserve important JOIN and WHERE expressions as evidence.
9. Analyze MyBatis dynamic SQL, including if, choose, when, foreach, where, trim, and set.
10. Return all SQL statements defined in the Mapper.
11. Keep descriptions concise.
12. The output must conform exactly to the requested structured schema.
```

User Prompt：

```text
Analyze the following MyBatis Mapper XML.

Source file:
%s

Mapper XML:
%s
```

参数：

```text
sourceFile
xmlContent
```

不要让用户输入影响 System Prompt。

---

# 18. Spring AI 调用方式

优先使用当前项目已有 `ChatClient`。

推荐逻辑：

```java
MapperSemantic result = chatClient.prompt()
        .system(SYSTEM_PROMPT)
        .user(buildUserPrompt(sourceFile, xmlContent))
        .call()
        .entity(MapperSemantic.class);
```

如果当前 Spring AI 版本支持 schema validation，可以使用：

```java
MapperSemantic result = chatClient.prompt()
        .system(SYSTEM_PROMPT)
        .user(buildUserPrompt(sourceFile, xmlContent))
        .call()
        .entity(
            MapperSemantic.class,
            spec -> spec.validateSchema()
        );
```

如果当前模型 Provider 支持 native structured output，并且当前 Spring AI 版本支持，可进一步使用：

```java
.entity(
    MapperSemantic.class,
    spec -> spec
        .useProviderStructuredOutput()
        .validateSchema()
);
```

但是：

> 不允许为了使用上述 API 强行升级当前项目 Spring AI 版本。

Codex 必须先读取 `pom.xml` / `build.gradle`，根据当前 Spring AI 版本选择兼容方式。

如果当前版本没有上述 `entity(..., spec)` API：

- 使用当前版本已有 `entity(Class<T>)`；
- 或使用 `BeanOutputConverter<MapperSemantic>`；
- 或采用当前项目已经存在的 Structured Output 实现。

最终必须保证输出可可靠反序列化成：

```java
MapperSemantic
```

不要直接：

```java
String response = chatClient.prompt(...).call().content();
Files.writeString(..., response);
```

然后相信模型一定返回正确 JSON。

必须经过 Java 类型校验。

---

# 19. MapperFileScanner

职责：

```text
扫描目录
↓
找到 XML
↓
过滤非法文件
↓
返回待处理文件列表
```

建议接口：

```java
public interface MapperFileScanner {

    List<Path> scan(Path sourceDirectory);

}
```

要求：

- 递归扫描；
- 只处理 `.xml`；
- 按路径稳定排序，保证重复执行顺序一致；
- 忽略隐藏文件；
- 对超过 `max-file-size` 的文件标记失败或跳过；
- 不跟随可能造成循环的 symbolic link；
- source directory 不存在时给出明确异常。

---

# 20. MapperSemanticExtractor

职责：

> 单文件 XML → MapperSemantic

建议：

```java
public interface MapperSemanticExtractor {

    MapperSemantic extract(
        Path sourceRoot,
        Path mapperFile
    );

}
```

实现过程：

```text
读取 UTF-8 XML
↓
计算 sourceFile 相对路径
↓
构造 Prompt
↓
调用 LLM
↓
Structured Output → MapperSemantic
↓
执行本地校验
↓
返回结果
```

---

# 21. 本地结果校验

LLM Structured Output 成功后仍然要执行 Java 侧校验。

至少检查：

```text
mapperName != blank
sourceFile != blank
statements != null
```

每个 statement：

```text
id != blank
operation != null
tables != null
columns != null
relationships != null
fixedFilters != null
dynamicFilters != null
businessMeanings != null
evidence != null
```

对于 List 字段：

> 优先输出空数组，不要输出 null。

如果使用 record 无法方便设置默认值，可以在生成后 normalize。

新增：

```text
MapperSemanticValidator
```

或在 service 中做清晰的私有校验方法。

---

# 22. MapperSemanticGenerator

这是批处理 orchestration service。

建议：

```java
public interface MapperSemanticGenerator {

    MapperSemanticGenerationReport generate();

}
```

或者：

```java
MapperSemanticGenerationReport generate(
    Path sourceDirectory,
    Path outputDirectory
);
```

处理流程：

```text
scan
↓
for mapperFile
    ↓
    extract
    ↓
    validate
    ↓
    serialize
    ↓
    write *.semantic.json
↓
report
```

单个文件异常：

```text
catch
↓
记录失败
↓
继续下一个
```

不要：

```text
第 37 个 Mapper 失败
↓
整个 500 Mapper 批次退出
```

---

# 23. 输出文件规则

假设输入：

```text
src/main/resources/mapper/order/OrderMapper.xml
```

输出：

```text
semantic-output/mapper/order/OrderMapper.semantic.json
```

必须保留相对目录。

原因是大型 Java 项目可能出现同名：

```text
module-a/mapper/UserMapper.xml
module-b/mapper/UserMapper.xml
```

不能全部直接写入同一个目录。

---

# 24. JSON 序列化

使用项目已有的 Jackson `ObjectMapper`。

建议 pretty print：

```java
objectMapper
    .writerWithDefaultPrettyPrinter()
    .writeValue(outputFile.toFile(), mapperSemantic);
```

编码：

```text
UTF-8
```

必须保证：

```text
写出的 JSON
↓
ObjectMapper.readValue(...)
↓
MapperSemantic
```

能够成功。

---

# 25. overwrite 规则

配置：

```yaml
semantic:
  mapper:
    overwrite: true
```

如果：

```text
overwrite = true
```

重新生成。

如果：

```text
overwrite = false
```

且目标文件已经存在：

```text
skip
```

并计入报告。

不要默默覆盖。

---

# 26. Generation Report

新增结果类型：

```java
public record MapperSemanticGenerationReport(

        int total,

        int success,

        int failed,

        int skipped,

        List<MapperSemanticGenerationFailure> failures

) {
}
```

失败：

```java
public record MapperSemanticGenerationFailure(

        String sourceFile,

        String errorType,

        String message

) {
}
```

不要把 API Key、完整 Prompt 或敏感配置放进错误信息。

---

# 27. 日志

批次开始：

```text
Start generating mapper semantic metadata.
sourceDirectory=...
outputDirectory=...
mapperCount=...
```

单文件成功：

```text
Generated semantic metadata for mapper/order/OrderMapper.xml
```

失败：

```text
Failed to generate semantic metadata for mapper/order/OrderMapper.xml
```

批次结束：

```text
Mapper semantic generation completed.
total=100 success=96 failed=3 skipped=1
```

不要默认在 INFO 日志打印整个 Mapper XML。

不要默认打印完整 LLM Prompt。

---

# 28. 手工触发入口

本次 PoC 需要一个简单入口。

如果当前项目是 Web 服务，可以新增：

```http
POST /api/v1/semantic/mappers/generate
```

建议返回：

```json
{
  "total": 100,
  "success": 96,
  "failed": 3,
  "skipped": 1,
  "failures": [
    {
      "sourceFile": "mapper/a/TestMapper.xml",
      "errorType": "STRUCTURED_OUTPUT_ERROR",
      "message": "..."
    }
  ]
}
```

不要把 Mapper XML 通过 HTTP Request Body 上传。

目录来自配置。

如果当前项目有更适合的管理接口规范，服从现有规范。

如果当前项目不是 Web 应用，可改成已有 CLI / Runner 方式。

---

# 29. 对 XML 的预处理

本次不需要自己写完整 MyBatis SQL Parser。

但是发送给 LLM 前可以做安全的轻量处理：

### 可以做

- UTF-8 读取；
- 去 BOM；
- 去掉明显无价值的超长空白；
- 保留 XML comments；
- 保留 CDATA；
- 保留 `<sql>`；
- 保留 `<include>`；
- 保留动态 SQL 标签。

### 不要做

不要自己用正则把 XML 转成 SQL。

例如不要：

```text
删除 <if>
删除 <choose>
展开失败的 include
```

这会丢失非常重要的语义。

第一版直接让 LLM 阅读完整 XML。

---

# 30. `<sql>` 与 `<include>` 的处理

Mapper 中可能存在：

```xml
<sql id="Base_Column_List">
    id, user_id, amount, status
</sql>
```

以及：

```xml
<include refid="Base_Column_List"/>
```

本次优先让 LLM 在整个单 Mapper 文件范围内自行关联。

System Prompt 中增加：

```text
Resolve local MyBatis <sql> fragments and <include> references when the referenced fragment exists in the same Mapper XML.
If an include refers to an external or unresolved fragment, do not invent its content.
```

跨 Mapper include 本次暂不展开。

如果无法解析：

在对应 statement description / evidence 中体现 unresolved include 即可。

---

# 31. 文件过大问题

如果单 Mapper XML 非常大：

第一版不做复杂 chunk RAG。

配置：

```yaml
max-file-size: 200KB
```

如果超过阈值：

先记录：

```text
FILE_TOO_LARGE
```

并跳过。

后续版本再实现：

```text
按 statement 拆分 XML
↓
分别提取
↓
Mapper 级合并
```

本次不要把范围扩大。

---

# 32. LLM 失败重试

如果项目已有统一 AI retry 机制，复用。

否则只针对可恢复异常做少量重试，例如：

```text
结构化输出解析失败
临时模型调用失败
```

建议最多：

```text
2 次额外重试
```

不要无限重试。

如果 Spring AI 当前版本的：

```java
validateSchema()
```

已经提供 schema validation + retry，则优先使用框架能力，不重复造一套。

---

# 33. 结果确定性

本任务主要是信息抽取，不需要创作。

如果当前模型配置允许单请求设置 temperature：

优先使用低 temperature：

```text
0 ~ 0.2
```

但：

> 不允许破坏现有全局模型配置。

如果修改全局 temperature 会影响已有聊天功能，则应该为 Semantic Extractor 创建基于同一 ChatModel 的专用 ChatClient / request options，而不是修改现有聊天行为。

具体 API 根据当前 Spring AI 版本实现。

---

# 34. 测试资源

在：

```text
src/test/resources/semantic/mapper/
```

至少创建以下测试 Mapper。

---

## Case 1：普通 SELECT

包含：

```sql
SELECT
FROM
WHERE
ORDER BY
```

验证：

- statement id；
- table；
- selected column；
- fixed filter；
- order by。

---

## Case 2：JOIN

包含：

```sql
LEFT JOIN
ON a.user_id = b.id
```

验证：

```text
relationship
```

---

## Case 3：动态 SQL

包含：

```xml
<if>
<where>
<choose>
<when>
```

验证：

```text
dynamicFilters
```

---

## Case 4：SQL Fragment

包含：

```xml
<sql>
<include>
```

验证同 Mapper include 能够被识别。

---

## Case 5：UPDATE

包含：

```xml
<update>
<set>
<if>
```

验证：

```text
operation = UPDATE
```

以及更新字段。

---

## Case 6：复杂 Mapper

一个 Mapper 同时存在：

```text
select
insert
update
delete
```

验证全部 statement 都存在。

---

# 35. 单元测试

至少为以下组件写单元测试：

```text
MapperFileScanner
MapperSemanticPromptBuilder
output path mapping
MapperSemantic validation
JSON serialization/deserialization
```

LLM 本身不要在普通单元测试中真实调用远端 Provider。

Extractor 应该允许 mock：

```text
ChatClient
```

或者进一步抽象：

```text
MapperSemanticAiClient
```

如果直接 mock `ChatClient` fluent chain 很麻烦，可以增加一个很薄的 adapter：

```java
public interface MapperSemanticAiClient {

    MapperSemantic extract(
        String systemPrompt,
        String userPrompt
    );
}
```

生产实现内部使用 Spring AI：

```text
SpringAiMapperSemanticAiClient
```

测试直接 mock adapter。

避免为了测试创建过度抽象。

---

# 36. 可选集成测试

可以增加一个：

```text
@Tag("llm-integration")
```

测试。

测试位于 `ai-server`，只有明确激活 `llm-integration` Maven profile，并配置 `AI_API_KEY`、`SEMANTIC_MAPPER_SOURCE`、`SEMANTIC_MAPPER_OUTPUT` 时才运行。它必须调用生产装配的 `MapperSemanticGenerator`、校验报告不变量，并回读 schema `1.1` artifact；缺少环境变量时应清晰跳过。

```bash
mvn -pl ai-server -am -Pllm-integration test
```

不能让：

```text
mvn test
```

默认调用收费 LLM API。

---

# 37. 失败类型

建议统一枚举：

```java
public enum MapperSemanticFailureType {

    READ_ERROR,
    FILE_TOO_LARGE,
    AI_CALL_ERROR,
    STRUCTURED_OUTPUT_ERROR,
    VALIDATION_ERROR,
    WRITE_ERROR,
    UNKNOWN
}
```

不是强制要求字段名称完全一致，但必须能够区分失败阶段。

---

# 38. 第一阶段的关键验收指标

本次 PoC 不追求“业务语义 100% 正确”。

先验证结构事实。

从真实项目随机抽取至少：

```text
10 个 Mapper
```

人工检查。

重点检查以下五项：

| 项目 | 目标 |
|---|---|
| Statement 识别 | 基本完整 |
| Table 识别 | 高准确 |
| JOIN 关系 | 高准确 |
| 固定 WHERE 条件 | 高准确 |
| 动态 SQL 条件 | 基本可用 |

`description`、`businessMeanings` 属于推断信息，可以存在误差。

事实性结构不能大量出现幻觉。

---

# 39. 禁止行为

Codex 实现过程中禁止：

### 1. 不要通过正则硬解析完整 SQL

可以做简单 XML 文件扫描，但不要在本次重造一个 SQL Parser。

### 2. 不要让 LLM 返回任意 Map

不要：

```java
Map<String, Object>
```

优先：

```java
MapperSemantic
```

### 3. 不要直接信任字符串 JSON

不要只使用：

```java
.content()
```

然后直接写文件。

必须经过：

```text
Structured Output
+
Java 类型
+
本地校验
```

### 4. 不要一次把整个项目所有 XML 塞进一个 Prompt

必须：

```text
一个 Mapper XML
=
一次独立提取任务
```

### 5. 不要因为一个文件失败终止全量处理

### 6. 不要修改已有 Database Tool 行为

### 7. 不要在本次实现 Semantic RAG

### 8. 不要凭空生成数据库业务规则

---

# 40. 本次输出物

完成后仓库应该至少新增：

```text
Semantic model Java classes
Mapper XML scanner
Prompt builder
Spring AI semantic extractor
Batch generator
Generation report
Configuration
Manual trigger
Tests
README / documentation
```

运行后应产生：

```text
semantic-output/
├── mapper/
│   ├── UserMapper.semantic.json
│   ├── OrderMapper.semantic.json
│   ├── ProductMapper.semantic.json
│   └── ...
```

---

# 41. 后续阶段预留

虽然本次不实现，但设计上不要阻断后续：

```text
*.semantic.json
        ↓
Semantic Merger
        ↓
Project Semantic Model
        ↓
Semantic Store
        ↓
searchSemanticContext Tool
        ↓
Database Agent
```

后续可能形成：

```text
semantic-output/
├── mapper/
│   └── *.semantic.json
│
├── table/
│   └── *.json
│
├── concept/
│   └── *.json
│
└── semantic-model.json
```

因此当前 JSON 必须：

- 字段语义清晰；
- 可反序列化；
- 有 evidence；
- 有 confidence；
- 不依赖具体 LLM Provider；
- 不把所有信息塞成自然语言 description。

---

# 42. Codex 执行顺序

请按照下面顺序执行，不要一开始大范围改代码。

## Step 1

检查项目：

```text
pom.xml / build.gradle
Spring Boot version
Spring AI version
现有 ChatClient 配置
已有 package 规范
现有 API 规范
```

输出简短实现判断，然后直接继续编码。

不需要等待人工确认。

---

## Step 2

创建 Semantic Model：

```text
MapperSemantic
MapperStatementSemantic
TableRef
ColumnRef
RelationshipSemantic
FilterSemantic
DynamicFilterSemantic
BusinessMeaning
SemanticEvidence
SqlOperation
```

确保 Jackson 可序列化 / 反序列化。

---

## Step 3

实现：

```text
MapperFileScanner
```

先写对应单元测试。

---

## Step 4

实现：

```text
MapperSemanticPromptBuilder
```

Prompt 放独立类或 resource。

不要散落字符串。

---

## Step 5

实现：

```text
MapperSemanticAiClient
```

使用项目已有 Spring AI。

要求：

```text
LLM
↓
Structured Output
↓
MapperSemantic
```

---

## Step 6

实现：

```text
MapperSemanticExtractor
```

完成：

```text
file
↓
read
↓
prompt
↓
AI
↓
validate
```

---

## Step 7

实现：

```text
MapperSemanticGenerator
```

完成批量任务：

```text
scan
extract
write
report
```

---

## Step 8

实现手工触发入口。

优先服从当前项目 API 风格。

---

## Step 9

增加测试 Mapper 和测试代码。

---

## Step 10

运行：

```bash
mvn test
```

如果项目使用 Maven Wrapper：

```bash
./mvnw test
```

如果是 Gradle：

```bash
./gradlew test
```

修复本次变更导致的所有失败。

---

## Step 11

如果本地环境存在可用 LLM 配置，再执行一次真实 PoC。

如果没有 API Key / 模型服务：

不要伪造结果。

保证：

```text
编译通过
单元测试通过
真实 LLM 集成入口已经完成
```

即可。

---

# 43. 完成后的 Codex 汇报格式

实现完成后，请输出：

## Changed

列出新增 / 修改的核心文件。

## Architecture

说明：

```text
Mapper XML
→ Scanner
→ Extractor
→ Spring AI
→ Structured Output
→ JSON
```

## How to Run

给出真实命令和配置示例。

## Output Example

给出一个真实生成文件路径。

## Tests

列出执行过的测试命令和结果。

## Limitations

明确本阶段：

```text
只分析 Mapper XML
尚未结合 DB Schema
尚未结合 Java Enum / Entity / Service
尚未实现项目级 Semantic Merger
```

---

# 44. Definition of Done

只有满足以下条件才算完成：

- [ ] 可以配置 Mapper 根目录；
- [ ] 可以递归扫描 `*.xml`；
- [ ] 每个 XML 独立调用 LLM；
- [ ] LLM 输出映射成固定 `MapperSemantic` Java 类型；
- [ ] 不依赖任意结构 `Map<String, Object>`；
- [ ] 生成对应的 `*.semantic.json`；
- [ ] 输出路径保留输入相对目录；
- [ ] JSON 可被 Jackson 成功反序列化回 Java 类型；
- [ ] 能提取 table；
- [ ] 能提取 column；
- [ ] 能提取 JOIN relationship；
- [ ] 能提取 fixed WHERE filter；
- [ ] 能提取常见 MyBatis dynamic filter；
- [ ] 推断信息具有 confidence；
- [ ] 重要信息具有 evidence；
- [ ] 单文件失败不影响其他 Mapper；
- [ ] 有批量 generation report；
- [ ] 默认单元测试不调用收费 LLM；
- [ ] 项目可以正常编译；
- [ ] 原有 AI / Database Tool 功能没有被破坏。

---

# 45. 最终目标说明

本阶段不是为了直接做出完整“语义层”。

本阶段的目标是建立稳定的：

```text
Repository Artifact
        ↓
Semantic Extraction
        ↓
Structured Semantic Metadata
```

其中第一种 Repository Artifact 就是：

```text
MyBatis Mapper XML
```

如果这个 PoC 验证成功，下一阶段再增加：

```text
Database Schema
+
Java Entity
+
Enum
+
DTO
+
Service
```

最终形成：

```text
Java Repository
        +
Database
        ↓
Semantic Builder
        ↓
Project Semantic Model
        ↓
Database Agent
```

因此，本次实现的首要原则是：

> **事实优先、结构化优先、可追溯优先，宁可少提取，也不要让 LLM 编造数据库语义。**
