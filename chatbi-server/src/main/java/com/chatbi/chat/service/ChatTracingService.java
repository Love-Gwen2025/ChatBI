package com.chatbi.chat.service;

import com.chatbi.common.StringUtils;
import com.chatbi.common.constants.AppConstants;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatTracingService {

    private final Tracer tracer;

    public ChatTraceContext.TraceState startRequestTrace(String conversationId,
                                                         Long projectId,
                                                         Long userId,
                                                         String message,
                                                         String requestMode) {
        Span requestSpan = tracer.spanBuilder("chat.request." + requestMode)
                .setNoParent()
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        requestSpan.setAttribute("langfuse.trace.name", "chatbi-chat");
        requestSpan.setAttribute("langfuse.trace.input", truncate(message, AppConstants.TRUNCATE_LONG * 20));
        requestSpan.setAttribute("langfuse.session.id", conversationId != null ? conversationId : "unknown");
        requestSpan.setAttribute("chatbi.mode", requestMode);
        putCommonAttributes(requestSpan, conversationId, projectId, userId);

        return ChatTraceContext.TraceState.builder()
                .traceId(requestSpan.getSpanContext().getTraceId())
                .rootSpanId(requestSpan.getSpanContext().getSpanId())
                .conversationId(conversationId)
                .projectId(projectId)
                .userId(userId)
                .requestMode(requestMode)
                .startedAt(java.time.Instant.now())
                .requestSpan(requestSpan)
                .build();
    }

    public Span startAssistantSpan(ChatTraceContext.TraceState traceState, String message) {
        Span requestSpan = traceState.getRequestSpan();
        Span assistantSpan = tracer.spanBuilder("chat.assistant." + traceState.getRequestMode())
                .setParent(Context.root().with(requestSpan))
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        assistantSpan.setAttribute("langfuse.observation.input", truncate(message, AppConstants.TRUNCATE_LONG * 20));
        assistantSpan.setAttribute("chatbi.mode", traceState.getRequestMode());
        putCommonAttributes(assistantSpan, traceState.getConversationId(), traceState.getProjectId(), traceState.getUserId());
        traceState.setAssistantSpan(assistantSpan);
        return assistantSpan;
    }

    public TraceSpanScope openTraceScope(ChatTraceContext.TraceState traceState) {
        ChatTraceContext.TraceScope traceScope = ChatTraceContext.scope(traceState);
        Scope otelScope = traceState.getRequestSpan() != null ? traceState.getRequestSpan().makeCurrent() : null;
        return () -> {
            if (otelScope != null) {
                otelScope.close();
            }
            traceScope.close();
        };
    }

    public void finishAssistantSpan(ChatTraceContext.TraceState traceState, String output, Throwable error) {
        Span assistantSpan = traceState != null ? traceState.getAssistantSpan() : null;
        if (assistantSpan == null) {
            return;
        }
        completeSpan(assistantSpan, "langfuse.observation.output", output, error);
        traceState.setAssistantSpan(null);
    }

    public void finishRequestTrace(ChatTraceContext.TraceState traceState, String output, Throwable error) {
        if (traceState == null || traceState.getRequestSpan() == null) {
            return;
        }
        completeSpan(traceState.getRequestSpan(), "langfuse.trace.output", output, error);
        traceState.setRequestSpan(null);
    }

    private void completeSpan(Span span, String outputAttribute, String output, Throwable error) {
        if (output != null && !output.isBlank()) {
            span.setAttribute(outputAttribute, truncate(output, AppConstants.TRUNCATE_LONG * 40));
        }
        if (error != null) {
            span.recordException(error);
            span.setStatus(StatusCode.ERROR, error.getMessage());
        } else {
            span.setStatus(StatusCode.OK);
        }
        span.end();
    }

    private void putCommonAttributes(Span span, String conversationId, Long projectId, Long userId) {
        if (conversationId != null && !conversationId.isBlank()) {
            span.setAttribute("chatbi.conversation.id", conversationId);
            span.setAttribute("langfuse.session.id", conversationId);
        }
        if (projectId != null) {
            span.setAttribute("chatbi.project.id", projectId);
        }
        if (userId != null) {
            span.setAttribute("chatbi.user.id", userId);
            span.setAttribute("langfuse.user.id", String.valueOf(userId));
        }
    }

    private String truncate(String value, int maxLength) {
        return StringUtils.truncate(value, maxLength);
    }

    public interface TraceSpanScope extends AutoCloseable {

        @Override
        void close();
    }
}
