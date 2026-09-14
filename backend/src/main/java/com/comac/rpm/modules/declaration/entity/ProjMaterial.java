package com.comac.rpm.modules.declaration.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * ProjMaterial 实体（表 proj_material）
 */
@Data
@TableName("proj_material")
public class ProjMaterial {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 业务类型 */
    private String bizType;

    /** 业务ID */
    private Long bizId;

    /** 附件栏编码 */
    private String fieldCode;

    /** 附件栏名称 */
    private String fieldName;

    /** 文件名 */
    private String fileName;

    /** 文件地址 */
    private String fileUrl;

    /** 文件大小 */
    private Long fileSize;

    /** 版本号 */
    private Integer version;

    /** 是否必需 */
    private Integer required;

    /** 是否锁定 */
    private Integer locked;

    /** 上传人 */
    private String uploadedBy;

    /** 上传时间 */
    private LocalDateTime uploadedAt;

}
