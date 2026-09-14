package com.comac.rpm.modules.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目渠道分类字典
 */
@Data
@TableName("proj_channel")
public class ProjChannel implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 渠道编码，全局唯一 */
    private String channelCode;
    /** 渠道名称（专项名称） */
    private String channelName;
    /** NATIONAL国家级/LOCAL地方级/COMPANY公司级 */
    private String levelCode;
    /** 渠道部委/委局 */
    private String channelDept;
    /** 渠道司局/处室 */
    private String channelOffice;
    /** 内部管理部门 */
    private String innerDept;
    /** 内部管理处室 */
    private String innerOffice;
    /** 全周期流程节点，→ 分隔 */
    private String flowNodes;
    /** 项目申报需提交材料 */
    private String declareMaterial;
    /** 项目立项需提交材料 */
    private String filingMaterial;
    private Integer status;
    private LocalDateTime createdAt;

    /** 流程节点数组（非表字段） */
    @TableField(exist = false)
    private List<String> flowNodeList = new ArrayList<>();
    /** 申报材料数组（非表字段） */
    @TableField(exist = false)
    private List<String> declareMaterialList = new ArrayList<>();
    /** 立项材料数组（非表字段） */
    @TableField(exist = false)
    private List<String> filingMaterialList = new ArrayList<>();
}
