package com.zorth.aiplatform.semantic.support;

import com.zorth.aiplatform.semantic.model.BusinessMeaning;
import com.zorth.aiplatform.semantic.model.ColumnRef;
import com.zorth.aiplatform.semantic.model.ColumnUsage;
import com.zorth.aiplatform.semantic.model.DynamicFilterSemantic;
import com.zorth.aiplatform.semantic.model.EvidenceType;
import com.zorth.aiplatform.semantic.model.FilterSemantic;
import com.zorth.aiplatform.semantic.model.JoinType;
import com.zorth.aiplatform.semantic.model.MapperSemantic;
import com.zorth.aiplatform.semantic.model.MapperStatementSemantic;
import com.zorth.aiplatform.semantic.model.RelationshipSemantic;
import com.zorth.aiplatform.semantic.model.SemanticEvidence;
import com.zorth.aiplatform.semantic.model.SqlOperation;
import com.zorth.aiplatform.semantic.model.TableRef;
import com.zorth.aiplatform.semantic.model.TableKind;
import com.zorth.aiplatform.semantic.scan.MapperPreflightResult;
import java.util.List;

public final class MapperSemanticFixtures {

    private MapperSemanticFixtures() {
    }

    public static MapperSemantic singleSelect(
            String sourceFile, String sourceHash, String mapperName, String namespace, String statementId) {
        return new MapperSemantic(
                MapperSemantic.SCHEMA_VERSION,
                sourceHash,
                mapperName,
                namespace,
                sourceFile,
                "Test mapper",
                List.of(selectStatement(sourceFile, statementId)));
    }

    public static MapperStatementSemantic selectStatement(String sourceFile, String statementId) {
        return new MapperStatementSemantic(
                statementId,
                SqlOperation.SELECT,
                "Select one order",
                List.of(new TableRef("t_order", "o", TableKind.PHYSICAL)),
                List.of(new ColumnRef("t_order", "id", null, ColumnUsage.SELECT)),
                List.of(),
                List.of(new FilterSemantic("o.id = #{id}", "t_order", "id", "=", "#{id}", 1.0d)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new SemanticEvidence(sourceFile, statementId, EvidenceType.SQL, "FROM t_order o")));
    }

    public static MapperStatementSemantic statement(
            String sourceFile, String statementId, SqlOperation operation) {
        return new MapperStatementSemantic(
                statementId,
                operation,
                operation.name() + " statement",
                List.of(new TableRef("t_order", null, TableKind.PHYSICAL)),
                List.of(new ColumnRef("t_order", "id", null, ColumnUsage.UNKNOWN)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new SemanticEvidence(sourceFile, statementId, EvidenceType.SQL, operation.name())));
    }

    public static MapperPreflightResult preflight(String namespace, String id, SqlOperation operation) {
        return new MapperPreflightResult(
                namespace, List.of(new MapperPreflightResult.MapperStatementRef(id, operation)));
    }

    public static RelationshipSemantic leftJoin() {
        return new RelationshipSemantic(
                "t_order", "user_id", "t_user", "id", JoinType.LEFT_JOIN, "o.user_id = u.id", 1.0d);
    }

    public static DynamicFilterSemantic startTimeFilter() {
        return new DynamicFilterSemantic(
                "startTime",
                "o.create_time >= #{startTime}",
                "t_order",
                "create_time",
                ">=",
                "startTime != null",
                1.0d);
    }

    public static BusinessMeaning completedOrders() {
        return new BusinessMeaning(
                "已完成订单",
                "该查询可能用于查询已完成状态的订单",
                "statement id=queryCompletedOrders and fixed filter status='03'",
                0.8d);
    }
}
