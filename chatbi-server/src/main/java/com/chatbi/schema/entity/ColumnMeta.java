package com.chatbi.schema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("column_meta")
public class ColumnMeta {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tableId;

    private String columnName;

    private String columnType;

    private String columnComment;

    private Boolean isPrimaryKey;

    private Integer ordinal;
}
