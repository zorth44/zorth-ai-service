package com.zorth.aiplatform.datasource.service;

import com.zorth.aiplatform.datasource.config.ValidationLimits;
import com.zorth.aiplatform.datasource.exception.DatasourceException;
import com.zorth.aiplatform.datasource.model.SqlCheckResult;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.view.CreateView;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.grant.Grant;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;

public final class SqlValidationService {

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE", "CREATE",
            "REPLACE", "MERGE", "GRANT", "REVOKE");

    private final ValidationLimits limits;

    public SqlValidationService(ValidationLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
    }

    public SqlCheckResult check(String sql) {
        if (sql == null || sql.isBlank()) {
            return SqlCheckResult.rejected("SQL_VALIDATION_ERROR", "SQL must not be empty");
        }

        String normalized = sql.trim();
        if (normalized.length() > limits.maxSqlLength()) {
            return SqlCheckResult.rejected(
                    "SQL_VALIDATION_ERROR",
                    "SQL exceeds the maximum allowed length of " + limits.maxSqlLength());
        }

        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(normalized);
        }
        catch (Exception exception) {
            return SqlCheckResult.rejected("SQL_PARSE_ERROR", "SQL could not be parsed");
        }

        if (statements == null || statements.getStatements() == null
                || statements.getStatements().isEmpty()) {
            return SqlCheckResult.rejected("SQL_PARSE_ERROR", "SQL could not be parsed");
        }
        if (statements.getStatements().size() != 1) {
            return SqlCheckResult.rejected(
                    "SQL_VALIDATION_ERROR", "Multiple SQL statements are not allowed");
        }

        Statement statement = statements.getStatements().get(0);
        if (isForbidden(statement)) {
            return SqlCheckResult.rejected(
                    "SQL_VALIDATION_ERROR",
                    "Only read-only SELECT queries are allowed");
        }
        if (!(statement instanceof Select select)) {
            return SqlCheckResult.rejected(
                    "SQL_VALIDATION_ERROR",
                    "Only read-only SELECT queries are allowed");
        }
        if (writesRows(select)) {
            return SqlCheckResult.rejected(
                    "SQL_VALIDATION_ERROR",
                    "SELECT INTO and other write queries are not allowed");
        }

        int complexity = countTables(select);
        if (complexity > limits.maxComplexity()) {
            return SqlCheckResult.rejected(
                    "SQL_VALIDATION_ERROR",
                    "SQL exceeds the maximum allowed complexity of " + limits.maxComplexity());
        }
        return SqlCheckResult.ok();
    }

    public void requireValid(String sql) {
        SqlCheckResult result = check(sql);
        if (!result.valid()) {
            throw new DatasourceException(result.errorType(), result.message());
        }
    }

    private static boolean isForbidden(Statement statement) {
        if (statement instanceof Insert
                || statement instanceof Update
                || statement instanceof Delete
                || statement instanceof Drop
                || statement instanceof Alter
                || statement instanceof Truncate
                || statement instanceof CreateTable
                || statement instanceof CreateView
                || statement instanceof Merge
                || statement instanceof Grant) {
            return true;
        }
        String type = statement.getClass().getSimpleName().toUpperCase(Locale.ROOT);
        return FORBIDDEN_KEYWORDS.stream().anyMatch(type::contains);
    }

    private static boolean writesRows(Select select) {
        try {
            PlainSelect plainSelect = select.getPlainSelect();
            if (plainSelect == null) {
                return false;
            }
            List<?> intoTables = plainSelect.getIntoTables();
            return (intoTables != null && !intoTables.isEmpty())
                    || plainSelect.getIntoTempTable() != null;
        }
        catch (ClassCastException ignored) {
            return false;
        }
    }

    private static int countTables(Select select) {
        if (select instanceof ParenthesedSelect parenthesedSelect) {
            return countTables(parenthesedSelect.getSelect());
        }
        if (select instanceof SetOperationList setOperationList) {
            int total = 0;
            if (setOperationList.getSelects() != null) {
                for (Select child : setOperationList.getSelects()) {
                    total += countTables(child);
                }
            }
            return total;
        }
        try {
            PlainSelect plainSelect = select.getPlainSelect();
            if (plainSelect == null) {
                return 1;
            }
            int total = countFromItem(plainSelect.getFromItem());
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    total += countFromItem(join.getFromItem());
                }
            }
            return Math.max(total, 1);
        }
        catch (ClassCastException ignored) {
            return 1;
        }
    }

    private static int countFromItem(FromItem fromItem) {
        if (fromItem instanceof ParenthesedSelect parenthesedSelect) {
            return countTables(parenthesedSelect.getSelect());
        }
        return fromItem == null ? 0 : 1;
    }
}
