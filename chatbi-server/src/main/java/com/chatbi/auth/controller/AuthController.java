package com.chatbi.auth.controller;

import com.chatbi.auth.entity.SysUser;
import com.chatbi.auth.model.AuthResponse;
import com.chatbi.auth.model.LoginRequest;
import com.chatbi.auth.model.RegisterRequest;
import com.chatbi.auth.service.AuthService;
import com.chatbi.common.security.SessionService;
import com.chatbi.common.security.UserContext;
import com.chatbi.project.entity.Project;
import com.chatbi.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final ProjectService projectService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        SysUser user = authService.getCurrentUser(userId);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/project/{projectId}")
    public ResponseEntity<?> switchProject(@PathVariable Long projectId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        // 校验用户有权访问该项目
        String role = projectService.getUserRole(userId, projectId);
        if (role == null) {
            return ResponseEntity.status(403).body(Map.of("error", "无权访问该项目"));
        }
        Project project = projectService.getById(projectId);
        if (project == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "项目不存在"));
        }
        sessionService.updateProjectInSession(userId, projectId, project.getName(), role);
        return ResponseEntity.ok(Map.of("success", true, "projectId", projectId));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            sessionService.deleteSession(userId);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }
}
