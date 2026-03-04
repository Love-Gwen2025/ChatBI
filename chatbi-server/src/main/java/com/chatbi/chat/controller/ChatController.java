package com.chatbi.chat.controller;

import com.chatbi.chat.model.ChatRequest;
import com.chatbi.chat.model.Conversation;
import com.chatbi.chat.model.SseEvent;
import com.chatbi.chat.service.ChatService;
import com.chatbi.chat.service.ConversationService;
import com.chatbi.common.PageResponse;
import com.chatbi.common.StringUtils;
import com.chatbi.common.constants.AppConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String reply = chatService.chat(request.getConversationId(), request.getMessage());
        return Map.of("reply", reply != null ? reply : "");
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        TokenStream tokenStream = chatService.chatStream(request.getConversationId(), request.getMessage());

        tokenStream
                .beforeToolExecution(before -> {
                    emitEvent(sink, SseEvent.toolStart(
                            before.request().name(),
                            StringUtils.truncate(before.request().arguments(), AppConstants.TRUNCATE_LONG)));
                })
                .onToolExecuted(execution -> {
                    emitEvent(sink, SseEvent.toolEnd(
                            execution.request().name(),
                            StringUtils.truncate(execution.result(), AppConstants.TRUNCATE_SHORT)));
                })
                .onPartialResponse(token -> {
                    emitEvent(sink, SseEvent.content(token));
                })
                .onCompleteResponse(response -> {
                    emitEvent(sink, SseEvent.done());
                    sink.tryEmitComplete();
                })
                .onError(error -> {
                    log.error("SSE 流处理异常", error);
                    emitEvent(sink, SseEvent.error(error.getMessage()));
                    sink.tryEmitComplete();
                })
                .start();

        return sink.asFlux();
    }

    @GetMapping("/chat/messages")
    public List<Map<String, Object>> getMessages(@RequestParam String conversationId) {
        return chatService.getMessages(conversationId);
    }

    // ---- 会话管理 ----

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

    // ---- SSE 事件工具方法 ----

    private void emitEvent(Sinks.Many<String> sink, SseEvent event) {
        try {
            sink.tryEmitNext(objectMapper.writeValueAsString(event.toMap()) + "\n");
        } catch (Exception e) {
            log.error("SSE 事件序列化失败", e);
        }
    }

}
