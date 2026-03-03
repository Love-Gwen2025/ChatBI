package com.chatbi.project.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddMemberRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    private String role;
}
