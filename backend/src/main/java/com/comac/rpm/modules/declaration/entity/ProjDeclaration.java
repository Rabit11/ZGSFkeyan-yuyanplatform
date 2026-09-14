package com.comac.rpm.modules.declaration.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Map;

/**
 * ProjDeclaration 实体（表 proj_declaration）
 */
@Data
@TableName("proj_declaration")
public class ProjDeclaration {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 申报单号 */
    private String applyNo;

    /** 项目名称 */
    private String name;

    /** 渠道ID */
    private Long channelId;

    /** 渠道名称 */
    private String channelName;

    /** 层级 */
    private String levelCode;

    /** 是否需要审批：1 需审批 / 0 无需审批直接报备 */
    private Integer needApproval;

    /** 研究目标 */
    private String goal;

    /** 申报经费（万元） */
    private BigDecimal applyFund;

    /** 计划开始 */
    private LocalDate startDate;

    /** 计划结束 */
    private LocalDate endDate;

    /** 合作单位 */
    private String partnerOrgs;

    /** 一级专业（附件1） */
    private String major1;

    /** 二级专业（附件1） */
    private String major2;

    /** 需求单位 */
    private String demandOrg;

    /** 责任单位/牵头单位 */
    private String leadOrgName;

    /** 牵头单位主要工作内容 */
    private String leadWorkContent;

    /** 单位ID */
    private Long orgId;

    /** 单位名称 */
    private String orgName;

    /** 申请人ID */
    private Long applicantId;

    /** 申请人 */
    private String applicant;

    /** 申报时间 */
    private LocalDateTime applyAt;

    /** 状态 */
    private String status;

    /** 当前审批节点 */
    private String flowNode;

    /** 审批意见 */
    private String opinion;

    /** 备注 */
    private String remark;

    /**
     * 申报岗位人员（roleKey -> “姓名（工号）”）。
     * 实际存入 proj_declaration_post，避免与申报主表字段耦合。
     */
    @TableField(exist = false)
    private Map<String, String> posts;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

}
