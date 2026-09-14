package com.comac.rpm.modules.change.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * ProjChange 实体（表 proj_change）
 */
@Data
@TableName("proj_change")
public class ProjChange {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 变更单号 */
    private String changeNo;

    /** 项目ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** PROJECT/DATA */
    private String changeType;

    /** 变更事项 */
    private String category;

    /** 变更标题 */
    private String title;

    /** 变更缘由 */
    private String reason;

    /** 调整前 */
    private String beforeValue;

    /** 调整后 */
    private String afterValue;

    /** 是否需法务审核 */
    private Integer legalReview;

    /** 状态 */
    private String status;

    /** 当前审批节点 */
    private String flowNode;

    /** 申请人 */
    private String applicant;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
