package com.comac.rpm.modules.project.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 项目基本信息审批草稿（表 proj_basic_draft）
 * <p>
 * 项目团队补充的基本信息先进草稿，经 项目负责人 → 单位科技管理部 → 单位分管领导 → 总部科研项目处
 * 审批通过后才写入项目台账（proj_info / proj_participant / proj_team_member）。
 */
@Data
@TableName("proj_basic_draft")
public class ProjBasicDraft {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_APPROVING = "APPROVING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 待审批的基本信息快照 JSON */
    private String payload;

    /** DRAFT/APPROVING/APPROVED/REJECTED */
    private String status;

    /** 当前节点编码 PROJECT_LEADER/UNIT_TECH/UNIT_LEADER/HQ */
    private String flowNode;

    private String flowNodeName;

    private String submittedBy;

    private String submittedNo;

    private LocalDateTime submittedAt;

    /** 审批记录 JSON */
    private String auditTrail;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
