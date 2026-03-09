package com.chatbi.schema.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatbi.common.PageResponse;
import com.chatbi.common.constants.AppConstants;
import com.chatbi.common.constants.InternalTableConstants;
import com.chatbi.schema.entity.ColumnMeta;
import com.chatbi.schema.entity.TableMeta;
import com.chatbi.schema.mapper.ColumnMetaMapper;
import com.chatbi.schema.mapper.TableMetaMapper;
import com.chatbi.schema.model.SchemaRecallCandidate;
import com.chatbi.schema.model.SchemaRecallResult;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.huaban.analysis.jieba.JiebaSegmenter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaService {

    private static final JiebaSegmenter SEGMENTER = new JiebaSegmenter();
    private static final Map<String, List<String>> QUERY_SYNONYMS = Map.of(
            "营收", List.of("销售额", "金额", "amount"),
            "销量", List.of("数量", "quantity"),
            "客户", List.of("customer", "customers"),
            "产品", List.of("product", "products"),
            "订单", List.of("order", "orders")
    );

    private final TableMetaMapper tableMetaMapper;
    private final ColumnMetaMapper columnMetaMapper;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

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

                if (InternalTableConstants.INTERNAL_TABLES.contains(tableName)) {
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
            log.warn("向量搜索失败，回退到关键词召回: {}", e.getMessage());
            return List.of();
        }
    }

    public List<TableMeta> searchTablesWithColumns(String keyword, int topK, Long projectId) {
        return searchTablesWithColumns(List.of(keyword), topK, projectId);
    }

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

    public SchemaRecallResult searchSchema(String query, Long projectId, int topK) {
        List<String> expandedQueries = buildExpandedQueries(query);
        List<TableMeta> denseCandidates = vectorSearch(query, Math.max(topK, AppConstants.SCHEMA_VECTOR_TOP_K), projectId);
        List<TableMeta> sparseCandidates = searchTablesWithColumns(expandedQueries, Math.max(topK, AppConstants.SCHEMA_KEYWORD_TOP_K), projectId);
        List<SchemaRecallCandidate> rankedCandidates = fuseAndRerank(query, expandedQueries, denseCandidates, sparseCandidates);
        List<SchemaRecallCandidate> topCandidates = rankedCandidates.stream().limit(topK).toList();

        String schemaText = topCandidates.isEmpty()
                ? "未找到与查询相关的表。"
                : topCandidates.stream()
                .map(candidate -> {
                    TableMeta table = candidate.getTable();
                    return table.getSchemaText() != null ? table.getSchemaText() : buildSchemaText(table.getId());
                })
                .collect(Collectors.joining("\n---\n"));

        return SchemaRecallResult.builder()
                .schemaText(schemaText)
                .expandedQueries(expandedQueries)
                .candidates(topCandidates)
                .build();
    }

    public String searchAndBuildSchemaText(String query, int topK, Long projectId) {
        return searchSchema(query, projectId, topK).getSchemaText();
    }

    public Set<String> listProjectTableNames(Long projectId) {
        return tableMetaMapper.selectList(
                        new LambdaQueryWrapper<TableMeta>()
                                .eq(TableMeta::getProjectId, projectId)
                                .select(TableMeta::getTableName))
                .stream()
                .map(TableMeta::getTableName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> buildExpandedQueries(String query) {
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        if (query != null && query.length() >= 2) {
            expanded.add(query.trim());
        }
        SEGMENTER.process(query, JiebaSegmenter.SegMode.SEARCH)
                .stream()
                .map(token -> token.word.trim())
                .filter(word -> word.length() >= 2)
                .forEach(expanded::add);

        List<String> snapshot = new ArrayList<>(expanded);
        for (String term : snapshot) {
            QUERY_SYNONYMS.forEach((key, synonyms) -> {
                if (term.contains(key) || key.contains(term)) {
                    expanded.addAll(synonyms);
                }
            });
        }
        return new ArrayList<>(expanded);
    }

    private List<SchemaRecallCandidate> fuseAndRerank(String query,
                                                      List<String> expandedQueries,
                                                      List<TableMeta> denseCandidates,
                                                      List<TableMeta> sparseCandidates) {
        Map<Long, RecallAccumulator> accumulators = new LinkedHashMap<>();
        applyRrfScores(accumulators, denseCandidates, true);
        applyRrfScores(accumulators, sparseCandidates, false);

        String rawQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return accumulators.values().stream()
                .map(accumulator -> toCandidate(accumulator, expandedQueries, rawQuery))
                .sorted(Comparator.comparingDouble(SchemaRecallCandidate::getRerankScore).reversed()
                        .thenComparingDouble(SchemaRecallCandidate::getFusionScore).reversed())
                .toList();
    }

    private void applyRrfScores(Map<Long, RecallAccumulator> accumulators, List<TableMeta> candidates, boolean dense) {
        for (int i = 0; i < candidates.size(); i++) {
            TableMeta table = candidates.get(i);
            RecallAccumulator accumulator = accumulators.computeIfAbsent(table.getId(), key -> new RecallAccumulator(table));
            double score = 1.0d / (AppConstants.RRF_K + i + 1);
            if (dense) {
                accumulator.vectorScore += score;
                accumulator.denseHit = true;
            } else {
                accumulator.keywordScore += score;
                accumulator.sparseHit = true;
            }
        }
    }

    private SchemaRecallCandidate toCandidate(RecallAccumulator accumulator, List<String> expandedQueries, String rawQuery) {
        TableMeta table = accumulator.table;
        String schemaText = table.getSchemaText() != null ? table.getSchemaText() : buildSchemaText(table.getId());
        String normalizedSchema = schemaText.toLowerCase(Locale.ROOT);
        List<String> matchedKeywords = expandedQueries.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .filter(keyword -> normalizedSchema.contains(keyword.toLowerCase(Locale.ROOT)))
                .limit(8)
                .toList();

        double fusionScore = accumulator.vectorScore + accumulator.keywordScore;
        double rerankScore = fusionScore;
        if (accumulator.denseHit && accumulator.sparseHit) {
            rerankScore += 0.18d;
        }
        rerankScore += Math.min(0.42d, matchedKeywords.size() * 0.07d);
        if (!rawQuery.isBlank() && table.getTableName() != null && table.getTableName().toLowerCase(Locale.ROOT).contains(rawQuery)) {
            rerankScore += 0.2d;
        }
        if (!rawQuery.isBlank() && table.getTableComment() != null && table.getTableComment().toLowerCase(Locale.ROOT).contains(rawQuery)) {
            rerankScore += 0.12d;
        }

        return SchemaRecallCandidate.builder()
                .table(table)
                .matchedKeywords(matchedKeywords)
                .vectorScore(accumulator.vectorScore)
                .keywordScore(accumulator.keywordScore)
                .fusionScore(fusionScore)
                .rerankScore(rerankScore)
                .strategy(resolveStrategy(accumulator))
                .build();
    }

    private String resolveStrategy(RecallAccumulator accumulator) {
        if (accumulator.denseHit && accumulator.sparseHit) {
            return "dense+sparse+rerank";
        }
        if (accumulator.denseHit) {
            return "dense";
        }
        return "sparse";
    }

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

    private static class RecallAccumulator {
        private final TableMeta table;
        private double vectorScore;
        private double keywordScore;
        private boolean denseHit;
        private boolean sparseHit;

        private RecallAccumulator(TableMeta table) {
            this.table = table;
        }
    }
}
