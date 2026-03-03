package com.chatbi.common.security;

import lombok.Data;

@Data
public class UserContextInfo {
    private Long userId;
    private String username;
    private String nickname;
    private Long projectId;
    private String projectName;
    private String role;
}
