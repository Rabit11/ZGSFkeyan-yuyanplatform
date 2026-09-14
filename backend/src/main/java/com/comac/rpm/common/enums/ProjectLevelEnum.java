package com.comac.rpm.common.enums;

/**
 * 项目层级：NATIONAL 国家级 / LOCAL 地方级 / COMPANY 公司级
 */
public enum ProjectLevelEnum {

    NATIONAL("NATIONAL", "国家级"),
    LOCAL("LOCAL", "地方级"),
    COMPANY("COMPANY", "公司级");

    private final String code;
    private final String label;

    ProjectLevelEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static ProjectLevelEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (ProjectLevelEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) {
                return e;
            }
        }
        return null;
    }
}
