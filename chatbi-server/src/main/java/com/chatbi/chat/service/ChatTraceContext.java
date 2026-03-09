package com.chatbi.chat.service;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.MDC;

import java.time.Instant;

public final class ChatTraceContext {

    private static final ThreadLocal<TraceState> HOLDER = new ThreadLocal<>();
    private static final String MDC_TRACE_ID_KEY = "traceId";

    private ChatTraceContext() {
    }

    public static TraceState begin(String conversationId, Long projectId, Long userId, String requestMode, Span requestSpan) {
        TraceState state = TraceState.builder()
                .traceId(requestSpan.getSpanContext().getTraceId())
                .rootSpanId(requestSpan.getSpanContext().getSpanId())
                .conversationId(conversationId)
                .projectId(projectId)
                .userId(userId)
                .requestMode(requestMode)
                .startedAt(Instant.now())
                .requestSpan(requestSpan)
                .build();
        set(state);
        return state;
    }

    public static void set(TraceState state) {
        if (state == null) {
            clear();
            return;
        }
        HOLDER.set(state);
        syncMdc(state);
    }

    public static TraceScope scope(TraceState state) {
        TraceState previous = HOLDER.get();
        String previousTraceId = MDC.get(MDC_TRACE_ID_KEY);
        set(state);
        return () -> {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
            restoreMdc(previousTraceId);
        };
    }

    public static TraceState current() {
        return HOLDER.get();
    }

    public static String currentTraceId() {
        TraceState state = HOLDER.get();
        return state != null ? state.getTraceId() : null;
    }

    public static void clear() {
        HOLDER.remove();
        MDC.remove(MDC_TRACE_ID_KEY);
    }

    private static void syncMdc(TraceState state) {
        if (state == null || state.getTraceId() == null || state.getTraceId().isBlank()) {
            MDC.remove(MDC_TRACE_ID_KEY);
            return;
        }
        MDC.put(MDC_TRACE_ID_KEY, state.getTraceId());
    }

    private static void restoreMdc(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            MDC.remove(MDC_TRACE_ID_KEY);
            return;
        }
        MDC.put(MDC_TRACE_ID_KEY, traceId);
    }

    public interface TraceScope extends AutoCloseable {

        @Override
        void close();
    }

    @Getter
    @Setter
    @Builder
    public static class TraceState {
        private String traceId;
        private String rootSpanId;
        private String conversationId;
        private Long projectId;
        private Long userId;
        private String requestMode;
        private Instant startedAt;
        private Span requestSpan;
        private Span assistantSpan;

        public SpanContext currentParentSpanContext() {
            if (assistantSpan != null && assistantSpan.getSpanContext().isValid()) {
                return assistantSpan.getSpanContext();
            }
            if (requestSpan != null && requestSpan.getSpanContext().isValid()) {
                return requestSpan.getSpanContext();
            }
            return SpanContext.getInvalid();
        }

        public String currentParentSpanId() {
            SpanContext parent = currentParentSpanContext();
            return parent.isValid() ? parent.getSpanId() : rootSpanId;
        }
    }
}
