package com.chatbi.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.auth.entity.SysUser;
import com.chatbi.auth.mapper.SysUserMapper;
import com.chatbi.auth.model.AuthResponse;
import com.chatbi.auth.model.LoginRequest;
import com.chatbi.auth.model.RegisterRequest;
import com.chatbi.common.security.JwtUtils;
import com.chatbi.common.security.SessionInfo;
import com.chatbi.common.security.SessionService;
import com.chatbi.project.entity.Project;
import com.chatbi.project.entity.UserProject;
import com.chatbi.project.mapper.ProjectMapper;
import com.chatbi.project.mapper.UserProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final JwtUtils jwtUtils;
    private final SessionService sessionService;
    private final UserProjectMapper userProjectMapper;
    private final ProjectMapper projectMapper;

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    public AuthResponse register(RegisterRequest req) {
        // 检查用户名是否已存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (count > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(PASSWORD_ENCODER.encode(req.getPassword()));
        user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        String token = jwtUtils.generateToken(user.getId(), user.getUsername());
        // 新注册用户还没有项目，session 不含 projectId
        AuthResponse response = buildAuthResponse(token, user);
        return response;
    }

    public AuthResponse login(LoginRequest req) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (user == null || !PASSWORD_ENCODER.matches(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername());
        AuthResponse response = buildAuthResponse(token, user);
        return response;
    }

    private AuthResponse buildAuthResponse(String token, SysUser user) {
        SessionInfo session = new SessionInfo();
        session.setUserId(user.getId());
        session.setUsername(user.getUsername());
        session.setNickname(user.getNickname());

        // 查用户的第一个项目作为 lastLoginProjectId
        UserProject firstUp = userProjectMapper.selectOne(
                new LambdaQueryWrapper<UserProject>()
                        .eq(UserProject::getUserId, user.getId())
                        .orderByAsc(UserProject::getId)
                        .last("LIMIT 1"));

        Long lastLoginProjectId = null;
        if (firstUp != null) {
            Project project = projectMapper.selectById(firstUp.getProjectId());
            if (project != null) {
                lastLoginProjectId = project.getId();
                session.setLastLoginProjectId(project.getId());
                session.setLastLoginProjectName(project.getName());
                session.setLastLoginProjectRole(firstUp.getRole());
            }
        }

        sessionService.saveSession(session);

        AuthResponse response = new AuthResponse(token, user);
        response.setLastLoginProjectId(lastLoginProjectId);
        return response;
    }

    public SysUser getCurrentUser(Long userId) {
        return userMapper.selectById(userId);
    }
}
