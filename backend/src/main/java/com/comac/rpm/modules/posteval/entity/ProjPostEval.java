package com.comac.rpm.modules.posteval.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * ProjPostEval 实体（表 proj_post_eval）
 */
@Data
@TableName("proj_post_eval")
public class ProjPostEval {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** 到期日 */
    private LocalDate dueDate;

    /** 整体目标达成 */
    private String goalAchieve;

    /** 实施进度管控 */
    private String progressCtrl;

    /** 经费预算执行 */
    private String fundExec;

    /** 科研成果产出 */
    private String achievementOutput;

    /** 协作履约 */
    private String partnerPerform;

    /** 风险管控 */
    private String riskCtrl;

    /** 综合评分 */
    private Integer score;

    /** 评价结论 */
    private String conclusion;

    /** 状态 */
    private String status;

    /** 四色状态 */
    private String colorStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
