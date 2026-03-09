package com.chatbi.chat.service;

import com.chatbi.common.StringUtils;
import com.chatbi.common.constants.AppConstants;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpClientListener;
import dev.langchain4j.mcp.client.McpHeadersSupplier;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class McpToolTracingBridge implements McpHeadersSupplier, McpClientListener {

    private final Tracer tracer;
    private final ChatAuditService chatAuditService;

    private final Map<Long, ToolCallSnapshot> inflightToolCalls = new ConcurrentHashMap<>();

    @Override
    public Map<String, String> apply(McpCallContext callContext) {
        InvocationParameters parameters = invocationParameters(callContext);
        ToolCallSnapshot snapshot = messageId(callContext) != null ? inflightToolCalls.get(messageId(callContext)) : null;
        SpanContext spanContext = snapshot != null && snapshot.span() != null
                ? snapshot.span().getSpanContext()
                : spanContextFromParameters(parameters);

        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        if (spanContext.isValid()) {
            headers.put(ChatTraceKeys.HEADER_TRACEPARENT, toTraceparent(spanContext));
        }

        putHeader(headers, ChatTraceKeys.HEADER_CONVERSATION_ID, get(parameters, ChatTraceKeys.PARAM_CONVERSATION_ID));
        putHeader(headers, ChatTraceKeys.HEADER_PROJECT_ID, get(parameters, ChatTraceKeys.PARAM_PROJECT_ID));
        putHeader(headers, ChatTraceKeys.HEADER_USER_ID, get(parameters, ChatTraceKeys.PARAM_USER_ID));
        putHeader(headers, ChatTraceKeys.HEADER_CHAT_MODE, get(parameters, ChatTraceKeys.PARAM_CHAT_MODE));
        putHeader(headers, ChatTraceKeys.HEADER_SESSION_ID, get(parameters, ChatTraceKeys.PARAM_CONVERSATION_ID));
        return headers.isEmpty() ? Collections.emptyMap() : headers;
    }

    @Override
    public void beforeExecuteTool(McpCallContext callContext) {
        ToolCallDetails details = toolCallDetails(callContext);
        InvocationParameters parameters = invocationParameters(callContext);
        ChatTraceContext.TraceState traceState = traceStateFromParameters(parameters);

        Context parentContext = parentContext(parameters, traceState);
        Span span = tracer.spanBuilder("mcp.tool." + details.toolName())
                .setParent(parentContext)
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();

        span.setAttribute("mcp.tool.name", details.toolName());
        span.setAttribute("langfuse.observation.input", truncate(details.inputText(), AppConstants.TRUNCATE_LONG * 12));
        span.setAttribute("chatbi.mode", traceState != null && traceState.getRequestMode() != null ? traceState.getRequestMode() : "unknown");
        putTraceAttributes(span, traceState);

        Long messageId = messageId(callContext);
        if (messageId != null) {
            inflightToolCalls.put(messageId, new ToolCallSnapshot(details.toolName(), details.inputText(), span,
                    System.currentTimeMillis(), traceState));
        }
    }

    @Override
    public void afterExecuteTool(McpCallContext callContext, ToolExecutionResult result, Map<String, Object> responsePayload) {
        ToolCallSnapshot snapshot = removeSnapshot(callContext);
        String outputText = result != null ? result.resultText() : safeToString(responsePayload);
        boolean error = result != null && result.isError();

        if (snapshot != null) {
            if (error) {
                snapshot.span().setStatus(StatusCode.ERROR, truncate(outputText, AppConstants.TRUNCATE_LONG * 12));
            } else {
                snapshot.span().setStatus(StatusCode.OK);
            }
            snapshot.span().setAttribute("langfuse.observation.output", truncate(outputText, AppConstants.TRUNCATE_LONG * 12));
            snapshot.span().end();
            recordAudit(snapshot, error ? "error" : "success", outputText);
        }
    }

    @Override
    public void onExecuteToolError(McpCallContext callContext, Throwable error) {
        ToolCallSnapshot snapshot = removeSnapshot(callContext);
        if (snapshot == null) {
            return;
        }

        snapshot.span().recordException(error);
        snapshot.span().setStatus(StatusCode.ERROR, error.getMessage());
        snapshot.span().setAttribute("langfuse.observation.output", truncate(error.getMessage(), AppConstants.TRUNCATE_LONG * 12));
        snapshot.span().end();
        recordAudit(snapshot, "error", error.getMessage());
    }

    @Override
    public void beforeResourceGet(McpCallContext callContext) {
    }

    @Override
    public void afterResourceGet(McpCallContext callContext, dev.langchain4j.mcp.client.McpReadResourceResult result, Map<String, Object> payload) {
    }

    @Override
    public void onResourceGetError(McpCallContext callContext, Throwable error) {
    }

    @Override
    public void beforePromptGet(McpCallContext callContext) {
    }

    @Override
    public void afterPromptGet(McpCallContext callContext, dev.langchain4j.mcp.client.McpGetPromptResult result, Map<String, Object> payload) {
    }

    @Override
    public void onPromptGetError(McpCallContext callContext, Throwable error) {
    }

    private void recordAudit(ToolCallSnapshot snapshot, String status, String outputText) {
        try (ChatTraceContext.TraceScope ignored = ChatTraceContext.scope(snapshot.traceState())) {
            chatAuditService.recordToolCall(
                    snapshot.toolName(),
                    status,
                    snapshot.inputText(),
                    outputText,
                    System.currentTimeMillis() - snapshot.startedAt());
        }
    }

    private ToolCallSnapshot removeSnapshot(McpCallContext callContext) {
        Long messageId = messageId(callContext);
        return messageId != null ? inflightToolCalls.remove(messageId) : null;
    }

    private Context parentContext(InvocationParameters parameters, ChatTraceContext.TraceState traceState) {
        SpanContext spanContext = spanContextFromParameters(parameters);
        if (!spanContext.isValid() && traceState != null) {
            spanContext = traceState.currentParentSpanContext();
        }
        return spanContext.isValid() ? Context.root().with(Span.wrap(spanContext)) : Context.current();
    }

    private void putTraceAttributes(Span span, ChatTraceContext.TraceState traceState) {
        if (traceState == null) {
            return;
        }
        if (traceState.getConversationId() != null && !traceState.getConversationId().isBlank()) {
            span.setAttribute("chatbi.conversation.id", traceState.getConversationId());
            span.setAttribute("langfuse.session.id", traceState.getConversationId());
        }
        if (traceState.getProjectId() != null) {
            span.setAttribute("chatbi.project.id", traceState.getProjectId());
        }
        if (traceState.getUserId() != null) {
            span.setAttribute("chatbi.user.id", traceState.getUserId());
            span.setAttribute("langfuse.user.id", String.valueOf(traceState.getUserId()));
        }
    }

    private ChatTraceContext.TraceState traceStateFromParameters(InvocationParameters parameters) {
        if (parameters == null || parameters.asMap().isEmpty()) {
            return null;
        }
        return ChatTraceContext.TraceState.builder()
                .traceId(asString(get(parameters, ChatTraceKeys.PARAM_TRACE_ID)))
                .rootSpanId(asString(get(parameters, ChatTraceKeys.PARAM_PARENT_SPAN_ID)))
                .conversationId(asString(get(parameters, ChatTraceKeys.PARAM_CONVERSATION_ID)))
                .projectId(asLong(get(parameters, ChatTraceKeys.PARAM_PROJECT_ID)))
                .userId(asLong(get(parameters, ChatTraceKeys.PARAM_USER_ID)))
                .requestMode(asString(get(parameters, ChatTraceKeys.PARAM_CHAT_MODE)))
                .startedAt(Instant.now())
                .build();
    }

    private SpanContext spanContextFromParameters(InvocationParameters parameters) {
        if (parameters == null) {
            return SpanContext.getInvalid();
        }
        String traceId = asString(get(parameters, ChatTraceKeys.PARAM_TRACE_ID));
        String parentSpanId = asString(get(parameters, ChatTraceKeys.PARAM_PARENT_SPAN_ID));
        if (!isTraceId(traceId) || !isSpanId(parentSpanId)) {
            return SpanContext.getInvalid();
        }
        return SpanContext.createFromRemoteParent(traceId, parentSpanId, TraceFlags.getSampled(), io.opentelemetry.api.trace.TraceState.getDefault());
    }

    private InvocationParameters invocationParameters(McpCallContext callContext) {
        return callContext != null && callContext.invocationContext() != null
                ? callContext.invocationContext().invocationParameters()
                : null;
    }

    private ToolCallDetails toolCallDetails(McpCallContext callContext) {
        if (callContext != null && callContext.message() instanceof McpCallToolRequest request) {
            Map<String, Object> params = request.getParams();
            Object toolName = params.get("name");
            Object arguments = params.get("arguments");
            String resolvedToolName = asString(toolName);
            return new ToolCallDetails(
                    resolvedToolName != null && !resolvedToolName.isBlank() ? resolvedToolName : "unknown",
                    safeToString(arguments));
        }
        return new ToolCallDetails("unknown", "{}");
    }

    private Long messageId(McpCallContext callContext) {
        return callContext != null && callContext.message() != null ? callContext.message().getId() : null;
    }

    private Object get(InvocationParameters parameters, String key) {
        return parameters != null && parameters.containsKey(key) ? parameters.get(key) : null;
    }

    private void putHeader(Map<String, String> headers, String key, Object value) {
        String text = asString(value);
        if (text != null && !text.isBlank()) {
            headers.put(key, text);
        }
    }

    private String toTraceparent(SpanContext spanContext) {
        String flags = spanContext.isSampled() ? "01" : "00";
        return "00-" + spanContext.getTraceId() + "-" + spanContext.getSpanId() + "-" + flags;
    }

    private boolean isTraceId(String value) {
        return value != null && value.matches("[0-9a-f]{32}");
    }

    private boolean isSpanId(String value) {
        return value != null && value.matches("[0-9a-f]{16}");
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private String safeToString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        return StringUtils.truncate(value, maxLength);
    }

    private record ToolCallDetails(String toolName, String inputText) {
    }

    private record ToolCallSnapshot(String toolName,
                                    String inputText,
                                    Span span,
                                    long startedAt,
                                    ChatTraceContext.TraceState traceState) {
    }
}
