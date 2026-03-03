package com.chatbi.chat.model;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String conversationId;
}
