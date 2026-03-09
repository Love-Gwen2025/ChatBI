package com.chatbi.chat.controller;

import com.chatbi.chat.model.ChatRequest;
import com.chatbi.chat.model.Conversation;
import com.chatbi.chat.model.SseEvent;
import com.chatbi.chat.service.ChatAuditService;
import com.chatbi.chat.service.ChatService;
import com.chatbi.chat.service.ChatTraceContext;
import com.chatbi.chat.service.ChatTracingService;
import com.chatbi.chat.service.ConversationService;
import com.chatbi.common.PageResponse;
import com.chatbi.common.StringUtils;
import com.chatbi.common.constants.AppConstants;
import com.chatbi.common.security.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.service.TokenStream;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ConversationService conversationService;
    private final ChatAuditService chatAuditService;
    private final ChatTracingService chatTracingService;
    private final ObjectMapper objectMapper;

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        conversationService.verifyConversationAccess(request.getConversationId());
        ChatTraceContext.TraceState traceState = chatTracingService.startRequestTrace(
                request.getConversationId(),
                UserContext.getProjectId(),
                UserContext.getUserId(),
                request.getMessage(),
                "sync");

        try (ChatTracingService.TraceSpanScope traceScope = chatTracingService.openTraceScope(traceState)) {
            chatAuditService.recordUserMessage(request.getConversationId(), request.getMessage());
            Span assistantSpan = chatTracingService.startAssistantSpan(traceState, request.getMessage());
            try (Scope assistantScope = assistantSpan.makeCurrent()) {
                String reply = chatService.chat(request.getConversationId(), request.getMessage());
                chatTracingService.finishAssistantSpan(traceState, reply, null);
                chatAuditService.recordAssistantMessage(request.getConversationId(), reply, assistantMetadata("sync", assistantSpan));
                chatTracingService.finishRequestTrace(traceState, reply, null);
                return Map.of("reply", reply != null ? reply : "");
            } catch (RuntimeException e) {
                chatTracingService.finishAssistantSpan(traceState, null, e);
                chatAuditService.recordAssistantError(request.getConversationId(), e.getMessage());
                chatTracingService.finishRequestTrace(traceState, null, e);
                throw e;
            }
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder assistantContent = new StringBuilder();

        conversationService.verifyConversationAccess(request.getConversationId());
        ChatTraceContext.TraceState traceState = chatTracingService.startRequestTrace(
                request.getConversationId(),
                UserContext.getProjectId(),
                UserContext.getUserId(),
                request.getMessage(),
                "stream");

        Span assistantSpan = null;
        TokenStream tokenStream;
        try (ChatTracingService.TraceSpanScope traceScope = chatTracingService.openTraceScope(traceState)) {
            chatAuditService.recordUserMessage(request.getConversationId(), request.getMessage());
            assistantSpan = chatTracingService.startAssistantSpan(traceState, request.getMessage());
            try (Scope assistantScope = assistantSpan.makeCurrent()) {
                tokenStream = chatService.chatStream(request.getConversationId(), request.getMessage());
            }
        } catch (RuntimeException e) {
            withTrace(traceState, () -> chatAuditService.recordAssistantError(request.getConversationId(), e.getMessage()));
            chatTracingService.finishAssistantSpan(traceState, null, e);
            chatTracingService.finishRequestTrace(traceState, null, e);
            throw e;
        }

        final Span finalAssistantSpan = assistantSpan;
        tokenStream
                .beforeToolExecution(before -> withTrace(traceState, () -> emitEvent(sink, SseEvent.toolStart(
                        before.request().name(),
                        StringUtils.truncate(before.request().arguments(), AppConstants.TRUNCATE_LONG)))))
                .onToolExecuted(execution -> withTrace(traceState, () -> emitEvent(sink, SseEvent.toolEnd(
                        execution.request().name(),
                        StringUtils.truncate(execution.result(), AppConstants.TRUNCATE_SHORT)))))
                .onPartialResponse(token -> withTrace(traceState, () -> {
                    assistantContent.append(token);
                    emitEvent(sink, SseEvent.content(token));
                }))
                .onCompleteResponse(response -> withTrace(traceState, () -> {
                    chatAuditService.recordAssistantMessage(
                            request.getConversationId(),
                            assistantContent.toString(),
                            assistantMetadata("stream", finalAssistantSpan));
                    chatTracingService.finishAssistantSpan(traceState, assistantContent.toString(), null);
                    chatTracingService.finishRequestTrace(traceState, assistantContent.toString(), null);
                    emitEvent(sink, SseEvent.done());
                    sink.tryEmitComplete();
                }))
                .onError(error -> withTrace(traceState, () -> {
                    log.error("SSE 流处理异常", error);
                    if (assistantContent.length() > 0) {
                        Map<String, Object> metadata = assistantMetadata("stream", finalAssistantSpan);
                        metadata.put("status", "error");
                        chatAuditService.recordAssistantMessage(request.getConversationId(), assistantContent.toString(), metadata);
                    } else {
                        chatAuditService.recordAssistantError(request.getConversationId(), error.getMessage());
                    }
                    chatTracingService.finishAssistantSpan(traceState, assistantContent.toString(), error);
                    chatTracingService.finishRequestTrace(traceState, assistantContent.toString(), error);
                    emitEvent(sink, SseEvent.error(error.getMessage()));
                    sink.tryEmitComplete();
                }))
                .start();

        return sink.asFlux();
    }

    @GetMapping("/chat/messages")
    public List<Map<String, Object>> getMessages(@RequestParam String conversationId) {
        return chatService.getMessages(conversationId);
    }

    @PostMapping("/conversations")
    public Conversation createConversation(@RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.get("title") : null;
        return conversationService.createConversation(title);
    }

    @GetMapping("/conversations")
    public PageResponse<Conversation> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return conversationService.listConversations(page, size);
    }

    @DeleteMapping("/conversations/{id}")
    public void deleteConversation(@PathVariable Long id) {
        conversationService.deleteConversation(id);
    }

    @PutMapping("/conversations/{id}")
    public void renameConversation(@PathVariable Long id, @RequestBody Map<String, String> body) {
        conversationService.renameConversation(id, body.get("title"));
    }

    private Map<String, Object> assistantMetadata(String mode, Span assistantSpan) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mode", mode);
        metadata.put("traceId", ChatTraceContext.currentTraceId());
        if (assistantSpan != null && assistantSpan.getSpanContext().isValid()) {
            metadata.put("observationId", assistantSpan.getSpanContext().getSpanId());
        }
        return metadata;
    }

    private void withTrace(ChatTraceContext.TraceState traceState, Runnable action) {
        try (ChatTracingService.TraceSpanScope traceScope = chatTracingService.openTraceScope(traceState)) {
            action.run();
        }
    }

    private void emitEvent(Sinks.Many<String> sink, SseEvent event) {
        try {
            sink.tryEmitNext(objectMapper.writeValueAsString(event.toMap()) + "\n");
        } catch (Exception e) {
            log.error("SSE 事件序列化失败", e);
        }
    }
}
