package com.comac.rpm.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目岗位办理权限矩阵行
 */
@Data
@TableName("sys_post_permission")
public class SysPostPermission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String postCode;
    private String permCode;
    private Integer enabled;
    private LocalDateTime updatedAt;
}
