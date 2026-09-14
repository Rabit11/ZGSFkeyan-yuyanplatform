package com.comac.rpm.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.common.permission.PostPermissionDefaults;
import com.comac.rpm.modules.system.entity.SysPostPermission;
import com.comac.rpm.modules.system.mapper.SysAuditLogMapper;
import com.comac.rpm.modules.system.mapper.SysPostPermissionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 项目岗位办理权限矩阵
 */
@Service
public class PermissionMatrixService {

    @Autowired
    private SysPostPermissionMapper postPermissionMapper;
    @Autowired
    private SysAuditLogMapper auditLogMapper;

    public Map<String, List<String>> getMatrix() {
        List<SysPostPermission> rows = postPermissionMapper.selectList(
                new LambdaQueryWrapper<SysPostPermission>().eq(SysPostPermission::getEnabled, 1));
        Map<String, List<String>> matrix = emptyMatrix();
        if (rows == null || rows.isEmpty()) {
            return PostPermissionDefaults.defaultMatrix();
        }
        for (SysPostPermission row : rows) {
            if (row.getPostCode() == null || row.getPermCode() == null) {
                continue;
            }
            matrix.computeIfAbsent(row.getPostCode(), k -> new ArrayList<>()).add(row.getPermCode());
        }
        inheritContactPerms(matrix);
        return matrix;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveMatrix(Map<String, List<String>> body) {
        persistMatrix(body);
        auditLogMapper.write("PERMISSION", "UPDATE", "POST_MATRIX", null, "更新项目岗位办理权限矩阵");
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, List<String>> resetDefault() {
        Map<String, List<String>> defaults = PostPermissionDefaults.defaultMatrix();
        persistMatrix(defaults);
        auditLogMapper.write("PERMISSION", "RESET", "POST_MATRIX", null, "重置项目岗位办理权限矩阵为默认值");
        return defaults;
    }

    private void persistMatrix(Map<String, List<String>> body) {
        requireAdmin();
        if (body == null) {
            throw new BusinessException("矩阵数据不能为空");
        }
        postPermissionMapper.delete(new LambdaQueryWrapper<>());
        for (String post : PostPermissionDefaults.POST_CODES) {
            List<String> perms = body.getOrDefault(post, Collections.emptyList());
            Set<String> uniq = new HashSet<>(perms == null ? Collections.emptyList() : perms);
            for (String perm : PostPermissionDefaults.PERM_CODES) {
                if (!uniq.contains(perm)) {
                    continue;
                }
                SysPostPermission row = new SysPostPermission();
                row.setPostCode(post);
                row.setPermCode(perm);
                row.setEnabled(1);
                postPermissionMapper.insert(row);
            }
        }
    }

    /** 判断岗位集合在当前矩阵下是否具备某办理权限（并集） */
    public boolean hasPerm(List<String> postCodes, String permCode) {
        if (permCode == null || postCodes == null || postCodes.isEmpty()) {
            return false;
        }
        if (UserContext.isAdmin()) {
            return true;
        }
        Map<String, List<String>> matrix = getMatrix();
        for (String post : postCodes) {
            List<String> perms = matrix.get(post);
            if (perms != null && perms.contains(permCode)) {
                return true;
            }
        }
        return false;
    }

    private void inheritContactPerms(Map<String, List<String>> matrix) {
        List<String> contactPerms = matrix.get("contact");
        if (contactPerms == null || contactPerms.isEmpty()) {
            return;
        }
        LinkedHashSet<String> merged = new LinkedHashSet<>(matrix.getOrDefault("owner", Collections.emptyList()));
        merged.addAll(contactPerms);
        matrix.put("owner", new ArrayList<>(merged));
    }

    private Map<String, List<String>> emptyMatrix() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (String post : PostPermissionDefaults.POST_CODES) {
            m.put(post, new ArrayList<>());
        }
        return m;
    }

    private void requireAdmin() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "仅系统管理员可配置项目岗位办理权限");
        }
    }
}
