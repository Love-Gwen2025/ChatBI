package com.chatbi.common.security;

import lombok.Data;

import java.io.Serializable;

@Data
public class SessionInfo implements Serializable {
    private Long userId;
    private String username;
    private String nickname;
    private Long lastLoginProjectId;
    private String lastLoginProjectName;
    private String lastLoginProjectRole;
}
