package com.comac.rpm.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 数据字典
 */
@Data
@TableName("sys_dict")
public class SysDict implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 字典类型 */
    private String dictType;
    /** 字典编码 */
    private String dictCode;
    /** 字典名称 */
    private String dictName;
    /** 父级编码 */
    private String parentCode;
    private Integer sort;
    private Integer status;
    private String remark;
}
