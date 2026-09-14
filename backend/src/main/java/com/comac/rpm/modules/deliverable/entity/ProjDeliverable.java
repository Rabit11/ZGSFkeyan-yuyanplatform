package com.comac.rpm.modules.deliverable.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * ProjDeliverable 实体（表 proj_deliverable）
 */
@Data
@TableName("proj_deliverable")
public class ProjDeliverable {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 对应里程碑节点 */
    private Long milestoneId;

    /** 交付物名称 */
    private String name;

    /** 交付物类型 */
    private String deliverType;

    /** 应交付时间 */
    private LocalDate dueDate;

    /** 实际交付时间 */
    private LocalDate deliverDate;

    /** PENDING/DELIVERED/OVERDUE */
    private String status;

    /** 四色状态 */
    private String colorStatus;

    /** 权属 */
    private String ownerOrgs;

    /** 关联成果编号 */
    private String achievementNo;

    /** 清单附件文件名（编制期可选；销项证明也可复用） */
    private String fileName;

    /** 清单附件地址 */
    private String fileUrl;

    /** 创建时间 */
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String projectName;

    @TableField(exist = false)
    private String projectNo;

}
