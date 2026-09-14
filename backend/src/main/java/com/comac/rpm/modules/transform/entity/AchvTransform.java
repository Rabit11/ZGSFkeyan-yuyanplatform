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

}
