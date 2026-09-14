package com.comac.rpm.modules.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * ProjParticipant 实体（表 proj_participant）
 */
@Data
@TableName("proj_participant")
public class ProjParticipant {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 参研单位名称 */
    private String orgName;

    /** 主要工作内容 */
    private String workContent;

    /** 排序 */
    private Integer sort;

}
