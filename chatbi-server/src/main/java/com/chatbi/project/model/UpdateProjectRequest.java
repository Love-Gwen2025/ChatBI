package com.chatbi.project.model;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProjectRequest {

    @Size(max = 128, message = "项目名称不能超过 128 个字符")
    private String name;

    @Size(max = 512, message = "描述不能超过 512 个字符")
    private String description;

    @Size(max = 64, message = "表前缀不能超过 64 个字符")
    private String tablePrefix;
}
