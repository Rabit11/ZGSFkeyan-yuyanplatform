package com.comac.rpm.modules.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.PageVO;
import com.comac.rpm.common.R;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.modules.system.entity.SysRole;
import com.comac.rpm.modules.system.entity.SysUser;
import com.comac.rpm.modules.system.entity.SysUserRole;
import com.comac.rpm.modules.system.mapper.SysAuditLogMapper;
import com.comac.rpm.modules.system.mapper.SysRoleMapper;
import com.comac.rpm.modules.system.mapper.SysUserMapper;
import com.comac.rpm.modules.system.mapper.SysUserRoleMapper;
import com.comac.rpm.modules.system.service.IdentityPermissionDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成员管理：任职身份、数据范围、专项授权（仅系统管理员可写）
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private SysUserRoleMapper userRoleMapper;
    @Autowired
    private SysRoleMapper roleMapper;
    @Autowired
    private SysAuditLogMapper auditLogMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 申报岗位候选人目录：登录用户可读，只返回岗位匹配所需的非敏感字段。
     * 成员维护、角色和专项授权仍由管理员接口控制。
     */
    @GetMapping("/candidates")
    public R<List<Map<String, Object>>> candidates() {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUser user : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("realName", user.getRealName());
            row.put("employeeNo", user.getEmployeeNo());
            row.put("orgId", user.getOrgId());
            row.put("orgName", user.getOrgName());
            row.put("deptName", user.getDeptName());
            row.put("identity", user.getIdentity());
            row.put("identityCode", user.getIdentityCode());
            row.put("projectPost", user.getProjectPost());
            result.add(row);
        }
        return R.ok(result);
    }

    @GetMapping
    public R<PageVO<SysUser>> page(@RequestParam(value = "page", defaultValue = "1") long page,
                                   @RequestParam(value = "size", defaultValue = "10") long size,
                                   @RequestParam(value = "keyword", required = false) String keyword,
                                   @RequestParam(value = "identity", required = false) String identity,
                                   @RequestParam(value = "dataScope", required = false) String dataScope,
                                   @RequestParam(value = "status", required = false) Integer status) {
        requireAdmin();
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            w.and(x -> x.like(SysUser::getUsername, kw)
                    .or().like(SysUser::getRealName, kw)
                    .or().like(SysUser::getEmployeeNo, kw)
                    .or().like(SysUser::getOrgName, kw));
        }
        if (identity != null && !identity.trim().isEmpty()) {
            w.eq(SysUser::getIdentity, identity.trim());
        }
        if (dataScope != null && !dataScope.trim().isEmpty()) {
            w.eq(SysUser::getDataScope, dataScope.trim());
        }
        if (status != null) {
            w.eq(SysUser::getStatus, status);
        }
        w.orderByAsc(SysUser::getId);
        Page<SysUser> p = userMapper.selectPage(new Page<>(page, size), w);
        for (SysUser u : p.getRecords()) {
            enrich(u);
        }
        return R.ok(PageVO.of(p));
    }

    @PostMapping
    public R<Long> create(@RequestBody SysUser body) {
        requireAdmin();
        body.setId(null);
        normalizePhone(body);
        if (body.getUsername() == null || body.getUsername().isBlank()) {
            body.setUsername(body.getEmployeeNo());
        }
        applyIdentityDefaults(body, true);
        if (body.getIdentity() == null || body.getIdentity().isBlank()) {
            throw new BusinessException("请选择任职身份，以便自动分配功能权限");
        }
        if (body.getPassword() == null || body.getPassword().isEmpty()) {
            String raw = body.getEmployeeNo() != null && !body.getEmployeeNo().isBlank()
                    ? body.getEmployeeNo().trim()
                    : body.getUsername();
            body.setPassword(encoder.encode(raw));
        } else {
            body.setPassword(encoder.encode(body.getPassword()));
        }
        if (body.getStatus() == null) {
            body.setStatus(1);
        }
        if (body.getDataScope() == null || body.getDataScope().isBlank()) {
            body.setDataScope("SELF");
        }
        if (body.getFinishAuth() == null) {
            body.setFinishAuth(0);
        }
        if (body.getDeclareResultAccess() == null) {
            body.setDeclareResultAccess(0);
        }
        forceAdminSpecialAuth(body);
        userMapper.insert(body);
        List<String> roles = resolveRoleCodes(body);
        if (roles.isEmpty()) {
            throw new BusinessException("未能根据任职身份推导功能角色，请检查任职身份配置");
        }
        bindRoles(body.getId(), roles);
        auditLogMapper.write("MEMBER", "CREATE", "SYS_USER", body.getId(),
                "新增成员：" + body.getRealName() + "/" + body.getEmployeeNo() + "，角色=" + roles);
        return R.ok(body.getId());
    }

    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody SysUser body) {
        requireAdmin();
        SysUser existing = userMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("成员不存在");
        }
        // 当前管理员不能把自己设为已离岗
        if (id.equals(UserContext.getUserId()) && body.getStatus() != null && body.getStatus() == 0) {
            throw new BusinessException("当前管理员不能把自己设置为已离岗");
        }
        body.setId(id);
        body.setPassword(null);
        normalizePhone(body);
        applyIdentityDefaults(body, false);
        forceAdminSpecialAuth(body, existing);
        userMapper.updateById(body);
        List<String> roles = resolveRoleCodes(body);
        if (!roles.isEmpty()) {
            bindRoles(id, roles);
        }
        auditLogMapper.write("MEMBER", "UPDATE", "SYS_USER", id,
                "更新成员：" + (body.getRealName() != null ? body.getRealName() : existing.getRealName()));
        return R.ok(true);
    }

    /**
     * 按任职身份补齐存量成员的功能角色（无角色或角色与身份不一致时重绑）
     */
    @PostMapping("/sync-identity-roles")
    public R<Map<String, Object>> syncIdentityRoles() {
        requireAdmin();
        List<SysUser> all = userMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getId));
        int fixed = 0;
        List<String> details = new ArrayList<>();
        for (SysUser u : all) {
            IdentityPermissionDefaults.Defaults def =
                    IdentityPermissionDefaults.resolve(u.getIdentity(), u.getIdentityCode());
            if (def == null) {
                continue;
            }
            boolean needPatch = false;
            SysUser patch = new SysUser();
            patch.setId(u.getId());
            if (u.getIdentityCode() == null || u.getIdentityCode().isBlank()) {
                patch.setIdentityCode(def.identityCode());
                needPatch = true;
            }
            if (u.getDataScope() == null || u.getDataScope().isBlank()) {
                patch.setDataScope(def.dataScope());
                needPatch = true;
            }
            if (def.formMaintScope() != null
                    && (u.getFormMaintScope() == null || u.getFormMaintScope().isBlank())) {
                patch.setFormMaintScope(def.formMaintScope());
                needPatch = true;
            }
            if ("admin".equals(def.identityCode())) {
                patch.setDeclareResultAccess(1);
                patch.setFinishAuth(1);
                needPatch = true;
            }
            if (needPatch) {
                userMapper.updateById(patch);
            }
            List<String> current = roleCodesOf(u.getId());
            List<String> expected = new ArrayList<>(def.roles());
            boolean same = current.size() == expected.size() && current.containsAll(expected);
            if (!same) {
                bindRoles(u.getId(), expected);
                fixed++;
                details.add(u.getEmployeeNo() + " " + u.getRealName() + " → " + expected);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", all.size());
        out.put("fixed", fixed);
        out.put("details", details);
        auditLogMapper.write("MEMBER", "SYNC_ROLES", "SYS_USER", null,
                "按任职身份同步功能角色，修复 " + fixed + " 人");
        return R.ok(out);
    }

    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable("id") Long id) {
        requireAdmin();
        if (id.equals(UserContext.getUserId())) {
            throw new BusinessException("当前管理员不能删除自己");
        }
        SysUser target = userMapper.selectById(id);
        if (target == null) {
            return R.ok(true);
        }
        List<String> roles = roleCodesOf(id);
        if (roles.contains("ADMIN") && countAdmins() <= 1) {
            throw new BusinessException("系统中唯一管理员不能被删除");
        }
        userMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        auditLogMapper.write("MEMBER", "DELETE", "SYS_USER", id,
                "删除成员：" + target.getRealName() + "/" + target.getEmployeeNo());
        return R.ok(true);
    }

    @PostMapping("/{id}/roles")
    public R<Boolean> assignRoles(@PathVariable("id") Long id, @RequestBody List<String> roleCodes) {
        requireAdmin();
        SysUser target = userMapper.selectById(id);
        if (target == null) {
            throw new BusinessException("成员不存在");
        }
        List<String> current = roleCodesOf(id);
        boolean removingAdmin = current.contains("ADMIN")
                && (roleCodes == null || !roleCodes.contains("ADMIN"));
        if (removingAdmin && countAdmins() <= 1) {
            throw new BusinessException("系统中唯一管理员不能被取消管理员身份");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        if (roleCodes != null) {
            for (String code : roleCodes) {
                SysRole role = roleMapper.selectOne(
                        new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, code).last("LIMIT 1"));
                if (role == null) {
                    continue;
                }
                SysUserRole ur = new SysUserRole();
                ur.setUserId(id);
                ur.setRoleId(role.getId());
                userRoleMapper.insert(ur);
            }
        }
        // 管理员强制具备立项审批结果权限
        if (roleCodes != null && roleCodes.contains("ADMIN")) {
            SysUser patch = new SysUser();
            patch.setId(id);
            patch.setDeclareResultAccess(1);
            if (target.getFormMaintScope() == null || target.getFormMaintScope().isEmpty()) {
                patch.setFormMaintScope("hq");
            }
            userMapper.updateById(patch);
        }
        auditLogMapper.write("MEMBER", "ASSIGN_ROLE", "SYS_USER", id, "分配角色：" + roleCodes);
        return R.ok(true);
    }

    @PostMapping("/{id}/reset-password")
    public R<Boolean> resetPassword(@PathVariable("id") Long id) {
        requireAdmin();
        SysUser target = userMapper.selectById(id);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        String raw = target.getEmployeeNo() != null && !target.getEmployeeNo().isBlank()
                ? target.getEmployeeNo().trim()
                : target.getUsername();
        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setPassword(encoder.encode(raw));
        userMapper.updateById(patch);
        auditLogMapper.write("MEMBER", "RESET_PWD", "SYS_USER", id, "重置密码为工号");
        return R.ok(true);
    }

    @PostMapping("/{id}/finish-auth")
    public R<Boolean> setFinishAuth(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        requireAdmin();
        boolean enabled = body != null && Boolean.TRUE.equals(body.get("enabled"));
        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setFinishAuth(enabled ? 1 : 0);
        userMapper.updateById(patch);
        auditLogMapper.write("MEMBER", "AUTH_GRANT", "SYS_USER", id,
                (enabled ? "开通" : "取消") + "结题授权：" + (targetName(id)));
        return R.ok(true);
    }

    private String targetName(Long id) {
        SysUser u = userMapper.selectById(id);
        return u == null ? String.valueOf(id) : u.getRealName();
    }

    private void enrich(SysUser u) {
        List<String> roles = roleCodesOf(u.getId());
        u.setRoles(roles);
        u.setPhone(u.getMobile());
        if (roles.contains("ADMIN")) {
            u.setDeclareResultAccess(1);
            if (u.getFormMaintScope() == null || u.getFormMaintScope().isEmpty()) {
                u.setFormMaintScope("hq");
            }
        }
    }

    private void normalizePhone(SysUser body) {
        if (body.getPhone() != null && (body.getMobile() == null || body.getMobile().isEmpty())) {
            body.setMobile(body.getPhone());
        }
    }

    private void forceAdminSpecialAuth(SysUser body) {
        forceAdminSpecialAuth(body, null);
    }

    private void forceAdminSpecialAuth(SysUser body, SysUser existing) {
        boolean isAdminIdentity = "系统管理员".equals(body.getIdentity())
                || "admin".equalsIgnoreCase(body.getIdentityCode());
        boolean wasAdmin = existing != null && roleCodesOf(existing.getId()).contains("ADMIN");
        if (isAdminIdentity || wasAdmin) {
            body.setDeclareResultAccess(1);
            if (body.getFormMaintScope() == null || body.getFormMaintScope().isEmpty()) {
                body.setFormMaintScope("hq");
            }
        }
    }

    private long countAdmins() {
        List<SysRole> adminRole = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, "ADMIN"));
        if (adminRole.isEmpty()) {
            return 0;
        }
        Long roleId = adminRole.get(0).getId();
        return userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId));
    }

    private List<String> roleCodesOf(Long userId) {
        List<SysUserRole> urs = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        List<String> codes = new ArrayList<>();
        for (SysUserRole ur : urs) {
            SysRole r = roleMapper.selectById(ur.getRoleId());
            if (r != null) {
                codes.add(r.getRoleCode());
            }
        }
        return codes;
    }

    /** 按任职身份补齐 identityCode / 数据范围 / 缺省角色建议 */
    private void applyIdentityDefaults(SysUser body, boolean forceScope) {
        IdentityPermissionDefaults.Defaults def =
                IdentityPermissionDefaults.resolve(body.getIdentity(), body.getIdentityCode());
        if (def == null) {
            return;
        }
        if (body.getIdentityCode() == null || body.getIdentityCode().isBlank()) {
            body.setIdentityCode(def.identityCode());
        }
        if (forceScope || body.getDataScope() == null || body.getDataScope().isBlank()) {
            body.setDataScope(def.dataScope());
        }
        if (def.formMaintScope() != null
                && (body.getFormMaintScope() == null || body.getFormMaintScope().isBlank())) {
            body.setFormMaintScope(def.formMaintScope());
        }
        if ("admin".equals(def.identityCode())) {
            body.setDeclareResultAccess(1);
            body.setFinishAuth(1);
        }
        if (body.getRoles() == null || body.getRoles().isEmpty()) {
            body.setRoles(new ArrayList<>(def.roles()));
        }
    }

    private List<String> resolveRoleCodes(SysUser body) {
        if (body.getRoles() != null && !body.getRoles().isEmpty()) {
            return new ArrayList<>(body.getRoles());
        }
        IdentityPermissionDefaults.Defaults def =
                IdentityPermissionDefaults.resolve(body.getIdentity(), body.getIdentityCode());
        if (def != null) {
            return new ArrayList<>(def.roles());
        }
        return new ArrayList<>();
    }

    private void bindRoles(Long userId, List<String> roleCodes) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleCodes == null) {
            return;
        }
        for (String code : roleCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            SysRole role = roleMapper.selectOne(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, code.trim()).last("LIMIT 1"));
            if (role == null) {
                continue;
            }
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(role.getId());
            userRoleMapper.insert(ur);
        }
    }

    private void requireAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "仅系统管理员可进行人员权限配置");
        }
    }
}
