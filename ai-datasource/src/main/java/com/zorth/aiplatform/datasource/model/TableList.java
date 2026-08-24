package com.zorth.aiplatform.datasource.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TableList(List<String> tables, boolean truncated, String message) {

    public TableList {
        tables = tables == null ? List.of() : List.copyOf(tables);
    }

    public static TableList complete(List<String> tables) {
        return new TableList(tables, false, null);
    }

    public static TableList truncated(List<String> tables, int limit) {
        return new TableList(
                tables,
                true,
                "Only the first " + limit + " tables are listed");
    }
}
