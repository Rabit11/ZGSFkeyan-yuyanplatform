package com.comac.rpm.modules.plan.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * ProjPlan 实体（表 proj_plan）
 */
@Data
@TableName("proj_plan")
public class ProjPlan {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 数据来源 */
    private String source;

    /** 计划标题 */
    private String title;

    /** TODO/DONE */
    private String planType;

    /** 到期日 */
    private LocalDate dueDate;

    /** 完成日 */
    private LocalDate finishDate;

    /** 责任人 */
    private String owner;

    /** 状态 */
    private String status;

    /** 四色状态 */
    private String colorStatus;

    /** 办结申请状态 */
    private String applyStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
