package com.comac.rpm.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统备份快照元数据
 */
@Data
@TableName("sys_backup")
public class SysBackup implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String backupNo;
    /** MANUAL / SCHEDULED / PRE_RESTORE / PRE_RISK */
    private String triggerType;
    /** RUNNING / SUCCESS / FAILED */
    private String status;
    private String remark;
    private String filePath;
    private String objectKey;
    private Long fileSize;
    private Integer tableCount;
    private Integer rowCount;
    private Integer objectCount;
    private String checksum;
    private String errorMsg;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    private LocalDateTime restoredAt;
    private String restoredBy;
}
