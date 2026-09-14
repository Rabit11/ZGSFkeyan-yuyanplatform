package com.comac.rpm.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.common.audit.AuditLogHolder;
import com.comac.rpm.modules.system.entity.SysAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 审计日志 Mapper
 * <p>
 * 提供 default 方法 write(...)，供各模块在关键动作（提交/审批/导出等）时快速留痕，
 * 也可由 {@code OperationAuditAspect} 自动调用。失败不影响主流程。
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {

    default void write(String module, String action, String bizType, Long bizId, String content) {
        write(module, action, bizType, bizId, content, null);
    }

    default void write(String module, String action, String bizType, Long bizId, String content, String fallbackUserName) {
        try {
            SysAuditLog log = new SysAuditLog();
            log.setUserId(UserContext.getUserId());
            String userName = UserContext.getUsername();
            if (userName == null || userName.isBlank()) {
                userName = fallbackUserName;
            }
            log.setUserName(userName);
            log.setModule(module);
            log.setAction(action);
            log.setBizType(bizType);
            log.setBizId(bizId);
            log.setContent(trimContent(content));
            log.setIp(currentIp());
            log.setCreatedAt(LocalDateTime.now());
            this.insert(log);
            AuditLogHolder.markWritten();
        } catch (Exception ignored) {
            // 审计日志失败不影响业务
        }
    }

    default String trimContent(String content) {
        if (content == null) {
            return null;
        }
        String t = content.trim();
        if (t.length() <= 1000) {
            return t;
        }
        return t.substring(0, 997) + "...";
    }

    default String currentIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String xff = request.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) {
                    return xff.split(",")[0].trim();
                }
                String realIp = request.getHeader("X-Real-IP");
                if (realIp != null && !realIp.isBlank()) {
                    return realIp.trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }
}
