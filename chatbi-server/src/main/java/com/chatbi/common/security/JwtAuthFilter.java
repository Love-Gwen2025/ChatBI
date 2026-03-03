package com.chatbi.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final SessionService sessionService;

    private static final Set<String> WHITE_LIST = Set.of(
            "/api/auth/login",
            "/api/auth/register"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String path = request.getRequestURI();

            // 白名单路径直接放行
            if (WHITE_LIST.stream().anyMatch(path::startsWith)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 提取 token: 优先 Authorization header，降级 query param（SSE 场景）
            String token = extractToken(request);

            if (token == null || !jwtUtils.isValid(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"未登录或 token 已过期\"}");
                return;
            }

            // 解析 token 并设置用户上下文
            Long userId = jwtUtils.getUserId(token);
            String username = jwtUtils.getUsername(token);

            UserContextInfo info = new UserContextInfo();
            info.setUserId(userId);
            info.setUsername(username);

            // 从 Redis Session 读取完整上下文（含 projectId）
            SessionInfo session = sessionService.getSession(userId);
            if (session != null) {
                info.setNickname(session.getNickname());
                info.setProjectId(session.getLastLoginProjectId());
                info.setProjectName(session.getLastLoginProjectName());
                info.setRole(session.getLastLoginProjectRole());
            }

            UserContext.set(info);

            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        // 1. Authorization: Bearer <token>
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        // 2. Query param token（EventSource SSE 场景）
        return request.getParameter("token");
    }
}
