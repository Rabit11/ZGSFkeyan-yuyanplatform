package com.comac.rpm.modules.fund.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * HqFundBudget 实体（表 hq_fund_budget）
 */
@Data
@TableName("hq_fund_budget")
public class HqFundBudget {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 年度 */
    private Integer year;

    /** 年度总盘子 */
    private BigDecimal totalAmount;

    /** DRAFT/LOCKED */
    private String status;

    /** 审批状态 */
    private String approveStatus;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
