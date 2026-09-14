package com.comac.rpm.modules.system.entity;

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
 * 用户 / 成员（含任职身份与专项授权）
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String employeeNo;
    private Long orgId;
    private String orgName;
    private String deptName;
    private String email;
    private String mobile;
    /** 登录任职身份标签 */
    private String identity;
    private String identityCode;
    private String projectPost;
    private String rankTitle;
    /** COMPANY/UNIT/DEPT/PROJECT/SELF/SELECTED */
    private String dataScope;
    private Integer finishAuth;
    private String formMaintScope;
    private String formMaintChannels;
    private String formMaintTypes;
    private Integer declareResultAccess;
    /** 1在岗 0已离岗 */
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<String> roles = new ArrayList<>();
    @TableField(exist = false)
    private List<Long> roleIds = new ArrayList<>();
    /** 前端兼容字段 */
    @TableField(exist = false)
    private String phone;
}
