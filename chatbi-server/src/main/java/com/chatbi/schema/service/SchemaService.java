package com.chatbi.schema.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatbi.common.PageResponse;
import com.chatbi.schema.entity.ColumnMeta;
import com.chatbi.schema.entity.TableMeta;
import com.chatbi.schema.mapper.ColumnMetaMapper;
import com.chatbi.schema.mapper.TableMetaMapper;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaService {

    private final TableMetaMapper tableMetaMapper;
    private final ColumnMetaMapper columnMetaMapper;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

    private static final JiebaSegmenter SEGMENTER = new JiebaSegmenter();

    private static final Set<String> INTERNAL_TABLES = Set.of(
            "table_meta", "column_meta", "conversation",
            "sys_user", "project", "user_project"
    );

    public void requireTableInProject(Long tableId, Long projectId) {
        if (tableId == null || projectId == null) {
            throw new SecurityException("无权访问该表");
        }
        Long count = tableMetaMapper.selectCount(
                new LambdaQueryWrapper<TableMeta>()
                        .eq(TableMeta::getId, tableId)
                        .eq(TableMeta::getProjectId, projectId));
        if (count == 0) {
            throw new SecurityException("无权访问该表");
        }
    }

    @Transactional
    public int importFromDatabase(String prefix, Long projectId) {
        int count = 0;
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet tables = meta.getTables(null, "public", prefix != null ? prefix + "%" : "%", new String[]{"TABLE"});

            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                String tableComment = tables.getString("REMARKS");

                if (INTERNAL_TABLES.contains(tableName)) {
                    continue;
                }

                Long existing = tableMetaMapper.selectCount(
                        new LambdaQueryWrapper<TableMeta>()
                                .eq(TableMeta::getTableName, tableName)
                                .eq(TableMeta::getProjectId, projectId));
                if (existing > 0) {
                    log.debug("表已存在，跳过: {}", tableName);
                    continue;
                }

                TableMeta tableMeta = new TableMeta();
                tableMeta.setTableName(tableName);
                tableMeta.setTableComment(tableComment);
                tableMeta.setSchemaName("public");
                tableMeta.setProjectId(projectId);
                tableMetaMapper.insert(tableMeta);

                List<ColumnMeta> columns = importColumns(meta, tableName, tableMeta.getId());
                generateAndStoreEmbedding(tableMeta, columns);

                count++;
                log.info("导入表: {} ({})", tableName, tableComment);
            }
        } catch (SQLException e) {
            throw new RuntimeException("导入表结构失败", e);
        }
        return count;
    }

    private List<ColumnMeta> importColumns(DatabaseMetaData meta, String tableName, Long tableId) throws SQLException {
        List<String> pkColumns = new ArrayList<>();
        try (ResultSet pkRs = meta.getPrimaryKeys(null, "public", tableName)) {
            while (pkRs.next()) {
                pkColumns.add(pkRs.getString("COLUMN_NAME"));
            }
        }

        List<ColumnMeta> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(null, "public", tableName, "%")) {
            while (rs.next()) {
                ColumnMeta col = new ColumnMeta();
                col.setTableId(tableId);
                col.setColumnName(rs.getString("COLUMN_NAME"));
                col.setColumnType(rs.getString("TYPE_NAME"));
                col.setColumnComment(rs.getString("REMARKS"));
                col.setIsPrimaryKey(pkColumns.contains(col.getColumnName()));
                col.setOrdinal(rs.getInt("ORDINAL_POSITION"));
                columnMetaMapper.insert(col);
                columns.add(col);
            }
        }
        return columns;
    }

    // ========== 向量化 ==========

    /**
     * 为单张表生成 schema_text 和 embedding 并存入 DB。
     * schema_text 同时用于：1) embedding 输入  2) LLM prompt 输出
     */
    private void generateAndStoreEmbedding(TableMeta table, List<ColumnMeta> columns) {
        try {
            String schemaText = buildSchemaText(table, columns);
            table.setSchemaText(schemaText);
            tableMetaMapper.updateById(table);

            Embedding embedding = embeddingModel.embed(schemaText).content();
            String vectorStr = vectorToString(embedding.vector());
            jdbcTemplate.update("UPDATE table_meta SET embedding = ?::vector WHERE id = ?",
                    vectorStr, table.getId());
            log.debug("生成 embedding: {} ({} 维)", table.getTableName(), embedding.vector().length);
        } catch (Exception e) {
            log.warn("生成 embedding 失败: {} - {}", table.getTableName(), e.getMessage());
        }
    }

    /**
     * 对项目下所有表重新生成 embedding
     */
    public int reindexEmbeddings(Long projectId) {
        List<TableMeta> tables = tableMetaMapper.selectList(
                new LambdaQueryWrapper<TableMeta>().eq(TableMeta::getProjectId, projectId));
        int count = 0;
        for (TableMeta table : tables) {
            List<ColumnMeta> columns = getColumns(table.getId());
            generateAndStoreEmbedding(table, columns);
            count++;
        }
        log.info("重新生成 embedding: {} 张表", count);
        return count;
    }

    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    // ========== 搜索 ==========

    /**
     * 向量相似度搜索
     */
    public List<TableMeta> vectorSearch(String query, int topK, Long projectId) {
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            String vectorStr = vectorToString(queryEmbedding.vector());
            return jdbcTemplate.query(
                    "SELECT id, table_name, table_comment, schema_name, project_id, schema_text " +
                            "FROM table_meta " +
                            "WHERE project_id = ? AND embedding IS NOT NULL " +
                            "ORDER BY embedding <=> ?::vector " +
                            "LIMIT ?",
                    (rs, i) -> {
                        TableMeta t = new TableMeta();
                        t.setId(rs.getLong("id"));
                        t.setTableName(rs.getString("table_name"));
                        t.setTableComment(rs.getString("table_comment"));
                        t.setSchemaName(rs.getString("schema_name"));
                        t.setProjectId(rs.getLong("project_id"));
                        t.setSchemaText(rs.getString("schema_text"));
                        return t;
                    },
                    projectId, vectorStr, topK);
        } catch (Exception e) {
            log.warn("向量搜索失败，回退到 ILIKE: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * ILIKE 搜索（单关键词，含字段级，MPJ Lambda JOIN）
     */
    public List<TableMeta> searchTablesWithColumns(String keyword, int topK, Long projectId) {
        return searchTablesWithColumns(List.of(keyword), topK, projectId);
    }

    /**
     * ILIKE 搜索（多关键词合并为一条 SQL，OR 拼接，1 次 DB 往返）
     */
    public List<TableMeta> searchTablesWithColumns(List<String> keywords, int topK, Long projectId) {
        if (keywords.isEmpty()) {
            return List.of();
        }
        MPJLambdaWrapper<TableMeta> wrapper = new MPJLambdaWrapper<TableMeta>()
                .selectAll(TableMeta.class)
                .leftJoin(ColumnMeta.class, ColumnMeta::getTableId, TableMeta::getId)
                .eq(TableMeta::getProjectId, projectId)
                .and(outer -> {
                    for (int i = 0; i < keywords.size(); i++) {
                        String kw = keywords.get(i);
                        if (i == 0) {
                            outer.like(TableMeta::getTableName, kw)
                                    .or().like(TableMeta::getTableComment, kw)
                                    .or().like(ColumnMeta::getColumnName, kw)
                                    .or().like(ColumnMeta::getColumnComment, kw);
                        } else {
                            outer.or().like(TableMeta::getTableName, kw)
                                    .or().like(TableMeta::getTableComment, kw)
                                    .or().like(ColumnMeta::getColumnName, kw)
                                    .or().like(ColumnMeta::getColumnComment, kw);
                        }
                    }
                })
                .distinct()
                .last("LIMIT " + topK);
        return tableMetaMapper.selectJoinList(TableMeta.class, wrapper);
    }

    /**
     * 混合搜索：向量 + ILIKE（jieba 分词），合并去重
     */
    public String searchAndBuildSchemaText(String query, int topK, Long projectId) {
        // 1. 向量搜索
        List<TableMeta> results = new ArrayList<>(vectorSearch(query, topK, projectId));
        Set<Long> seen = results.stream().map(TableMeta::getId).collect(Collectors.toSet());

        // 2. jieba 分词，收集所有关键词（分词结果 + 原始 query）
        List<String> keywords = new ArrayList<>(
                SEGMENTER.process(query, JiebaSegmenter.SegMode.SEARCH)
                        .stream()
                        .map(token -> token.word.trim())
                        .filter(w -> w.length() >= 2)
                        .distinct()
                        .toList());
        if (query.length() >= 2 && !keywords.contains(query)) {
            keywords.add(query);
        }

        // 3. 一次 DB 查询完成所有关键词的 ILIKE 匹配
        for (TableMeta t : searchTablesWithColumns(keywords, topK, projectId)) {
            if (seen.add(t.getId())) {
                results.add(t);
            }
        }

        // 4. 截断
        if (results.size() > topK) {
            results = results.subList(0, topK);
        }

        // 5. 拼接 schema_text
        if (results.isEmpty()) {
            return "未找到与查询相关的表。";
        }
        return results.stream()
                .map(t -> t.getSchemaText() != null ? t.getSchemaText()
                        : "表名: " + t.getTableName())
                .collect(Collectors.joining("\n---\n"));
    }

    // ========== Schema 文本构建 ==========

    /**
     * 构建单表 schema 文本（统一格式，用于 embedding 输入和 LLM prompt 输出）
     */
    private String buildSchemaText(TableMeta table, List<ColumnMeta> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("表名: ").append(table.getTableName());
        if (table.getTableComment() != null) {
            sb.append(" (").append(table.getTableComment()).append(")");
        }
        sb.append("\n字段:\n");

        for (ColumnMeta col : columns) {
            sb.append("  - ").append(col.getColumnName())
                    .append(" ").append(col.getColumnType());
            if (Boolean.TRUE.equals(col.getIsPrimaryKey())) {
                sb.append(" [PK]");
            }
            if (col.getColumnComment() != null && !col.getColumnComment().isEmpty()) {
                sb.append(" -- ").append(col.getColumnComment());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 通过 tableId 获取 schema 文本（优先读缓存，未生成时实时构建）
     */
    public String buildSchemaText(Long tableId) {
        TableMeta table = tableMetaMapper.selectById(tableId);
        if (table == null) {
            return "";
        }
        if (table.getSchemaText() != null) {
            return table.getSchemaText();
        }
        List<ColumnMeta> columns = getColumns(tableId);
        return buildSchemaText(table, columns);
    }

    // ========== 列表查询 ==========

    public PageResponse<TableMeta> listTables(Long projectId, int page, int size) {
        IPage<TableMeta> result = tableMetaMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<TableMeta>().eq(TableMeta::getProjectId, projectId));
        return PageResponse.of(result);
    }

    public List<ColumnMeta> getColumns(Long tableId) {
        return columnMetaMapper.selectList(
                new LambdaQueryWrapper<ColumnMeta>()
                        .eq(ColumnMeta::getTableId, tableId)
                        .orderByAsc(ColumnMeta::getOrdinal));
    }

    public PageResponse<ColumnMeta> getColumns(Long tableId, int page, int size) {
        IPage<ColumnMeta> result = columnMetaMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<ColumnMeta>()
                        .eq(ColumnMeta::getTableId, tableId)
                        .orderByAsc(ColumnMeta::getOrdinal));
        return PageResponse.of(result);
    }
}
