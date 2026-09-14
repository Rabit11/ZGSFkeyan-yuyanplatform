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

    /** 状态 DRAFT/SUBMITTED/UNIT_OK/DONE/RECTIFYING/REJECTED */
    private String status;

    /** 报告文件 */
    private String reportFile;

    /** 项目渠道编码（决定审批链差异，回显用） */
    private String channelCode;

    /** 评审结论 */
    private String conclusion;

    /** 整改说明 */
    private String rectifyNote;

    /** 整改是否完成：0否/1是 */
    private Integer rectifyDone;

    /** 提交人 */
    private String submitBy;

    /** 审核人（最近一次） */
    private String auditBy;

    /** 审核时间（最近一次） */
    private LocalDateTime auditAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
