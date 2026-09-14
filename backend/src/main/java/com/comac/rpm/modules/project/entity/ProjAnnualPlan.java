package com.comac.rpm.modules.project.entity;

import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * ProjAnnualPlan 实体（表 proj_annual_plan）
 */
@Data
@TableName("proj_annual_plan")
public class ProjAnnualPlan {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 年度 */
    private Integer year;

    /** 年度目标 */
    private String annualGoal;

    /** 计划内容 */
    private String planContent;

    /** 完成时间 */
    private LocalDate dueDate;

    /** 完成情况 */
    private String finishStatus;

    /** 四色状态 */
    private String colorStatus;

}
