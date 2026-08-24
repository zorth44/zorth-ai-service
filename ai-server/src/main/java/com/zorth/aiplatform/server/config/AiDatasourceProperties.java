package com.zorth.aiplatform.server.config;

import com.zorth.aiplatform.datasource.config.DatasourceConnectionProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public class AiDatasourceProperties {

    private Map<String, DatasourceConnectionProperties> datasources = new LinkedHashMap<>();

    public Map<String, DatasourceConnectionProperties> getDatasources() {
        return datasources;
    }

    public void setDatasources(Map<String, DatasourceConnectionProperties> datasources) {
        this.datasources = datasources == null ? new LinkedHashMap<>() : datasources;
    }
}
