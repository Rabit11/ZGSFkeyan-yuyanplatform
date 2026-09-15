package com.comac.rpm.modules.project.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * ProjInfo 实体（表 proj_info）
 */
@Data
@TableName("proj_info")
public class ProjInfo {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 项目编号 */
    private String projectNo;

    /** 项目名称 */
    private String name;

    /** 项目整体目标 */
    private String goal;

    /** 开始时间 */
    private LocalDate startDate;

    /** 结束时间 */
    private LocalDate endDate;

    /** NATIONAL/LOCAL/COMPANY */
    private String levelCode;

    /** 立项部门 */
    private String filingDept;

    /** 渠道ID */
    private Long channelId;

    /** 渠道类别 */
    private String channelName;

    /** 牵头单位ID */
    private Long leadOrgId;

    /** 牵头单位 */
    private String leadOrgName;

    /** 牵头单位主要工作内容 */
    private String mainWork;

    /** 项目状态 */
    private String status;

    /** 成果转化状态 */
    private String transformStatus;

    /** 四色状态 */
    private String warnColor;

    /** PLATFORM平台同步 / FORM_MAINT表单维护导入 */
    private String dataSource;

    /** 总经费（万元） */
    private BigDecimal totalFund;

    /** 国拨经费（万元） */
    private BigDecimal nationalFund;

    /** 自筹经费（万元） */
    private BigDecimal selfFund;

    /** 历年支出 */
    private BigDecimal expenseTotal;

    /** 年度预算 */
    private BigDecimal yearBudget;

    /** 年度支出 */
    private BigDecimal yearExpense;

    /** 外协金额 */
    private BigDecimal outsourceAmount;

    /** 管理/需求单位 */
    private String manageOrgName;

    /** 司局/处室 */
    private String bureauOffice;

    /** 项目类型 */
    private String projectType;

    /** 一级专业 */
    private String major1;

    /** 二级专业 */
    private String major2;

    /** 项目负责人 */
    private String ownerName;

    /** 验收状态 */
    private String acceptStatus;

    /** 承担单位（数据权限） */
    private Long orgId;

    /** 承担单位名称 */
    private String orgName;

    /** 创建人 */
    private Long createBy;

    /** 创建人姓名 */
    private String createByName;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除 */
    private Integer deleted;

    @TableField(exist = false)
    /** 参研单位 */
    private List<ProjParticipant> participants;

    @TableField(exist = false)
    /** 项目团队 */
    private List<ProjTeamMember> teamMembers;

    @TableField(exist = false)
    /** 科研年度目标及计划 */
    private List<ProjAnnualPlan> annualPlans;

    /** 台账列表摘要：负责人（优先 ownerName） */
    @TableField(exist = false)
    private String leaderName;

    @TableField(exist = false)
    private Integer milestoneDone;

    @TableField(exist = false)
    private Integer milestoneTotal;

    @TableField(exist = false)
    private Integer milestonePercent;

    @TableField(exist = false)
    private Integer deliverableDone;

    @TableField(exist = false)
    private Integer deliverableTotal;

    @TableField(exist = false)
    private Integer partnerDone;

    @TableField(exist = false)
    private Integer partnerTotal;

    @TableField(exist = false)
    private Boolean hasBlacklist;

    @TableField(exist = false)
    private Integer transformDone;

    @TableField(exist = false)
    private Integer transformTotal;

    @TableField(exist = false)
    private com.comac.rpm.modules.milestone.entity.ProjMilestone nextMilestone;

    @TableField(exist = false)
    private String nextFallback;

    @TableField(exist = false)
    private Boolean canEdit;

    @TableField(exist = false)
    private Boolean canDelete;

    @TableField(exist = false)
    private java.util.Map<String,Object> supplement;

}
