package com.comac.rpm.modules.milestone.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.comac.rpm.modules.declaration.entity.ProjMaterial;

/**
 * ProjMilestone 实体（表 proj_milestone）
 * <p>
 * 状态机：DOING → CLOSE_DEPT_AUDIT → CLOSE_UNIT_AUDIT → DONE；驳回回到 DOING。
 * OVERDUE 仅为展示态（RED 且未完成），不落库。
 */
@Data
@TableName("proj_milestone")
public class ProjMilestone {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 年度 */
    private Integer year;

    /** 里程碑名称 */
    private String name;

    /** 计划完成时间（延期只能通过项目变更回写） */
    private LocalDate planDate;

    /** 基线计划日期：年度清单审核存档时固化，之后 plan_date 只能走变更 */
    private LocalDate baselinePlanDate;

    /** 延期变更次数 */
    private Integer delayCount;

    /** 实际完成时间 */
    private LocalDate actualDate;

    /** 节点预算 */
    private BigDecimal budget;

    /** DOING/CLOSE_DEPT_AUDIT/CLOSE_UNIT_AUDIT/DONE */
    private String status;

    /** 四色状态 */
    private String colorStatus;

    /** 是否已上传佐证材料 */
    private Integer evidence;

    /** 滞后原因（逾期节点销项必填） */
    private String lagReason;

    /** 滞后处理措施 */
    private String lagMeasure;

    /** 最近审核人 */
    private String auditBy;

    /** 最近审核时间 */
    private LocalDateTime auditAt;

    /** 最近审核意见 */
    private String auditOpinion;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    /** 当前里程碑节点已上传的真实材料（非表字段） */
    @TableField(exist = false)
    private List<ProjMaterial> materials;

    /** 计划日期是否已锁定（进入基线或已逾期），只能通过变更调整（非表字段） */
    @TableField(exist = false)
    private Boolean dateLocked;

    /** 是否允许删除：DOING、无佐证、未进基线（非表字段） */
    @TableField(exist = false)
    private Boolean canDelete;

    @TableField(exist = false)
    private String projectName;

    @TableField(exist = false)
    private String projectNo;

    @TableField(exist = false)
    private String ownerName;
}
