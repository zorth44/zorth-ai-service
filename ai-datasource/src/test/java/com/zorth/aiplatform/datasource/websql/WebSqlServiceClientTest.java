package com.zorth.aiplatform.datasource.websql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.zorth.aiplatform.datasource.exception.DatasourceException;
import com.zorth.aiplatform.datasource.port.DatasourceCall;
import com.zorth.aiplatform.datasource.websql.api.WebSqlExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WebSqlServiceClientTest {

    private MockRestServiceServer server;
    private WebSqlServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WebSqlServiceClient(builder.baseUrl("http://web-sql.test").build());
    }

    @Test
    void executeSendsIdentityHeaders() {
        server.expect(once(), requestTo("http://web-sql.test/api/v1/sql/executions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret-token"))
                .andExpect(header("X-Request-Id", "req-1"))
                .andExpect(content().string(containsString("\"readOnly\":true")))
                .andExpect(content().string(containsString("\"source\":\"AI_AGENT\"")))
                .andExpect(content().string(containsString("\"timeoutSeconds\":10")))
                .andExpect(content().string(containsString("\"rowLimit\":200")))
                .andRespond(withSuccess("""
                        {
                          "executionId": "exec-1",
                          "kind": "RESULT_SET",
                          "columns": [{"name":"id","label":"id","jdbcType":"BIGINT","typeName":"BIGINT"}],
                          "rows": [["1"]],
                          "rowCount": 1,
                          "truncated": false
                        }
                        """, MediaType.APPLICATION_JSON));

        client.execute(
                new WebSqlExecutionRequest(
                        "exec-1", "ds-1", "orders", "SELECT id FROM users", 200, true, 10, "AI_AGENT"),
                call());
        server.verify();
    }

    @Test
    void mapsUnknownColumn() {
        expectError(HttpStatus.UNPROCESSABLE_ENTITY, "SQL_EXECUTION_FAILED",
                "Unknown column 'order_time'", """
                        {"executionId":"exec-1","sqlState":"42S22","vendorErrorCode":1054}""");
        DatasourceException exception = assertThrows(DatasourceException.class,
                () -> client.execute(request(), call()));
        assertEquals("SQL_EXECUTION_ERROR", exception.errorType());
        assertTrue(exception.getMessage().contains("order_time"));
        assertTrue(exception.getMessage().contains("42S22"));
        server.verify();
    }

    @Test
    void mapsReadOnlyViolation() {
        expectError(HttpStatus.UNPROCESSABLE_ENTITY, "READ_ONLY_VIOLATION", "只读模式只允许查询语句", null);
        assertEquals("SQL_VALIDATION_ERROR", thrownType());
    }

    @Test
    void mapsTimeout() {
        expectError(HttpStatus.GATEWAY_TIMEOUT, "SQL_EXECUTION_TIMEOUT", "SQL 执行超时", null);
        assertEquals("SQL_TIMEOUT", thrownType());
    }

    @Test
    void mapsRateLimit() {
        expectError(HttpStatus.TOO_MANY_REQUESTS, "EXECUTION_LIMIT_EXCEEDED", "并发超限", null);
        assertEquals("RATE_LIMITED", thrownType());
    }

    private void expectError(HttpStatus status, String code, String message, String details) {
        String body = details == null
                ? """
                {"requestId":"r1","code":"%s","message":"%s"}
                """.formatted(code, message)
                : """
                {"requestId":"r1","code":"%s","message":"%s","details":%s}
                """.formatted(code, message, details);
        server.expect(once(), requestTo("http://web-sql.test/api/v1/sql/executions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private String thrownType() {
        DatasourceException exception = assertThrows(DatasourceException.class,
                () -> client.execute(request(), call()));
        server.verify();
        return exception.errorType();
    }

    private static WebSqlExecutionRequest request() {
        return new WebSqlExecutionRequest(
                "exec-1", "ds-1", "orders", "SELECT 1", 200, true, 10, "AI_AGENT");
    }

    private static DatasourceCall call() {
        return new DatasourceCall("ds-1", "orders", "Bearer secret-token", "req-1", "exec-1");
    }
}
