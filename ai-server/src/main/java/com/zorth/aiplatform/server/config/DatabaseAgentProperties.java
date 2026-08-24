package com.zorth.aiplatform.server.config;

import com.zorth.aiplatform.datasource.config.QueryLimits;
import com.zorth.aiplatform.datasource.config.ValidationLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ai.agent.database")
public record DatabaseAgentProperties(
        @DefaultValue("200") int maxRows,
        @DefaultValue("10") int queryTimeoutSeconds,
        @DefaultValue("1048576") int maxResultBytes,
        @DefaultValue("10000") int maxSqlLength,
        @DefaultValue("12") int maxComplexity,
        @DefaultValue("false") boolean includeViews) {

    public QueryLimits queryLimits() {
        return new QueryLimits(maxRows, queryTimeoutSeconds, maxResultBytes);
    }

    public ValidationLimits validationLimits() {
        return new ValidationLimits(maxSqlLength, maxComplexity);
    }
}
