package com.comac.rpm.modules.system.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任职身份 → 功能角色包 / 数据范围（对齐前端 IDENTITY_DEFS）
 */
public final class IdentityPermissionDefaults {

    public record Defaults(String identityCode, List<String> roles, String dataScope, String formMaintScope) {
    }

    private static final Map<String, Defaults> BY_LABEL = new LinkedHashMap<>();
    private static final Map<String, Defaults> BY_CODE = new LinkedHashMap<>();

    static {
        put("系统管理员", "admin", list("ADMIN"), "COMPANY", "hq");
        put("公司领导", "leader", list("MANAGEMENT"), "COMPANY", null);
        put("总部责任处室处长", "hqHead", list("MANAGEMENT"), "COMPANY", "hq");
        put("总部科研项目主管", "hqStaff", list("MANAGEMENT"), "COMPANY", null);
        put("单位科研管理部门负责人", "unitHead", list("MANAGEMENT"), "UNIT", null);
        put("单位项目主管", "unitStaff", list("MANAGEMENT"), "UNIT", null);
        put("项目承担部门负责人", "deptHead", list("MANAGEMENT"), "DEPT", null);
        put("项目负责人", "owner", list("PROJECT_TEAM"), "SELF", null);
        put("技术负责人", "techLead", list("PROJECT_TEAM"), "SELF", null);
        put("项目主管", "projectPm", list("PROJECT_TEAM"), "SELF", null);
        put("项目联系人", "contactLogin", list("PROJECT_TEAM"), "SELF", null);
        put("一级总师（公司级）", "chief1", list("CHIEF_ENGINEER"), "SELF", null);
        put("二级总师（单位级）", "chief2", list("CHIEF_ENGINEER"), "SELF", null);
        put("总部财务主管", "finHq", list("FINANCE"), "COMPANY", null);
        put("单位财务部长", "finHead", list("FINANCE"), "UNIT", null);
        put("单位财务主管", "finStaff", list("FINANCE"), "UNIT", null);
    }

    private IdentityPermissionDefaults() {
    }

    private static List<String> list(String... roles) {
        return Collections.unmodifiableList(Arrays.asList(roles));
    }

    private static void put(String label, String code, List<String> roles, String dataScope, String formMaintScope) {
        Defaults d = new Defaults(code, roles, dataScope, formMaintScope);
        BY_LABEL.put(label, d);
        BY_CODE.put(code, d);
    }

    public static Defaults resolve(String identityLabel, String identityCode) {
        if (identityLabel != null && !identityLabel.isBlank()) {
            Defaults d = BY_LABEL.get(identityLabel.trim());
            if (d != null) {
                return d;
            }
        }
        if (identityCode != null && !identityCode.isBlank()) {
            return BY_CODE.get(identityCode.trim());
        }
        return null;
    }
}
