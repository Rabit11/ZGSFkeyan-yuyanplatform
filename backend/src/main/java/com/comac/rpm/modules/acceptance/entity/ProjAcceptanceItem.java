package com.comac.rpm.modules.acceptance.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * ProjAcceptanceItem 实体（表 proj_acceptance_item）
 */
@Data
@TableName("proj_acceptance_item")
public class ProjAcceptanceItem {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 验收ID */
    private Long acceptanceId;

    /** 层级编码 */
    private String levelCode;

    /** 层级名称 */
    private String levelName;

    /** 栏位编码 */
    private String fieldCode;

    /** 材料名称 */
    private String materialName;

    /** 是否必需 */
    private Integer required;

    /** 是否锁定 */
    private Integer locked;

    /** 文件地址 */
    private String fileUrl;

    /** 文件名 */
    private String fileName;

    /** 文件大小 */
    private Long fileSize;

    /** 上传人 */
    private String uploadedBy;

    /** 上传时间 */
    private LocalDateTime uploadedAt;

    /** 状态 */
    private String status;

    /** 排序 */
    private Integer sort;

}
