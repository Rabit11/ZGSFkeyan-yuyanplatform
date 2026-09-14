package com.comac.rpm.modules.declaration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 申报阶段指定的岗位人员；立项后原样同步到项目团队。 */
@Data
@TableName("proj_declaration_post")
public class ProjDeclarationPost {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long declarationId;
    private String groupCode;
    private String roleKey;
    private String roleCode;
    private String roleName;
    private String userName;
    private String employeeNo;
    private Integer sort;
}
