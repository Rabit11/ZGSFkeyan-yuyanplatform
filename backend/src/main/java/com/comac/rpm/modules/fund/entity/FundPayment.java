package com.comac.rpm.modules.fund.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * FundPayment 实体（表 fund_payment）
 */
@Data
@TableName("fund_payment")
public class FundPayment {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 预算ID */
    private Long budgetId;

    /** PAY/EXPENSE/WRITEOFF */
    private String flowType;

    /** 金额 */
    private BigDecimal amount;

    /** 凭证号 */
    private String voucherNo;

    /** 发生日期 */
    private LocalDate occurDate;

    /** 核销状态 */
    private String writeoffStatus;

    /** 经办人 */
    private String operator;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
