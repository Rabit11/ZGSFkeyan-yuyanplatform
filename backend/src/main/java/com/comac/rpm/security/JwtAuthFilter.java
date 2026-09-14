package com.comac.rpm.security;

import com.comac.rpm.common.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * JWT + Redis 会话校验：签名通过且 Redis 中存在 jti 才视为已登录。
 * 携带无效/失效 Token 时直接返回 401，避免被 Spring 转成 403 造成前端误判。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final TokenSessionService tokenSessionService;

    public JwtAuthFilter(JwtUtils jwtUtils, TokenSessionService tokenSessionService) {
        this.jwtUtils = jwtUtils;
        this.tokenSessionService = tokenSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        boolean isPublicAuth = path.startsWith("/api/auth/login")
                || path.startsWith("/api/health")
                || path.startsWith("/actuator");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                Map<String, String> claims = jwtUtils.parseToken(token);
                String jti = claims.getOrDefault("jti", "");
                if (!tokenSessionService.exists(jti)) {
                    if (!isPublicAuth) {
                        writeUnauthorized(response, "登录已失效，请重新登录");
                        return;
                    }
                } else {
                    Long userId = Long.valueOf(claims.getOrDefault("userId", "0"));
                    String username = claims.getOrDefault("username", "");
                    Long orgId = Long.valueOf(claims.getOrDefault("orgId", "0"));
                    String roles = claims.getOrDefault("roles", "");
                    List<String> roleList = new ArrayList<>();
                    if (!roles.isEmpty()) {
                        roleList.addAll(Arrays.asList(roles.split(",")));
                    }
                    UserContext.set(userId, username, roleList, orgId);

                    List<GrantedAuthority> authorities = new ArrayList<>();
                    for (String role : roleList) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    }
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    tokenSessionService.refreshTtl(jti);
                }
            } catch (Exception ex) {
                UserContext.clear();
                SecurityContextHolder.clearContext();
                if (!isPublicAuth) {
                    String msg = ex.getMessage() == null || ex.getMessage().isBlank()
                            ? "无效的令牌或登录已失效，请重新登录后再试"
                            : ex.getMessage();
                    writeUnauthorized(response, msg);
                    return;
                }
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        String safe = msg.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"code\":401,\"msg\":\"" + safe + "\"}");
    }
}
