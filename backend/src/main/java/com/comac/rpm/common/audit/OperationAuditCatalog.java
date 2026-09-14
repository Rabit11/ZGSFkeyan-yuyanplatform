package com.comac.rpm.common.audit;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 将 HTTP 路径/方法映射为审计模块、动作与中文说明。
 */
final class OperationAuditCatalog {

    private OperationAuditCatalog() {
    }

    static Resolved resolve(String method, String uri) {
        String path = uri == null ? "" : uri;
        String lower = path.toLowerCase(Locale.ROOT);
        String http = method == null ? "GET" : method.toUpperCase(Locale.ROOT);

        String module = moduleOf(lower);
        String action = actionOf(http, lower);
        String content = actionLabel(action) + moduleLabel(module);
        return new Resolved(module, action, content);
    }

    static String moduleOf(String lowerUri) {
        if (contains(lowerUri, "/backups")) {
            return "BACKUP";
        }
        if (contains(lowerUri, "/auth")) {
            return "AUTH";
        }
        if (contains(lowerUri, "/users")) {
            return "MEMBER";
        }
        if (contains(lowerUri, "/permission-matrix")) {
            return "PERMISSION";
        }
        if (contains(lowerUri, "/milestones")) {
            return "MILESTONE";
        }
        if (contains(lowerUri, "/declarations")) {
            return "DECLARATION";
        }
        if (contains(lowerUri, "/files")) {
            return "FILE";
        }
        if (contains(lowerUri, "/warnings")) {
            return "WARNING";
        }
        if (contains(lowerUri, "/post-evals")) {
            return "POST_EVAL";
        }
        if (contains(lowerUri, "/transforms")) {
            return "TRANSFORM";
        }
        if (contains(lowerUri, "/partner-evals") || contains(lowerUri, "/partners/")) {
            return "PARTNER";
        }
        if (contains(lowerUri, "/deliverables")) {
            return "DELIVERABLE";
        }
        if (contains(lowerUri, "/acceptance")) {
            return "ACCEPTANCE";
        }
        if (contains(lowerUri, "/changes")) {
            return "CHANGE";
        }
        if (contains(lowerUri, "/evaluations")) {
            return "EVALUATION";
        }
        if (contains(lowerUri, "/hq-fund")) {
            return "HQ_FUND";
        }
        if (contains(lowerUri, "/fund")) {
            return "FUND";
        }
        if (contains(lowerUri, "/plans")) {
            return "PLAN";
        }
        if (contains(lowerUri, "/projects")) {
            return "PROJECT";
        }
        if (contains(lowerUri, "/dict")) {
            return "DICT";
        }
        return "SYSTEM";
    }

    static String actionOf(String http, String lowerUri) {
        if (endsWith(lowerUri, "/login")) {
            return "LOGIN";
        }
        if (endsWith(lowerUri, "/logout")) {
            return "LOGOUT";
        }
        if (endsWith(lowerUri, "/submit") || endsWith(lowerUri, "/finish-apply")) {
            return "SUBMIT";
        }
        if (endsWith(lowerUri, "/audit") || endsWith(lowerUri, "/finish-audit")) {
            return "APPROVE";
        }
        if (endsWith(lowerUri, "/revoke")) {
            return "REVOKE";
        }
        if (endsWith(lowerUri, "/filing")) {
            return "FILING";
        }
        if (endsWith(lowerUri, "/close")) {
            return "CLOSE";
        }
        if (endsWith(lowerUri, "/delay")) {
            return "DELAY";
        }
        if (endsWith(lowerUri, "/writeoff")) {
            return "WRITEOFF";
        }
        if (endsWith(lowerUri, "/lock")) {
            return "LOCK";
        }
        if (endsWith(lowerUri, "/bind")) {
            return "BIND";
        }
        if (endsWith(lowerUri, "/reset-password")) {
            return "RESET_PWD";
        }
        if (endsWith(lowerUri, "/roles")) {
            return "ASSIGN_ROLE";
        }
        if (endsWith(lowerUri, "/finish-auth")) {
            return "AUTH_GRANT";
        }
        if (endsWith(lowerUri, "/materials")) {
            return "MATERIAL";
        }
        if (endsWith(lowerUri, "/annual-plan")) {
            return "UPDATE";
        }
        if (endsWith(lowerUri, "/refresh-status") || endsWith(lowerUri, "/refresh")) {
            return "REFRESH";
        }
        if (endsWith(lowerUri, "/scan")) {
            return "SCAN";
        }
        if (endsWith(lowerUri, "/read")) {
            return "READ";
        }
        if (endsWith(lowerUri, "/export")) {
            return "EXPORT";
        }
        if (endsWith(lowerUri, "/download")) {
            return "DOWNLOAD";
        }
        if (endsWith(lowerUri, "/file")) {
            return "DOWNLOAD";
        }
        if (endsWith(lowerUri, "/upload")) {
            return "UPLOAD";
        }
        if (endsWith(lowerUri, "/sync")) {
            return "SYNC";
        }
        if (endsWith(lowerUri, "/reset")) {
            return "RESET";
        }
        if (endsWith(lowerUri, "/restore")) {
            return "RESTORE";
        }
        if (endsWith(lowerUri, "/check")) {
            return "CHECK";
        }
        switch (http) {
            case "POST":
                return "CREATE";
            case "PUT":
            case "PATCH":
                return "UPDATE";
            case "DELETE":
                return "DELETE";
            default:
                return http;
        }
    }

    static String moduleLabel(String module) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("AUTH", "认证");
        map.put("MEMBER", "成员管理");
        map.put("PERMISSION", "权限矩阵");
        map.put("PROJECT", "项目台账");
        map.put("MILESTONE", "里程碑");
        map.put("DECLARATION", "申报立项");
        map.put("FILE", "附件");
        map.put("WARNING", "预警");
        map.put("POST_EVAL", "后评价");
        map.put("TRANSFORM", "成果转化");
        map.put("PARTNER", "合作评价");
        map.put("DELIVERABLE", "交付物");
        map.put("ACCEPTANCE", "验收");
        map.put("CHANGE", "变更");
        map.put("EVALUATION", "过程评价");
        map.put("HQ_FUND", "总部经费");
        map.put("FUND", "经费");
        map.put("PLAN", "计划");
        map.put("DICT", "字典");
        map.put("BACKUP", "备份回滚");
        map.put("SYSTEM", "系统");
        return map.getOrDefault(module, module);
    }

    static String actionLabel(String action) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("CREATE", "新增");
        map.put("UPDATE", "修改");
        map.put("DELETE", "删除");
        map.put("SUBMIT", "提交");
        map.put("APPROVE", "审批");
        map.put("EXPORT", "导出");
        map.put("LOGIN", "登录");
        map.put("LOGOUT", "退出登录");
        map.put("REVOKE", "撤回");
        map.put("FILING", "备案");
        map.put("CLOSE", "关闭");
        map.put("DELAY", "延期");
        map.put("WRITEOFF", "核销");
        map.put("LOCK", "锁定");
        map.put("BIND", "绑定");
        map.put("RESET_PWD", "重置密码");
        map.put("ASSIGN_ROLE", "分配角色");
        map.put("AUTH_GRANT", "授权");
        map.put("MATERIAL", "维护材料");
        map.put("REFRESH", "刷新状态");
        map.put("SCAN", "扫描");
        map.put("READ", "已读");
        map.put("DOWNLOAD", "下载");
        map.put("UPLOAD", "上传");
        map.put("SYNC", "同步");
        map.put("RESET", "重置");
        map.put("CHECK", "检查");
        map.put("BACKUP", "备份");
        map.put("RESTORE", "回滚");
        return map.getOrDefault(action, action);
    }

    private static boolean contains(String uri, String token) {
        return uri != null && uri.contains(token);
    }

    private static boolean endsWith(String uri, String suffix) {
        if (uri == null) {
            return false;
        }
        int q = uri.indexOf('?');
        String path = q >= 0 ? uri.substring(0, q) : uri;
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.endsWith(suffix);
    }

    static final class Resolved {
        final String module;
        final String action;
        final String contentPrefix;

        Resolved(String module, String action, String contentPrefix) {
            this.module = module;
            this.action = action;
            this.contentPrefix = contentPrefix;
        }
    }
}
