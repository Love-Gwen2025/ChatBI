package com.chatbi.auth.model;

import com.chatbi.auth.entity.SysUser;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private SysUser user;
    private Long lastLoginProjectId;

    public AuthResponse(String token, SysUser user) {
        this.token = token;
        this.user = user;
    }
}
