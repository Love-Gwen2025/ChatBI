package com.chatbi.common.constants;

import java.time.Duration;

public final class CacheKeyConstants {

    private CacheKeyConstants() {}

    public static final String SESSION_PREFIX = "chatbi:session:";
    public static final String MEMORY_PREFIX = "chatbi:memory:";
    public static final String MEMORY_IDS_KEY = "chatbi:memory:ids";
    public static final String MEMORY_SUMMARY_PREFIX = "chatbi:memory:summary:";
    public static final Duration SESSION_TTL = Duration.ofHours(24);
}
