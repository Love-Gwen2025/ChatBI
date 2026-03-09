package com.chatbi.chat.service;

import com.chatbi.common.constants.AppConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqlExecutionService {

    private final DataSource dataSource;
    private final SqlGuardService sqlGuardService;
    private final ObjectMapper objectMapper;

    public String executeForProject(String sql, Long projectId) {
        SqlGuardService.GuardedSql guardedSql = sqlGuardService.guard(sql, projectId);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(AppConstants.SQL_QUERY_TIMEOUT_SECONDS);
            try (ResultSet resultSet = statement.executeQuery(guardedSql.getBoundedSql())) {
                return toJson(resultSet, guardedSql);
            }
        } catch (Exception e) {
            log.warn("SQL 执行失败: {}", e.getMessage());
            throw new IllegalArgumentException("SQL 执行失败: " + e.getMessage(), e);
        }
    }

    private String toJson(ResultSet resultSet, SqlGuardService.GuardedSql guardedSql) throws Exception {
        ResultSetMetaData metaData = resultSet.getMetaData();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            columns.add(metaData.getColumnLabel(i));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                row.put(columns.get(i - 1), resultSet.getObject(i));
            }
            rows.add(row);
        }

        boolean truncated = rows.size() > AppConstants.MAX_SQL_ROWS;
        if (truncated) {
            rows = new ArrayList<>(rows.subList(0, AppConstants.MAX_SQL_ROWS));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("columns", columns);
        payload.put("rows", rows);
        payload.put("rowCount", rows.size());
        payload.put("truncated", truncated);
        payload.put("referencedTables", guardedSql.getReferencedTables());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("SQL 结果序列化失败", e);
        }
    }
}
