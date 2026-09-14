package com.comac.rpm.modules.partner.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * PartnerBlacklist 实体（表 partner_blacklist）
 */
@Data
@TableName("partner_blacklist")
public class PartnerBlacklist {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 协作单位名称 */
    private String partnerName;

    /** 纳入原因 */
    private String reason;

    /** 佐证材料 */
    private String evidence;

    /** 纳入日期 */
    private LocalDate inDate;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
