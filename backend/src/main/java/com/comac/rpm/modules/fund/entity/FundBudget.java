package com.comac.rpm.modules.fund.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * FundBudget 实体（表 fund_budget）
 */
@Data
@TableName("fund_budget")
public class FundBudget {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 年度 */
    private Integer year;

    /** 里程碑ID */
    private Long milestoneId;

    /** 里程碑名称 */
    private String milestoneName;

    /** 预算金额 */
    private BigDecimal amount;

    /** 状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
