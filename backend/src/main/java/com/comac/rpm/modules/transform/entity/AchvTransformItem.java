package com.comac.rpm.modules.transform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * AchvTransformItem 实体（表 achv_transform_item）
 */
@Data
@TableName("achv_transform_item")
public class AchvTransformItem {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 成果包ID */
    private Long transformId;

    /** 交付物ID */
    private Long deliverableId;

}
