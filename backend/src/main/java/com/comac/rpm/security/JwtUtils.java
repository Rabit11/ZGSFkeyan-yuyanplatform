package com.comac.rpm.security;

import com.comac.rpm.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 工具类：零额外依赖，自实现 HS256（HmacSHA256）+ Base64URL。
 * payload 含 jti，配合 Redis 做会话存贮与登出失效。
 */
@Component
public class JwtUtils {

    private static final String ALG = "HmacSHA256";

    @Value("${rpm.jwt.secret}")
    private String secret;

    @Value("${rpm.jwt.expire-hours:12}")
    private long expireHours;

    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, "", null);
    }

    public String generateToken(Long userId, String username, String roles) {
        return generateToken(userId, username, roles, null);
    }

    /**
     * 生成 Token（含 jti，供 Redis 会话键使用）
     */
    public String generateToken(Long userId, String username, String roles, Long orgId) {
        long exp = System.currentTimeMillis() + expireHours * 3600L * 1000L;
        String jti = UUID.randomUUID().toString().replace("-", "");
        String payload = "{\"userId\":" + (userId == null ? 0 : userId)
                + ",\"username\":\"" + escape(username) + "\""
                + ",\"roles\":\"" + escape(roles) + "\""
                + ",\"orgId\":" + (orgId == null ? 0 : orgId)
                + ",\"jti\":\"" + jti + "\""
                + ",\"exp\":" + exp + "}";
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String signingInput = base64Url(header.getBytes(StandardCharsets.UTF_8))
                + "." + base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + hmacSha256(signingInput);
    }

    public long getExpireHours() {
        return expireHours;
    }

    /**
     * 解析 Token，返回 claims；签名错误或已过期抛出 BusinessException
     */
    public Map<String, String> parseToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new BusinessException(401, "Token 为空");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BusinessException(401, "Token 格式错误");
        }
        String signingInput = parts[0] + "." + parts[1];
        if (!hmacSha256(signingInput).equals(parts[2])) {
            throw new BusinessException(401, "Token 签名校验失败");
        }
        String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, String> claims = new HashMap<>();
        String body = json.trim();
        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1);
        }
        for (String pair : body.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                claims.put(unquote(kv[0]), unquote(kv[1]));
            }
        }
        long exp = 0L;
        try {
            exp = Long.parseLong(claims.getOrDefault("exp", "0"));
        } catch (NumberFormatException ignored) {
            exp = 0L;
        }
        if (exp < System.currentTimeMillis()) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        }
        return claims;
    }

    public boolean validate(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALG));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return base64Url(bytes);
        } catch (Exception e) {
            throw new BusinessException(500, "Token 签名计算失败：" + e.getMessage());
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "").replace("\"", "").replace(",", "").replace(":", "");
    }

    private static String unquote(String value) {
        String v = value.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v;
    }
}
