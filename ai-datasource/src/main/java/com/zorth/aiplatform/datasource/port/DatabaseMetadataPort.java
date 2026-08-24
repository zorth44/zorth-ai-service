package com.zorth.aiplatform.datasource.port;

import com.zorth.aiplatform.datasource.model.TableList;
import com.zorth.aiplatform.datasource.model.TableSchema;
import java.util.List;

public interface DatabaseMetadataPort {

    TableList listTables(DatasourceCall call);

    List<TableSchema> getTableSchemas(DatasourceCall call, List<String> tableNames);
}
