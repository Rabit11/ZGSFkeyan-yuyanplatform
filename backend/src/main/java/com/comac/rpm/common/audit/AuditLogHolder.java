package com.comac.rpm.common.audit;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 当前请求是否已由业务代码手动写入审计日志，避免 AOP 再记一条重复记录。
 */
public final class AuditLogHolder {

    public static final String ATTR_WRITTEN = "RPM_AUDIT_WRITTEN";

    private AuditLogHolder() {
    }

    public static void markWritten() {
        ServletRequestAttributes attrs = current();
        if (attrs != null) {
            attrs.getRequest().setAttribute(ATTR_WRITTEN, Boolean.TRUE);
        }
    }

    public static boolean alreadyWritten() {
        ServletRequestAttributes attrs = current();
        return attrs != null && Boolean.TRUE.equals(attrs.getRequest().getAttribute(ATTR_WRITTEN));
    }

    private static ServletRequestAttributes current() {
        try {
            return (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        } catch (Exception ignored) {
            return null;
        }
    }
}
