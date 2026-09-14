package com.comac.rpm.modules.fund.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * HqFundTransfer 实体（表 hq_fund_transfer）
 */
@Data
@TableName("hq_fund_transfer")
public class HqFundTransfer {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 预算ID */
    private Long budgetId;

    /** 额度ID */
    private Long quotaId;

    /** 单位ID */
    private Long orgId;

    /** 单位名称 */
    private String orgName;

    /** 拨付金额 */
    private BigDecimal amount;

    /** 申请单号 */
    private String applyNo;

    /** 事由 */
    private String reason;

    /** 状态 */
    private String status;

    /** 申请时间 */
    private LocalDateTime applyAt;

    /** 审批时间 */
    private LocalDateTime approvedAt;

}
