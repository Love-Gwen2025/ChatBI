package com.chatbi.chat.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class SseEvent {

    private final Map<String, String> data = new LinkedHashMap<>();

    private SseEvent(String type) {
        data.put("type", type);
    }

    public static SseEvent content(String text) {
        SseEvent event = new SseEvent("content");
        event.data.put("content", text);
        return event;
    }

    public static SseEvent toolStart(String name, String detail) {
        SseEvent event = new SseEvent("tool_start");
        event.data.put("name", name);
        event.data.put("detail", detail);
        return event;
    }

    public static SseEvent toolEnd(String name, String detail) {
        SseEvent event = new SseEvent("tool_end");
        event.data.put("name", name);
        event.data.put("detail", detail);
        return event;
    }

    public static SseEvent error(String message) {
        SseEvent event = new SseEvent("error");
        event.data.put("content", message);
        return event;
    }

    public static SseEvent done() {
        return new SseEvent("done");
    }

    public Map<String, String> toMap() {
        return data;
    }
}
