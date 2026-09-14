package com.comac.rpm.modules.system.controller;

import com.comac.rpm.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查（SecurityConfig 中放行）
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public R<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "rpm-backend");
        data.put("time", java.time.LocalDateTime.now().toString());
        return R.ok(data);
    }
}
