package com.comac.rpm.modules.change.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * ProjChange 实体（表 proj_change）
 */
@Data
@TableName("proj_change")
public class ProjChange {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 变更单号 */
    private String changeNo;

    /** 项目ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** PROJECT/DATA */
    private String changeType;

    /** 变更事项：MILESTONE_DELAY/FUND/PERIOD/OUTSOURCE/PAYMENT/INDICATOR/BASIC/LEVEL */
    private String category;

    /** 变更标题 */
    private String title;

    /** 变更缘由 */
    private String reason;

    /** 调整前 */
    private String beforeValue;

    /** 调整后 */
    private String afterValue;

    /** 延期变更对应里程碑 */
    private Long milestoneId;

    /** 延期变更新计划日期 */
    private LocalDate newPlanDate;

    /** 是否需法务审核 */
    private Integer legalReview;

    /** DRAFT/APPROVING/APPROVED/REJECTED */
    private String status;

    /** 当前审批节点 */
    private String flowNode;

    /** 申请人 */
    private String applicant;

    /** 最近审核人 */
    private String auditBy;

    /** 最近审核时间 */
    private LocalDateTime auditAt;

    /** 最近审核意见 */
    private String auditOpinion;

    /** 审批记录 JSON（列 audit_trail） */
    @JsonIgnore
    @TableField("audit_trail")
    private String auditTrailJson;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /** 审批记录（非表字段，由 auditTrailJson 解析） */
    @TableField(exist = false)
    private List<Map<String, Object>> auditTrail;
}
