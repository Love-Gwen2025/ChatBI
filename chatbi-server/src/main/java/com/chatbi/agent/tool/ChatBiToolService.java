package com.chatbi.agent.tool;

import com.chatbi.chat.service.ChatAuditService;
import com.chatbi.chat.service.SqlExecutionService;
import com.chatbi.common.StringUtils;
import com.chatbi.common.constants.AppConstants;
import com.chatbi.common.security.UserContext;
import com.chatbi.schema.model.SchemaRecallCandidate;
import com.chatbi.schema.model.SchemaRecallResult;
import com.chatbi.schema.service.SchemaService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatBiToolService {

    private final SchemaService schemaService;
    private final SqlExecutionService sqlExecutionService;
    private final ChatAuditService chatAuditService;

    @Tool(name = "schema_search", value = "检索当前项目下与用户问题最相关的数据库表结构，返回可直接用于生成 SQL 的 schema 上下文。")
    public String schemaSearch(@P("用户问题或检索关键词") String query) {
        long startedAt = System.currentTimeMillis();
        try {
            Long projectId = requireProjectId();
            SchemaRecallResult result = schemaService.searchSchema(query, projectId, AppConstants.SCHEMA_RERANK_TOP_K);
            chatAuditService.recordToolCall("schema_search", "success", query, summarizeRecall(result), System.currentTimeMillis() - startedAt);
            return result.getSchemaText();
        } catch (RuntimeException e) {
            chatAuditService.recordToolCall("schema_search", "error", query, e.getMessage(), System.currentTimeMillis() - startedAt);
            throw e;
        }
    }

    @Tool(name = "execute_sql", value = "执行单条只读 PostgreSQL SELECT 查询。工具内置 SQL Guardrail，会做 AST 校验、项目表白名单校验、行数限制与超时保护。")
    public String executeSql(@P("PostgreSQL SELECT 查询语句") String sql) {
        long startedAt = System.currentTimeMillis();
        try {
            String result = sqlExecutionService.executeForProject(sql, requireProjectId());
            chatAuditService.recordToolCall(
                    "execute_sql",
                    "success",
                    StringUtils.truncate(sql, AppConstants.TRUNCATE_LONG * 3),
                    StringUtils.truncate(result, AppConstants.TRUNCATE_LONG * 6),
                    System.currentTimeMillis() - startedAt);
            return result;
        } catch (RuntimeException e) {
            chatAuditService.recordToolCall(
                    "execute_sql",
                    "error",
                    StringUtils.truncate(sql, AppConstants.TRUNCATE_LONG * 3),
                    e.getMessage(),
                    System.currentTimeMillis() - startedAt);
            throw e;
        }
    }

    private Long requireProjectId() {
        Long projectId = UserContext.getProjectId();
        if (projectId == null) {
            throw new IllegalStateException("当前会话未选择项目，无法执行数据分析工具");
        }
        return projectId;
    }

    private String summarizeRecall(SchemaRecallResult result) {
        List<Map<String, Object>> candidates = result.getCandidates().stream()
                .limit(5)
                .map(this::toCandidateMap)
                .collect(Collectors.toList());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("expandedQueries", result.getExpandedQueries());
        summary.put("candidates", candidates);
        return summary.toString();
    }

    private Map<String, Object> toCandidateMap(SchemaRecallCandidate candidate) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("table", candidate.getTableName());
        map.put("strategy", candidate.getStrategy());
        map.put("fusionScore", candidate.getFusionScore());
        map.put("rerankScore", candidate.getRerankScore());
        map.put("matchedKeywords", candidate.getMatchedKeywords());
        return map;
    }
}
