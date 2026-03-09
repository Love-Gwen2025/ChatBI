package com.chatbi.chat.service;

public final class ChatTraceKeys {

    private ChatTraceKeys() {
    }

    public static final String PARAM_TRACE_ID = "chatbi.trace_id";
    public static final String PARAM_PARENT_SPAN_ID = "chatbi.parent_span_id";
    public static final String PARAM_CONVERSATION_ID = "chatbi.conversation_id";
    public static final String PARAM_PROJECT_ID = "chatbi.project_id";
    public static final String PARAM_USER_ID = "chatbi.user_id";
    public static final String PARAM_CHAT_MODE = "chatbi.chat_mode";

    public static final String HEADER_TRACEPARENT = "traceparent";
    public static final String HEADER_CONVERSATION_ID = "x-chatbi-conversation-id";
    public static final String HEADER_PROJECT_ID = "x-chatbi-project-id";
    public static final String HEADER_USER_ID = "x-chatbi-user-id";
    public static final String HEADER_CHAT_MODE = "x-chatbi-chat-mode";
    public static final String HEADER_SESSION_ID = "x-chatbi-session-id";
}
