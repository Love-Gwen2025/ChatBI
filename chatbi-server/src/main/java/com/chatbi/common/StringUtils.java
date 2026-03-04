package com.chatbi.common;

public final class StringUtils {

    private StringUtils() {}

    public static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
