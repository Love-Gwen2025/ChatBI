package com.chatbi.schema.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SchemaRecallResult {

    String schemaText;
    List<String> expandedQueries;
    List<SchemaRecallCandidate> candidates;
}
