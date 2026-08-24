package com.zorth.aiplatform.datasource.port;

import com.zorth.aiplatform.datasource.model.QueryResult;

public interface QueryExecutionPort {

    QueryResult execute(DatasourceCall call, String sql);
}
