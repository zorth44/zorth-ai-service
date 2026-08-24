package com.zorth.aiplatform.datasource.websql;

import com.zorth.aiplatform.datasource.exception.DatasourceException;
import com.zorth.aiplatform.datasource.port.DatasourceCall;

final class WebSqlGuards {

    private WebSqlGuards() {
    }

    static void requireReady(DatasourceCall call, WebSqlSettings settings) {
        if (call == null || call.datasourceId() == null || call.datasourceId().isBlank()) {
            throw new DatasourceException(
                    "MISSING_DATASOURCE",
                    "datasourceId is not available in the current agent context");
        }
        if (!settings.allows(call.datasourceId())) {
            throw new DatasourceException(
                    "DATASOURCE_NOT_ALLOWED",
                    "Datasource '" + call.datasourceId() + "' is not allowed for the database agent");
        }
        if (call.database() == null || call.database().isBlank()) {
            throw new DatasourceException(
                    "MISSING_DATABASE",
                    "database is not available in the current agent context");
        }
        if (call.authorization() == null || call.authorization().isBlank()) {
            throw new DatasourceException(
                    "AUTH_ERROR",
                    "Authorization is required to query this datasource");
        }
    }
}
