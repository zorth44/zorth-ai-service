package com.zorth.aiplatform.datasource.service;

import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Fetch;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.Top;

public final class SqlLimitApplier {

    public enum Dialect {
        LIMIT,
        SQL_SERVER,
        ORACLE
    }

    public record AppliedSql(String sql, boolean addedLimit) {
    }

    public AppliedSql apply(String sql, int maxRows, Dialect dialect) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select select)) {
                return new AppliedSql(sql, false);
            }
            if (hasRowCap(select)) {
                return new AppliedSql(sql, false);
            }
            cap(select, maxRows, dialect);
            return new AppliedSql(select.toString(), true);
        }
        catch (Exception exception) {
            return new AppliedSql(sql, false);
        }
    }

    public static Dialect detect(String jdbcUrl) {
        if (jdbcUrl == null) {
            return Dialect.LIMIT;
        }
        String normalized = jdbcUrl.toLowerCase();
        if (normalized.contains("sqlserver") || normalized.contains("jtds")) {
            return Dialect.SQL_SERVER;
        }
        if (normalized.contains("oracle")) {
            return Dialect.ORACLE;
        }
        return Dialect.LIMIT;
    }

    private static boolean hasRowCap(Select select) {
        if (select.getLimit() != null || select.getFetch() != null) {
            return true;
        }
        try {
            PlainSelect plainSelect = select.getPlainSelect();
            return plainSelect != null && plainSelect.getTop() != null;
        }
        catch (ClassCastException ignored) {
            return false;
        }
    }

    private static void cap(Select select, int maxRows, Dialect dialect) {
        switch (dialect) {
            case SQL_SERVER -> {
                try {
                    PlainSelect plainSelect = select.getPlainSelect();
                    if (plainSelect != null) {
                        Top top = new Top();
                        top.setExpression(new LongValue(maxRows));
                        plainSelect.setTop(top);
                        return;
                    }
                }
                catch (ClassCastException ignored) {
                    // Fall through to LIMIT for UNION-style statements.
                }
                select.setLimit(limit(maxRows));
            }
            case ORACLE -> {
                Fetch fetch = new Fetch();
                fetch.setRowCount(maxRows);
                select.setFetch(fetch);
            }
            case LIMIT -> select.setLimit(limit(maxRows));
        }
    }

    private static Limit limit(int maxRows) {
        Limit limit = new Limit();
        limit.setRowCount(new LongValue(maxRows));
        return limit;
    }
}
