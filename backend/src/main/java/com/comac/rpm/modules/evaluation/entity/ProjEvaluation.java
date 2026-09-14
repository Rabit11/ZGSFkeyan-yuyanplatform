package com.comac.rpm.modules.evaluation.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * ProjEvaluation 实体（表 proj_evaluation）
 */
@Data
@TableName("proj_evaluation")
public class ProjEvaluation {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 评估类型 */
    private String evalType;

    /** 评估名称 */
    private String name;

    /** 到期日 */
    private LocalDate dueDate;

    /** PASS/FAIL */
    private String result;

    /** 状态 */
    private String status;

    /** 报告文件 */
    private String reportFile;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
