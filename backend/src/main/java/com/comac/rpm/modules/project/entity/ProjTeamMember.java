package com.comac.rpm.modules.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * ProjTeamMember 实体（表 proj_team_member）
 */
@Data
@TableName("proj_team_member")
public class ProjTeamMember {

    @TableId(value = "id", type = IdType.AUTO)
    /** 主键 */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** TECH/EXPERT/MGMT/FIN */
    private String groupCode;

    /** 角色编码 */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 姓名 */
    private String userName;

    /** 工号 */
    private String employeeNo;

    /** 排序 */
    private Integer sort;

}
