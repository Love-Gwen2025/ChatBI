package com.chatbi.common.constants;

import java.util.Set;
import java.util.regex.Pattern;

public final class InternalTableConstants {

    private InternalTableConstants() {}

    public static final Set<String> INTERNAL_TABLES = Set.of(
            "sys_user", "project", "user_project",
            "table_meta", "column_meta", "conversation"
    );

    public static final Pattern TABLE_WORD_PATTERN = Pattern.compile(
            "\\b(" + String.join("|", INTERNAL_TABLES) + ")\\b",
            Pattern.CASE_INSENSITIVE
    );
}
