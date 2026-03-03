package com.chatbi.common.security;

public class UserContext {

    private static final ThreadLocal<UserContextInfo> CONTEXT = new ThreadLocal<>();

    public static void set(UserContextInfo info) {
        CONTEXT.set(info);
    }

    public static UserContextInfo get() {
        return CONTEXT.get();
    }

    public static Long getUserId() {
        UserContextInfo info = CONTEXT.get();
        return info != null ? info.getUserId() : null;
    }

    public static String getUsername() {
        UserContextInfo info = CONTEXT.get();
        return info != null ? info.getUsername() : null;
    }

    public static Long getProjectId() {
        UserContextInfo info = CONTEXT.get();
        return info != null ? info.getProjectId() : null;
    }

    public static String getNickname() {
        UserContextInfo info = CONTEXT.get();
        return info != null ? info.getNickname() : null;
    }

    public static String getProjectName() {
        UserContextInfo info = CONTEXT.get();
        return info != null ? info.getProjectName() : null;
    }

    public static String getRole() {
        UserContextInfo info = CONTEXT.get();
        return info != null ? info.getRole() : null;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
