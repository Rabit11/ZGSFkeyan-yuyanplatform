package com.comac.rpm.modules.milestone.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.comac.rpm.modules.declaration.entity.ProjMaterial;

/**
 * ProjMilestone 实体（表 proj_milestone）
 */
@Data
@TableName("proj_milestone")
public class ProjMilestone {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 年度 */
    private Integer year;

    /** 里程碑名称 */
    private String name;

    /** 计划完成时间 */
    private LocalDate planDate;

    /** 实际完成时间 */
    private LocalDate actualDate;

    /** 节点预算 */
    private BigDecimal budget;

    /** DOING/DONE/OVERDUE */
    private String status;

    /** 四色状态 */
    private String colorStatus;

    /** 是否已上传佐证材料 */
    private Integer evidence;

    /** 滞后原因 */
    private String lagReason;

    /** 当前里程碑节点已上传的真实材料（非表字段） */
    @TableField(exist = false)
    private List<ProjMaterial> materials;

    @TableField(exist = false)
    private String projectName;

    @TableField(exist = false)
    private String projectNo;

    @TableField(exist = false)
    private String ownerName;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
