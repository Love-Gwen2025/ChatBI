package com.chatbi.schema.controller;

import com.chatbi.common.PageResponse;
import com.chatbi.common.security.UserContext;
import com.chatbi.schema.entity.ColumnMeta;
import com.chatbi.schema.entity.TableMeta;
import com.chatbi.schema.service.SchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/schema")
@RequiredArgsConstructor
public class SchemaController {

    private final SchemaService schemaService;

    @PostMapping("/import")
    public Map<String, Object> importSchema(@RequestParam(required = false) String prefix) {
        Long projectId = UserContext.getProjectId();
        int count = schemaService.importFromDatabase(prefix, projectId);
        Map<String, Object> result = new HashMap<>();
        result.put("imported", count);
        return result;
    }

    @GetMapping("/tables")
    public PageResponse<TableMeta> listTables(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long projectId = UserContext.getProjectId();
        return schemaService.listTables(projectId, page, size);
    }

    @GetMapping("/tables/{tableId}/columns")
    public PageResponse<ColumnMeta> getColumns(
            @PathVariable Long tableId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return schemaService.getColumns(tableId, page, size);
    }

    @GetMapping("/search")
    public String searchSchema(@RequestParam String query,
                               @RequestParam(defaultValue = "5") int topK) {
        Long projectId = UserContext.getProjectId();
        return schemaService.searchAndBuildSchemaText(query, topK, projectId);
    }

    @PostMapping("/reindex")
    public Map<String, Object> reindexEmbeddings() {
        Long projectId = UserContext.getProjectId();
        int count = schemaService.reindexEmbeddings(projectId);
        Map<String, Object> result = new HashMap<>();
        result.put("reindexed", count);
        return result;
    }
}
