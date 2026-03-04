package com.chatbi.common;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL 安全校验，只允许 SELECT 查询
 */
public final class SqlSanitizer {

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE",
            "CREATE", "GRANT", "REVOKE", "EXEC", "EXECUTE", "CALL",
            "INTO", "COPY"
    );

    private static final Pattern COMMENT_PATTERN = Pattern.compile(
            "/\\*.*?\\*/|--[^\\n]*", Pattern.DOTALL);

    private SqlSanitizer() {
    }

    /**
     * 校验 SQL 是否安全（仅允许 SELECT / WITH）
     *
     * @return 错误信息，null 表示安全
     */
    public static String validate(String sql) {
        if (sql == null || sql.isBlank()) {
            return "SQL 不能为空";
        }

        // 去除注释
        String cleaned = COMMENT_PATTERN.matcher(sql).replaceAll(" ").trim();
        // 禁止多语句
        String noTrailing = cleaned.replaceAll(";+$", "").trim();
        if (noTrailing.contains(";")) {
            return "不支持多语句 SQL";
        }
        String upper = noTrailing.toUpperCase();

        // 必须以 SELECT 或 WITH 开头
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            return "仅允许 SELECT 查询语句";
        }

        // 检查是否包含禁止关键字（作为独立单词）
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (Pattern.compile("\\b" + keyword + "\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(noTrailing).find()) {
                return "SQL 包含禁止操作: " + keyword;
            }
        }

        return null;
    }
}
