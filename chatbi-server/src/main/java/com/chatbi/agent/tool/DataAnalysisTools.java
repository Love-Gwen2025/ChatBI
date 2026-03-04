package com.chatbi.agent.tool;

import com.chatbi.common.SqlSanitizer;
import com.chatbi.schema.service.SchemaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataAnalysisTools {

    private final SchemaService schemaService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_ROWS = 100;
    private static final int MAX_SCHEMA_SEARCH = 3;
    private static final int QUERY_TIMEOUT_SECONDS = 5;

    private static final Set<String> FORBIDDEN_TABLES = Set.of(
            "sys_user", "project", "user_project",
            "table_meta", "column_meta", "conversation"
    );
    private static final Pattern TABLE_WORD_PATTERN = Pattern.compile(
            "\\b(" + String.join("|", FORBIDDEN_TABLES) + ")\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Tool("根据用户问题关键词搜索相关的数据库表结构，返回表名、字段名、字段类型和注释。" +
            "在生成 SQL 之前必须先调用此工具了解可用的表和字段。")
    public String schemaSearch(@P("与用户问题相关的搜索关键词") String query, InvocationParameters params) {
        log.info("[Tool] schemaSearch: query={}", query);

        // 硬限制：同一次对话调用中最多搜索 MAX_SCHEMA_SEARCH 次
        Integer count = params.get("_schemaSearchCount");
        if (count == null) {
            count = 0;
        }
        count++;
        params.put("_schemaSearchCount", count);
        if (count > MAX_SCHEMA_SEARCH) {
            log.warn("[Tool] schemaSearch 已达上限 {} 次，拒绝继续搜索", MAX_SCHEMA_SEARCH);
            return "已达到搜索次数上限，当前项目中没有匹配的数据表。请确认项目是否已导入表结构。";
        }

        Long projectId = params.get("projectId");
        if (projectId == null) {
            return "未指定项目，无法搜索表结构。";
        }

        String result = schemaService.searchAndBuildSchemaText(query, 5, projectId);
        log.debug("[Tool] schemaSearch result:\n{}", result);

        return result;
    }

    @Tool("执行 SQL SELECT 查询并返回 JSON 格式结果。仅支持 SELECT 语句，最多返回 100 行。" +
            "如果执行失败会返回错误信息，请根据错误修正 SQL 后重试。")
    public String executeSql(@P("要执行的 PostgreSQL SELECT 语句") String sql, InvocationParameters params) {
        log.info("[Tool] executeSql: {}", sql);

        Long projectId = params != null ? params.get("projectId") : null;
        if (projectId == null || projectId <= 0) {
            return "未选择项目，拒绝执行 SQL。";
        }

        String error = SqlSanitizer.validate(sql);
        if (error != null) {
            return "SQL 校验失败: " + error;
        }

        // 保护内部表，避免意外暴露账号/项目等敏感数据
        if (TABLE_WORD_PATTERN.matcher(sql).find()) {
            return "SQL 校验失败: 禁止查询内部系统表";
        }

        try {
            String safeSql = enforceHardLimit(sql, MAX_ROWS);
            List<Map<String, Object>> rows = jdbcTemplate.query(con -> {
                var ps = con.prepareStatement(safeSql);
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                return ps;
            }, new ColumnMapRowMapper());
            String json = objectMapper.writeValueAsString(rows);
            log.info("[Tool] executeSql 返回 {} 行", rows.size());
            return json;
        } catch (JsonProcessingException e) {
            return "结果序列化失败: " + e.getMessage();
        } catch (Exception e) {
            String msg = "SQL 执行失败: " + e.getMessage();
            log.warn("[Tool] {}", msg);
            return msg;
        }
    }

    private String enforceHardLimit(String sql, int maxRows) {
        String cleaned = sql.trim().replaceAll(";+$", "").trim();
        // 强制包一层，避免用户写了超大 LIMIT 或者无 LIMIT 导致资源消耗
        return "SELECT * FROM (" + cleaned + ") AS chatbi_q LIMIT " + maxRows;
    }
}
