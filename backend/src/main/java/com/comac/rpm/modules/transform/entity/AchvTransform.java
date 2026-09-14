package com.comac.rpm.modules.transform.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * AchvTransform 实体（表 achv_transform）
 */
@Data
@TableName("achv_transform")
public class AchvTransform {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 成果编号 */
    private String achievementNo;

    /** 成果名称 */
    private String name;

    /** 项目ID */
    private Long projectId;

    /** 项目编号 */
    private String projectNo;

    /** 成果简介 */
    private String intro;

    /** MODEL/MARKET */
    private String transformWay;

    /** 转化形式 */
    private String transformForm;

    /** 计划转化时间 */
    private LocalDate planDate;

    /** 实际转化时间 */
    private LocalDate actualDate;

    /** 转化状态 */
    private String status;

    /** 四色状态 */
    private String colorStatus;

    /** 转化简介 */
    private String introDetail;

    /** 责任单位 */
    private String dutyOrg;

    /** 关联交付物数量 */
    private Integer itemCount;

    /** 创建时间 */
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String projectName;

    /** 审核流程与业务转化状态独立。历史记录迁移为草稿，不能推断已审核。 */
    private String workflowStatus;
    /** 填报进度；status 保留供既有台账/看板聚合的已确认进度。 */
    private String reportedStatus;
    private LocalDate reportedActualDate;
    /** 历史来源待核对；不表示通过本模块的新审核流程。 */
    private Boolean legacyRecord;
    @TableField(exist = false)
    private String confirmedStatus;
    @TableField(exist = false)
    private LocalDate confirmedActualDate;
    /** 历史成果包所属项目已不存在，仅管理员可只读核对。 */
    @TableField(exist = false)
    private Boolean orphanedProject;
    private Long revision;
    private String evidenceJson;
    private String historyJson;

    @TableField(exist = false)
    private java.util.List<Long> deliverableIds;
    @TableField(exist = false)
    private java.util.List<com.comac.rpm.modules.deliverable.entity.ProjDeliverable> deliverables;
    @TableField(exist = false)
    private java.util.List<String> allowedActions;

}
