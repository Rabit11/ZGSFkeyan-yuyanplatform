package com.comac.rpm.modules.declaration.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * ProjFiling 实体（表 proj_filing）
 */
@Data
@TableName("proj_filing")
public class ProjFiling {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 申报ID */
    private Long declarationId;

    /** 项目ID */
    private Long projectId;

    /** 备案编号 */
    private String filingNo;

    /** 备案日期 */
    private LocalDate filingDate;

    /** 备案部门 */
    private String filingDept;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
