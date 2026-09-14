package com.comac.rpm.modules.fund.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * HqFundQuota 实体（表 hq_fund_quota）
 */
@Data
@TableName("hq_fund_quota")
public class HqFundQuota {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 预算ID */
    private Long budgetId;

    /** 单位ID */
    private Long orgId;

    /** 单位名称 */
    private String orgName;

    /** 额度上限 */
    private BigDecimal quotaAmount;

    /** 累计已拨付 */
    private BigDecimal usedAmount;

}
