package com.comac.rpm.common.enums;

/**
 * 角色编码
 */
public enum RoleCodeEnum {

    PROJECT_TEAM("PROJECT_TEAM", "项目团队"),
    CHIEF_ENGINEER("CHIEF_ENGINEER", "责任总师"),
    MANAGEMENT("MANAGEMENT", "管理团队"),
    FINANCE("FINANCE", "财务团队"),
    ADMIN("ADMIN", "超级管理员");

    private final String code;
    private final String label;

    RoleCodeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
