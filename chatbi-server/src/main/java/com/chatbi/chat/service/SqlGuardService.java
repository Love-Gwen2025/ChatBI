package com.chatbi.chat.service;

import com.chatbi.common.constants.AppConstants;
import com.chatbi.schema.service.SchemaService;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SqlGuardService {

    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
            " insert ", " update ", " delete ", " drop ", " alter ", " truncate ",
            " create ", " grant ", " revoke ", " comment ", " copy ", " merge ", " call "
    );

    private final SchemaService schemaService;

    public GuardedSql guard(String rawSql, Long projectId) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        String normalizedSql = normalizeSql(rawSql);
        assertSingleStatement(normalizedSql);
        assertReadOnly(normalizedSql);

        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(normalizedSql);
        } catch (Exception e) {
            throw new IllegalArgumentException("SQL 解析失败: " + e.getMessage(), e);
        }
        if (!(statement instanceof Select)) {
            throw new IllegalArgumentException("仅允许执行 SELECT 查询");
        }

        Set<String> referencedTables = new LinkedHashSet<>(new TablesNamesFinder().getTableList(statement));
        if (projectId != null && !referencedTables.isEmpty()) {
            Set<String> allowedTables = schemaService.listProjectTableNames(projectId);
            for (String tableName : referencedTables) {
                if (tableName != null && !allowedTables.contains(tableName.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("SQL 引用了当前项目未注册的表: " + tableName);
                }
            }
        }

        String boundedSql = "SELECT * FROM (" + normalizedSql + ") chatbi_guard LIMIT " + (AppConstants.MAX_SQL_ROWS + 1);
        return GuardedSql.builder()
                .originalSql(rawSql)
                .boundedSql(boundedSql)
                .referencedTables(referencedTables)
                .build();
    }

    private String normalizeSql(String sql) {
        String normalized = sql.trim();
        while (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private void assertSingleStatement(String sql) {
        if (sql.indexOf(';') >= 0) {
            throw new IllegalArgumentException("仅允许执行单条 SQL");
        }
    }

    private void assertReadOnly(String sql) {
        String lower = (" " + sql.toLowerCase(Locale.ROOT).replace('\n', ' ').replace('\r', ' ') + " ");
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (lower.contains(keyword)) {
                throw new IllegalArgumentException("检测到非只读 SQL 关键字: " + keyword.trim());
            }
        }
    }

    @Value
    @Builder
    public static class GuardedSql {
        String originalSql;
        String boundedSql;
        Set<String> referencedTables;
    }
}
