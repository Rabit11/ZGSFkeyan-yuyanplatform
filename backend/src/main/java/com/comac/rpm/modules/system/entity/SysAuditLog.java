package com.comac.rpm.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志
 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String userName;
    /** 业务模块 */
    private String module;
    /** CREATE/UPDATE/DELETE/SUBMIT/APPROVE/EXPORT */
    private String action;
    private String bizType;
    private Long bizId;
    private String content;
    private String ip;
    private LocalDateTime createdAt;
}
