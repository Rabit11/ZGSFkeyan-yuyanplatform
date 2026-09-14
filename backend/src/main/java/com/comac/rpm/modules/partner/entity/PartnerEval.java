package com.comac.rpm.modules.partner.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * PartnerEval 实体（表 partner_eval）
 */
@Data
@TableName("partner_eval")
public class PartnerEval {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 协作单位名称 */
    private String partnerName;

    /** LEAD/PARTNER/OUTSOURCE */
    private String partnerType;

    /** 技术能力 */
    private Integer techScore;

    /** 交付质量 */
    private Integer qualityScore;

    /** 进度履约 */
    private Integer progressScore;

    /** 服务配合 */
    private Integer serviceScore;

    /** 合规性 */
    private Integer complianceScore;

    /** 总分 */
    private Integer score;

    /** 等级 */
    private String grade;

    /** 评价日期 */
    private LocalDate evalDate;

    /** 评价人 */
    private String evaluator;

    /** 评价到期日 */
    private LocalDate dueDate;

    /** 状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
