package com.chatbi.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatbi.auth.entity.SysUser;
import com.chatbi.auth.mapper.SysUserMapper;
import com.chatbi.chat.service.ConversationService;
import com.chatbi.common.PageResponse;
import com.chatbi.common.constants.RoleConstants;
import com.chatbi.project.entity.Project;
import com.chatbi.project.entity.UserProject;
import com.chatbi.project.mapper.ProjectMapper;
import com.chatbi.project.mapper.UserProjectMapper;
import com.chatbi.schema.entity.ColumnMeta;
import com.chatbi.schema.entity.TableMeta;
import com.chatbi.schema.mapper.ColumnMetaMapper;
import com.chatbi.schema.mapper.TableMetaMapper;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final UserProjectMapper userProjectMapper;
    private final SysUserMapper sysUserMapper;
    private final ConversationService conversationService;
    private final TableMetaMapper tableMetaMapper;
    private final ColumnMetaMapper columnMetaMapper;

    /**
     * 校验用户拥有指定角色，否则抛异常
     */
    public void requireRole(Long userId, Long projectId, String requiredRole) {
        String role = getUserRole(userId, projectId);
        if (role == null) {
            throw new SecurityException("无权访问该项目");
        }
        if (requiredRole != null && !requiredRole.equals(role)) {
            throw new SecurityException("仅项目所有者可执行此操作");
        }
    }

    @Transactional
    public Project createProject(String name, String description, String tablePrefix, Long ownerId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("项目名称不能为空");
        }
        Project project = new Project();
        project.setName(name.trim());
        project.setDescription(description);
        project.setTablePrefix(tablePrefix);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(project);

        UserProject up = new UserProject();
        up.setUserId(ownerId);
        up.setProjectId(project.getId());
        up.setRole(RoleConstants.OWNER);
        up.setCreatedAt(LocalDateTime.now());
        userProjectMapper.insert(up);

        return project;
    }

    public PageResponse<Project> listByUser(Long userId, int page, int size) {
        MPJLambdaWrapper<Project> wrapper = new MPJLambdaWrapper<Project>()
                .selectAll(Project.class)
                .innerJoin(UserProject.class, UserProject::getProjectId, Project::getId)
                .eq(UserProject::getUserId, userId)
                .orderByDesc(Project::getUpdatedAt);
        IPage<Project> result = projectMapper.selectJoinPage(new Page<>(page, size), Project.class, wrapper);
        return PageResponse.of(result);
    }

    public Project getById(Long projectId) {
        return projectMapper.selectById(projectId);
    }

    public String getUserRole(Long userId, Long projectId) {
        UserProject up = userProjectMapper.selectOne(
                new LambdaQueryWrapper<UserProject>()
                        .eq(UserProject::getUserId, userId)
                        .eq(UserProject::getProjectId, projectId));
        return up != null ? up.getRole() : null;
    }

    public Project updateProject(Long projectId, String name, String description, String tablePrefix) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在");
        }
        if (name != null) project.setName(name);
        if (description != null) project.setDescription(description);
        if (tablePrefix != null) project.setTablePrefix(tablePrefix);
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        return project;
    }

    @Transactional
    public void deleteProject(Long projectId) {
        // 1. 删除项目下所有 column_meta（通过 table_meta JOIN）
        List<Long> tableIds = tableMetaMapper.selectList(
                new LambdaQueryWrapper<TableMeta>().eq(TableMeta::getProjectId, projectId)
                        .select(TableMeta::getId))
                .stream().map(TableMeta::getId).toList();
        if (!tableIds.isEmpty()) {
            columnMetaMapper.delete(
                    new LambdaQueryWrapper<ColumnMeta>().in(ColumnMeta::getTableId, tableIds));
        }
        // 2. 删除项目下所有 table_meta
        tableMetaMapper.delete(
                new LambdaQueryWrapper<TableMeta>().eq(TableMeta::getProjectId, projectId));
        // 3. 删除项目下所有 conversation
        conversationService.deleteByProjectId(projectId);
        // 4. 删除 user_project 关系
        userProjectMapper.delete(
                new LambdaQueryWrapper<UserProject>().eq(UserProject::getProjectId, projectId));
        // 5. 删除 project 本身
        projectMapper.deleteById(projectId);
    }

    @Transactional
    public void addMember(Long projectId, String username, String role) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + username);
        }
        Long count = userProjectMapper.selectCount(
                new LambdaQueryWrapper<UserProject>()
                        .eq(UserProject::getUserId, user.getId())
                        .eq(UserProject::getProjectId, projectId));
        if (count > 0) {
            throw new IllegalArgumentException("用户已是项目成员");
        }
        UserProject up = new UserProject();
        up.setUserId(user.getId());
        up.setProjectId(projectId);
        up.setRole(role != null ? role : RoleConstants.MEMBER);
        up.setCreatedAt(LocalDateTime.now());
        userProjectMapper.insert(up);
    }

    public PageResponse<Map<String, Object>> listMembers(Long projectId, int page, int size) {
        IPage<UserProject> upPage = userProjectMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<UserProject>().eq(UserProject::getProjectId, projectId));
        List<Map<String, Object>> data = upPage.getRecords().stream().map(up -> {
            SysUser user = sysUserMapper.selectById(up.getUserId());
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("userId", up.getUserId());
            map.put("username", user != null ? user.getUsername() : "");
            map.put("nickname", user != null ? user.getNickname() : "");
            map.put("role", up.getRole());
            map.put("joinedAt", up.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
        return PageResponse.of(data, upPage.getTotal(), (int) upPage.getCurrent(), (int) upPage.getSize());
    }

    public void removeMember(Long projectId, Long userId) {
        // 禁止移除最后一个 OWNER
        UserProject target = userProjectMapper.selectOne(
                new LambdaQueryWrapper<UserProject>()
                        .eq(UserProject::getProjectId, projectId)
                        .eq(UserProject::getUserId, userId));
        if (target == null) {
            throw new IllegalArgumentException("该用户不是项目成员");
        }
        if (RoleConstants.OWNER.equals(target.getRole())) {
            Long ownerCount = userProjectMapper.selectCount(
                    new LambdaQueryWrapper<UserProject>()
                            .eq(UserProject::getProjectId, projectId)
                            .eq(UserProject::getRole, RoleConstants.OWNER));
            if (ownerCount <= 1) {
                throw new IllegalArgumentException("不能移除最后一个管理员");
            }
        }
        userProjectMapper.deleteById(target.getId());
    }
}
