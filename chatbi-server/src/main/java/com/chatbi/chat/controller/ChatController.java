package com.chatbi.chat.controller;

import com.chatbi.chat.model.ChatRequest;
import com.chatbi.chat.model.Conversation;
import com.chatbi.chat.service.ChatService;
import com.chatbi.common.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.service.TokenStream;
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
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String reply = chatService.chat(request.getConversationId(), request.getMessage());
        return Map.of("reply", reply != null ? reply : "");
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam String conversationId,
                                   @RequestParam String message) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        TokenStream tokenStream = chatService.chatStream(conversationId, message);

        tokenStream
                .beforeToolExecution(before -> {
                    emitEvent(sink, "tool_start", "name", before.request().name(),
                            "detail", truncate(before.request().arguments(), 200));
                })
                .onToolExecuted(execution -> {
                    emitEvent(sink, "tool_end", "name", execution.request().name(),
                            "detail", truncate(execution.result(), 150));
                })
                .onPartialResponse(token -> {
                    emitEvent(sink, "content", "content", token);
                })
                .onCompleteResponse(response -> {
                    emitEvent(sink, "done");
                    sink.tryEmitComplete();
                })
                .onError(error -> {
                    log.error("SSE 流处理异常", error);
                    emitEvent(sink, "error", "content", error.getMessage());
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
        return chatService.createConversation(title);
    }

    @GetMapping("/conversations")
    public PageResponse<Conversation> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return chatService.listConversations(page, size);
    }

    @DeleteMapping("/conversations/{id}")
    public void deleteConversation(@PathVariable Long id) {
        chatService.deleteConversation(id);
    }

    @PutMapping("/conversations/{id}")
    public void renameConversation(@PathVariable Long id, @RequestBody Map<String, String> body) {
        chatService.renameConversation(id, body.get("title"));
    }

    // ---- SSE 事件工具方法 ----

    private void emitEvent(Sinks.Many<String> sink, String type, String... kvPairs) {
        try {
            Map<String, String> event = new LinkedHashMap<>();
            event.put("type", type);
            for (int i = 0; i < kvPairs.length - 1; i += 2) {
                event.put(kvPairs[i], kvPairs[i + 1]);
            }
            sink.tryEmitNext(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("SSE 事件序列化失败", e);
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
