package com.comac.rpm.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 当前登录用户上下文（ThreadLocal）
 */
public final class UserContext {

    private UserContext() {
    }

    private static final ThreadLocal<Info> TL = new ThreadLocal<>();

    public static void set(Long userId, String username, List<String> roles, Long orgId) {
        Info info = new Info();
        info.userId = userId;
        info.username = username;
        info.roles = roles == null ? new ArrayList<>() : roles;
        info.orgId = orgId;
        TL.set(info);
    }

    public static void clear() {
        TL.remove();
    }

    private static Info current() {
        Info info = TL.get();
        return info == null ? new Info() : info;
    }

    public static Long getUserId() {
        return current().userId;
    }

    public static String getUsername() {
        return current().username;
    }

    public static Long getOrgId() {
        return current().orgId;
    }

    public static List<String> getRoles() {
        Info info = current();
        return info.roles == null ? Collections.emptyList() : info.roles;
    }

    public static boolean hasRole(String roleCode) {
        return roleCode != null && getRoles().contains(roleCode);
    }

    /** 超级管理员 */
    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * 是否可查看全量数据（总部管理团队 / 超级管理员）
     */
    public static boolean isHeadquarter() {
        return isAdmin() || hasRole("MANAGEMENT");
    }

    private static class Info {
        private Long userId;
        private String username;
        private List<String> roles = new ArrayList<>();
        private Long orgId;
    }
}
