package com.comac.rpm.modules.system.controller;

import com.comac.rpm.common.R;
import com.comac.rpm.modules.system.service.BackupService;
import com.comac.rpm.modules.system.service.PermissionMatrixService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 项目岗位办理权限矩阵（系统管理员配置）
 */
@RestController
@RequestMapping("/api/permission-matrix")
public class PermissionMatrixController {

    @Autowired
    private PermissionMatrixService permissionMatrixService;
    @Autowired
    private BackupService backupService;

    @GetMapping
    public R<Map<String, List<String>>> get() {
        return R.ok(permissionMatrixService.getMatrix());
    }

    @PutMapping
    public R<Boolean> save(@RequestBody Map<String, List<String>> body) {
        backupService.create("PRE_RISK", "更新项目岗位权限矩阵前");
        permissionMatrixService.saveMatrix(body);
        return R.ok(true);
    }

    @PostMapping("/reset")
    public R<Map<String, List<String>>> reset() {
        backupService.create("PRE_RISK", "重置项目岗位权限矩阵前");
        return R.ok(permissionMatrixService.resetDefault());
    }
}
