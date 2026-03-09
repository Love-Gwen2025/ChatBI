package com.chatbi.schema.model;

import com.chatbi.schema.entity.TableMeta;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SchemaRecallCandidate {

    TableMeta table;
    List<String> matchedKeywords;
    double vectorScore;
    double keywordScore;
    double fusionScore;
    double rerankScore;
    String strategy;

    public String getTableName() {
        return table != null ? table.getTableName() : null;
    }
}
