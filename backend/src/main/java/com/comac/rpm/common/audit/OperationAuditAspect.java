package com.comac.rpm.common.audit;

import com.comac.rpm.common.R;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.modules.auth.dto.LoginRequest;
import com.comac.rpm.modules.system.mapper.SysAuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 全局操作审计：拦截全部 RestController。
 * <ul>
 *   <li>应用日志：除健康检查外的每一次接口调用都打印操作人、方法、路径、成败与耗时</li>
 *   <li>审计表：所有写操作（POST/PUT/PATCH/DELETE）以及导出/下载写入 sys_audit_log</li>
 * </ul>
 * 业务代码若已调用 {@code SysAuditLogMapper.write}，本切面不再重复写入审计表。
 * 审计失败不影响主流程。
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class OperationAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationAuditAspect.class);

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "oldpassword", "newpassword", "token", "accessToken", "refreshToken");

    private static final String[] NAME_GETTERS = {
            "getName", "getProjectName", "getTitle", "getRealName", "getUsername",
            "getProjectNo", "getFileName", "getOriginalFilename", "getObjectKey"
    };

    private final SysAuditLogMapper auditLogMapper;

    public OperationAuditAspect(SysAuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request == null || isNoise(request)) {
            return pjp.proceed();
        }
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            boolean failed = isFailedResult(result);
            accessLog(request, !failed, failed ? failMessage(result) : null, System.currentTimeMillis() - start);
            if (shouldPersist(request) && !AuditLogHolder.alreadyWritten()) {
                writeLog(request, pjp.getArgs(), failed, failed ? failMessage(result) : null);
            }
            return result;
        } catch (Throwable ex) {
            accessLog(request, false, ex.getMessage(), System.currentTimeMillis() - start);
            if (shouldPersist(request) && !AuditLogHolder.alreadyWritten()) {
                writeLog(request, pjp.getArgs(), true, ex.getMessage());
            }
            throw ex;
        }
    }

    private void accessLog(HttpServletRequest request, boolean ok, String error, long costMs) {
        try {
            String user = UserContext.getUsername();
            if (user == null || user.isBlank()) {
                user = "-";
            }
            if (ok) {
                log.info("操作日志 user={} {} {} 成功 {}ms", user, request.getMethod(), request.getRequestURI(), costMs);
            } else {
                log.warn("操作日志 user={} {} {} 失败 {}ms reason={}",
                        user, request.getMethod(), request.getRequestURI(), costMs, error);
            }
        } catch (Exception ignored) {
            // ignore
        }
    }

    private void writeLog(HttpServletRequest request, Object[] args, boolean failed, String error) {
        try {
            String uri = request.getRequestURI();
            OperationAuditCatalog.Resolved resolved = OperationAuditCatalog.resolve(request.getMethod(), uri);
            Long bizId = extractBizId(request, args);
            String summary = extractSummary(args, request);
            StringBuilder content = new StringBuilder();
            if (failed) {
                content.append("失败：");
            }
            content.append(resolved.contentPrefix);
            if (summary != null && !summary.isEmpty()) {
                content.append("，").append(summary);
            }
            if (bizId != null) {
                content.append("，ID=").append(bizId);
            }
            if (failed && error != null && !error.isBlank()) {
                content.append("，原因：").append(error);
            }
            String fallbackUser = extractLoginUsername(args);
            auditLogMapper.write(resolved.module, resolved.action, resolved.module, bizId,
                    content.toString(), fallbackUser);
        } catch (Exception ignored) {
            // 审计失败不影响业务
        }
    }

    private boolean isNoise(HttpServletRequest request) {
        String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase(Locale.ROOT);
        if ("OPTIONS".equals(method) || "HEAD".equals(method) || "TRACE".equals(method)) {
            return true;
        }
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI().toLowerCase(Locale.ROOT);
        return uri.startsWith("/api/health")
                || uri.startsWith("/swagger")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/actuator");
    }

    /** 写入审计表：所有写操作 + 导出/下载；查询类接口只打应用日志，避免刷屏。 */
    private boolean shouldPersist(HttpServletRequest request) {
        String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase(Locale.ROOT);
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI().toLowerCase(Locale.ROOT);
        if (uri.startsWith("/api/audit-logs")) {
            return false;
        }
        if ("GET".equals(method)) {
            return uri.endsWith("/export") || uri.endsWith("/download") || uri.matches(".*/backups/[0-9]+/file");
        }
        return true;
    }

    private boolean isFailedResult(Object result) {
        if (result instanceof R<?> r) {
            return r.getCode() != 0;
        }
        return false;
    }

    private String failMessage(Object result) {
        if (result instanceof R<?> r) {
            return r.getMsg();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Long extractBizId(HttpServletRequest request, Object[] args) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars instanceof Map<?, ?> map) {
            Long id = parseLong(map.get("id"));
            if (id != null) {
                return id;
            }
            id = parseLong(map.get("projectId"));
            if (id != null) {
                return id;
            }
            for (Object value : map.values()) {
                Long parsed = parseLong(value);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        if (args != null) {
            for (Object arg : args) {
                Long id = readId(arg);
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }

    private String extractSummary(Object[] args, HttpServletRequest request) {
        if (args == null) {
            return queryHint(request);
        }
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg == null || arg instanceof HttpServletRequest) {
                continue;
            }
            if (arg instanceof LoginRequest login) {
                append(sb, "账号=" + login.getUsername());
                continue;
            }
            if (arg instanceof MultipartFile file) {
                append(sb, "文件=" + file.getOriginalFilename());
                continue;
            }
            if (arg instanceof Collection<?> col) {
                append(sb, "数据=" + col);
                continue;
            }
            if (arg instanceof Map<?, ?> map) {
                append(sb, mapHint(map));
                continue;
            }
            String named = beanName(arg);
            if (named != null) {
                append(sb, named);
            }
        }
        String hint = queryHint(request);
        if (hint != null) {
            append(sb, hint);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String extractLoginUsername(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof LoginRequest login && login.getUsername() != null) {
                return login.getUsername();
            }
        }
        return UserContext.getUsername();
    }

    private String queryHint(HttpServletRequest request) {
        String objectKey = request.getParameter("objectKey");
        if (objectKey != null && !objectKey.isBlank()) {
            return "objectKey=" + objectKey;
        }
        String bizType = request.getParameter("bizType");
        if (bizType != null && !bizType.isBlank()) {
            return "bizType=" + bizType;
        }
        return null;
    }

    private String mapHint(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String key = String.valueOf(e.getKey());
            if (SENSITIVE_KEYS.contains(key) || SENSITIVE_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (n >= 4) {
                break;
            }
            if (sb.length() > 0) {
                sb.append("，");
            }
            sb.append(key).append("=").append(shorten(String.valueOf(e.getValue()), 40));
            n++;
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String beanName(Object arg) {
        Class<?> type = arg.getClass();
        if (type.isPrimitive() || type.getName().startsWith("java.")) {
            return null;
        }
        for (String getter : NAME_GETTERS) {
            try {
                Method m = type.getMethod(getter);
                Object val = m.invoke(arg);
                if (val != null && !String.valueOf(val).isBlank()) {
                    return String.valueOf(val);
                }
            } catch (Exception ignored) {
                // try next
            }
        }
        return null;
    }

    private Long readId(Object arg) {
        if (arg == null) {
            return parseLong(null);
        }
        Long direct = parseLong(arg);
        if (direct != null) {
            return direct;
        }
        try {
            Method m = arg.getClass().getMethod("getId");
            return parseLong(m.invoke(arg));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            String s = String.valueOf(value).trim();
            if (s.isEmpty()) {
                return null;
            }
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void append(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("，");
        }
        sb.append(part);
    }

    private String shorten(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception ignored) {
            return null;
        }
    }
}
