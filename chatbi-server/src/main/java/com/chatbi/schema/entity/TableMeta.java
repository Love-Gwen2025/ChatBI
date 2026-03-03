package com.chatbi.schema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("table_meta")
public class TableMeta {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tableName;

    private String tableComment;

    private String schemaName;

    private Long projectId;

    private String schemaText;

    private LocalDateTime createdAt;
}
