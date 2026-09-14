package com.comac.rpm.modules.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.R;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.modules.system.entity.SysRole;
import com.comac.rpm.modules.system.entity.SysUser;
import com.comac.rpm.modules.system.entity.SysUserRole;
import com.comac.rpm.modules.system.mapper.SysAuditLogMapper;
import com.comac.rpm.modules.system.mapper.SysRoleMapper;
import com.comac.rpm.modules.system.mapper.SysUserMapper;
import com.comac.rpm.modules.system.mapper.SysUserRoleMapper;
import com.comac.rpm.modules.auth.dto.LoginRequest;
import com.comac.rpm.security.JwtUtils;
import com.comac.rpm.security.TokenSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证相关接口
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;
    @Autowired
    private SysRoleMapper sysRoleMapper;
    @Autowired
    private SysAuditLogMapper sysAuditLogMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private TokenSessionService tokenSessionService;

    /**
     * 登录：签发 JWT，并将 jti 写入 Redis 会话
     */
    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginRequest request) {
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            throw new BusinessException("用户名或密码不能为空");
        }
        String loginKey = request.getUsername().trim();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, loginKey));
        if (user == null) {
            // 兼容用工号登录（演示账号常填 100012 等）
            user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmployeeNo, loginKey));
        }
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已停用，请联系管理员");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        List<String> roleCodes = loadRoleCodes(user.getId());
        String roles = String.join(",", roleCodes);
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), roles, user.getOrgId());
        Map<String, String> claims = jwtUtils.parseToken(token);
        tokenSessionService.save(claims.get("jti"), user.getId());

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(update);

        UserContext.set(user.getId(), user.getUsername(), roleCodes, user.getOrgId());
        sysAuditLogMapper.write("AUTH", "LOGIN", "SYS_USER", user.getId(), "用户登录：" + user.getUsername());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("employeeNo", user.getEmployeeNo());
        data.put("realName", user.getRealName());
        data.put("roles", roleCodes);
        data.put("orgId", user.getOrgId());
        data.put("orgName", user.getOrgName());
        data.put("identity", user.getIdentity());
        data.put("identityCode", user.getIdentityCode());
        data.put("dataScope", user.getDataScope());
        data.put("finishAuth", user.getFinishAuth());
        data.put("formMaintScope", user.getFormMaintScope());
        data.put("declareResultAccess", user.getDeclareResultAccess());
        return R.ok(data);
    }

    /**
     * 当前用户信息
     */
    @GetMapping("/profile")
    public R<Map<String, Object>> profile() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("employeeNo", user.getEmployeeNo());
        data.put("orgId", user.getOrgId());
        data.put("orgName", user.getOrgName());
        data.put("email", user.getEmail());
        data.put("mobile", user.getMobile());
        data.put("roles", loadRoleCodes(user.getId()));
        data.put("identity", user.getIdentity());
        data.put("identityCode", user.getIdentityCode());
        data.put("dataScope", user.getDataScope());
        data.put("deptName", user.getDeptName());
        data.put("formMaintScope", user.getFormMaintScope());
        data.put("declareResultAccess", user.getDeclareResultAccess());
        data.put("finishAuth", user.getFinishAuth());
        return R.ok(data);
    }

    /**
     * 退出登录：删除 Redis 中的 Token 会话
     */
    @PostMapping("/logout")
    public R<?> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Map<String, String> claims = jwtUtils.parseToken(header.substring(7).trim());
                tokenSessionService.remove(claims.get("jti"));
            } catch (Exception ignored) {
                // token 已失效也视为退出成功
            }
        }
        sysAuditLogMapper.write("AUTH", "LOGOUT", "SYS_USER", UserContext.getUserId(), "用户退出登录");
        return R.ok();
    }

    private List<String> loadRoleCodes(Long userId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        List<String> codes = new ArrayList<>();
        for (SysUserRole ur : userRoles) {
            SysRole role = sysRoleMapper.selectById(ur.getRoleId());
            if (role != null && role.getRoleCode() != null) {
                codes.add(role.getRoleCode());
            }
        }
        return codes;
    }
}
