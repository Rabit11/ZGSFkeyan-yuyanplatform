package com.comac.rpm.common.enums;

/**
 * 四色状态：GREEN 绿 / BLUE 蓝 / YELLOW 黄 / RED 红
 */
public enum ColorStatusEnum {

    GREEN("GREEN", "绿"),
    BLUE("BLUE", "蓝"),
    YELLOW("YELLOW", "黄"),
    RED("RED", "红");

    private final String code;
    private final String label;

    ColorStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 严重程度权重，用于取"最严重颜色" */
    public int weight() {
        switch (this) {
            case RED:
                return 4;
            case YELLOW:
                return 3;
            case BLUE:
                return 2;
            case GREEN:
                return 1;
            default:
                return 0;
        }
    }

    public static ColorStatusEnum of(String code) {
        if (code == null) {
            return BLUE;
        }
        for (ColorStatusEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) {
                return e;
            }
        }
        return BLUE;
    }
}
