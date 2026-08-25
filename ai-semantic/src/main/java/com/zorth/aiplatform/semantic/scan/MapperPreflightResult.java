package com.zorth.aiplatform.semantic.scan;

import com.zorth.aiplatform.semantic.model.SqlOperation;
import java.util.List;

public record MapperPreflightResult(String namespace, List<MapperStatementRef> statements) {

    public MapperPreflightResult {
        statements = statements == null ? List.of() : List.copyOf(statements);
    }

    public record MapperStatementRef(String id, SqlOperation operation) {
    }
}
