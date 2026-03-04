package com.chatbi.project.controller;

import com.chatbi.common.PageResponse;
import com.chatbi.common.constants.RoleConstants;
import com.chatbi.common.security.SessionService;
import com.chatbi.common.security.UserContext;
import com.chatbi.project.entity.Project;
import com.chatbi.project.model.AddMemberRequest;
import com.chatbi.project.model.CreateProjectRequest;
import com.chatbi.project.model.UpdateProjectRequest;
import com.chatbi.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final SessionService sessionService;

    @PostMapping
    public Project create(@Valid @RequestBody CreateProjectRequest request) {
        Project project = projectService.createProject(
                request.getName(),
                request.getDescription(),
                request.getTablePrefix(),
                UserContext.getUserId());
        // 若用户当前无 projectId，自动切换到新建项目
        if (UserContext.getProjectId() == null) {
            sessionService.updateProjectInSession(
                    UserContext.getUserId(), project.getId(), project.getName(), RoleConstants.OWNER);
        }
        return project;
    }

    @GetMapping
    public PageResponse<Project> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return projectService.listByUser(UserContext.getUserId(), page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        Project project = projectService.getById(id);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }
        String role = projectService.getUserRole(UserContext.getUserId(), id);
        if (role == null) {
            return ResponseEntity.status(403).body(Map.of("error", "无权访问该项目"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", project.getId());
        result.put("name", project.getName());
        result.put("description", project.getDescription());
        result.put("tablePrefix", project.getTablePrefix());
        result.put("role", role);
        result.put("createdAt", project.getCreatedAt());
        result.put("updatedAt", project.getUpdatedAt());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public Project update(@PathVariable Long id, @Valid @RequestBody UpdateProjectRequest request) {
        projectService.requireRole(UserContext.getUserId(), id, RoleConstants.OWNER);
        return projectService.updateProject(id, request.getName(), request.getDescription(), request.getTablePrefix());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        projectService.requireRole(UserContext.getUserId(), id, RoleConstants.OWNER);
        projectService.deleteProject(id);
        return Map.of("success", true);
    }

    // ---- 成员管理 ----

    @PostMapping("/{id}/members")
    public Map<String, Object> addMember(@PathVariable Long id, @Valid @RequestBody AddMemberRequest request) {
        projectService.requireRole(UserContext.getUserId(), id, RoleConstants.OWNER);
        projectService.addMember(id, request.getUsername(), request.getRole());
        return Map.of("success", true);
    }

    @GetMapping("/{id}/members")
    public PageResponse<Map<String, Object>> listMembers(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        projectService.requireRole(UserContext.getUserId(), id, null); // 任意角色即可
        return projectService.listMembers(id, page, size);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Map<String, Object> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        projectService.requireRole(UserContext.getUserId(), id, RoleConstants.OWNER);
        projectService.removeMember(id, userId);
        return Map.of("success", true);
    }
}
