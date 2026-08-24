package com.zorth.aiplatform.server.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.datasource")
public class DatasourceProviderProperties {

    private String provider = "web-sql";
    private WebSql webSql = new WebSql();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public WebSql getWebSql() {
        return webSql;
    }

    public void setWebSql(WebSql webSql) {
        this.webSql = webSql == null ? new WebSql() : webSql;
    }

    public boolean jdbc() {
        return "jdbc".equalsIgnoreCase(provider);
    }

    public static class WebSql {

        private String baseUrl = "http://localhost:8080";
        private int connectTimeoutSeconds = 5;
        private int readTimeoutSeconds = 20;
        private int maxTablesPerSchemaCall = 5;
        private int maxListedTables = 200;
        private List<String> allowedDatasourceIds = new ArrayList<>();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getReadTimeoutSeconds() {
            return readTimeoutSeconds;
        }

        public void setReadTimeoutSeconds(int readTimeoutSeconds) {
            this.readTimeoutSeconds = readTimeoutSeconds;
        }

        public int getMaxTablesPerSchemaCall() {
            return maxTablesPerSchemaCall;
        }

        public void setMaxTablesPerSchemaCall(int maxTablesPerSchemaCall) {
            this.maxTablesPerSchemaCall = maxTablesPerSchemaCall;
        }

        public int getMaxListedTables() {
            return maxListedTables;
        }

        public void setMaxListedTables(int maxListedTables) {
            this.maxListedTables = maxListedTables;
        }

        public List<String> getAllowedDatasourceIds() {
            return allowedDatasourceIds;
        }

        public void setAllowedDatasourceIds(List<String> allowedDatasourceIds) {
            this.allowedDatasourceIds = allowedDatasourceIds == null
                    ? new ArrayList<>()
                    : allowedDatasourceIds;
        }
    }
}
