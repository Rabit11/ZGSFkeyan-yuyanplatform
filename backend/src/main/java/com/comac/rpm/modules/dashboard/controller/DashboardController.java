package com.comac.rpm.modules.dashboard.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.R;
import com.comac.rpm.common.util.ColorUtil;
import com.comac.rpm.modules.dashboard.service.DashboardService;
import com.comac.rpm.modules.deliverable.entity.ProjDeliverable;
import com.comac.rpm.modules.deliverable.mapper.ProjDeliverableMapper;
import com.comac.rpm.modules.milestone.entity.ProjMilestone;
import com.comac.rpm.modules.milestone.mapper.ProjMilestoneMapper;
import com.comac.rpm.modules.plan.entity.ProjPlan;
import com.comac.rpm.modules.plan.mapper.ProjPlanMapper;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import com.comac.rpm.modules.system.entity.SysWarning;
import com.comac.rpm.modules.system.mapper.SysWarningMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可视化看板：企业级驾驶舱 / 预研大屏 / 风险榜
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ProjInfoMapper projectMapper;
    private final ProjMilestoneMapper milestoneMapper;
    private final ProjPlanMapper planMapper;
    private final ProjDeliverableMapper deliverableMapper;
    private final SysWarningMapper warningMapper;

    public DashboardController(DashboardService dashboardService,
                               ProjInfoMapper projectMapper,
                               ProjMilestoneMapper milestoneMapper,
                               ProjPlanMapper planMapper,
                               ProjDeliverableMapper deliverableMapper,
                               SysWarningMapper warningMapper) {
        this.dashboardService = dashboardService;
        this.projectMapper = projectMapper;
        this.milestoneMapper = milestoneMapper;
        this.planMapper = planMapper;
        this.deliverableMapper = deliverableMapper;
        this.warningMapper = warningMapper;
    }

    @GetMapping
    public R<Map<String, Object>> cockpit(
            @RequestParam(value = "year", required = false) String year,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "sourceChannel", required = false) String sourceChannel,
            @RequestParam(value = "orgOffice", required = false) String orgOffice,
            @RequestParam(value = "projectType", required = false) String projectType,
            @RequestParam(value = "major1", required = false) String major1,
            @RequestParam(value = "major2", required = false) String major2,
            @RequestParam(value = "unit", required = false) String unit,
            @RequestParam(value = "screen", required = false) String screen) {
        return R.ok(dashboardService.build(query(year, level, sourceChannel, orgOffice, projectType, major1, major2, unit, screen), true));
    }

    @GetMapping("/cockpit")
    public R<Map<String, Object>> cockpitAlias(
            @RequestParam(value = "year", required = false) String year,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "sourceChannel", required = false) String sourceChannel,
            @RequestParam(value = "orgOffice", required = false) String orgOffice,
            @RequestParam(value = "projectType", required = false) String projectType,
            @RequestParam(value = "major1", required = false) String major1,
            @RequestParam(value = "major2", required = false) String major2,
            @RequestParam(value = "unit", required = false) String unit,
            @RequestParam(value = "screen", required = false) String screen) {
        return cockpit(year, level, sourceChannel, orgOffice, projectType, major1, major2, unit, screen);
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview(
            @RequestParam(value = "year", required = false) String year,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "sourceChannel", required = false) String sourceChannel,
            @RequestParam(value = "orgOffice", required = false) String orgOffice,
            @RequestParam(value = "projectType", required = false) String projectType,
            @RequestParam(value = "major1", required = false) String major1,
            @RequestParam(value = "major2", required = false) String major2,
            @RequestParam(value = "unit", required = false) String unit,
            @RequestParam(value = "screen", required = false) String screen) {
        // 首页概览面向所有已登录用户；只有驾驶舱/大屏接口需要总部可视化权限。
        return R.ok(dashboardService.build(
                query(year, level, sourceChannel, orgOffice, projectType, major1, major2, unit, screen), false));
    }

    @GetMapping("/warnings")
    public R<List<SysWarning>> warnings() {
        List<SysWarning> list = warningMapper.selectList(
                new LambdaQueryWrapper<SysWarning>().orderByDesc(SysWarning::getCreatedAt).last("LIMIT 50"));
        if (list.isEmpty()) {
            list = realtimeWarnings();
        }
        return R.ok(list);
    }

    private Map<String, String> query(String year, String level, String sourceChannel, String orgOffice,
                                      String projectType, String major1, String major2, String unit, String screen) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("year", year);
        q.put("level", level);
        q.put("sourceChannel", sourceChannel);
        q.put("orgOffice", orgOffice);
        q.put("projectType", projectType);
        q.put("major1", major1);
        q.put("major2", major2);
        q.put("unit", unit);
        q.put("screen", screen);
        return q;
    }

    private List<SysWarning> realtimeWarnings() {
        List<SysWarning> list = new ArrayList<>();
        List<ProjInfo> projects = projectMapper.selectList(null);
        Map<Long, String> names = new LinkedHashMap<>();
        for (ProjInfo p : projects) {
            names.put(p.getId(), p.getName());
        }
        for (ProjMilestone m : milestoneMapper.selectList(null)) {
            String c = ColorUtil.calcCode(m.getPlanDate(), "DONE".equals(m.getStatus()));
            if ("RED".equals(c) || "YELLOW".equals(c)) {
                list.add(warn("MILESTONE", m.getId(), m.getProjectId(), names.get(m.getProjectId()),
                        c, (c.equals("RED") ? "里程碑逾期：" : "里程碑临期：") + m.getName(),
                        "计划完成时间 " + m.getPlanDate()));
            }
        }
        for (ProjDeliverable d : deliverableMapper.selectList(null)) {
            String c = ColorUtil.calcCode(d.getDueDate(), "DELIVERED".equals(d.getStatus()));
            if ("RED".equals(c) || "YELLOW".equals(c)) {
                list.add(warn("DELIVERABLE", d.getId(), d.getProjectId(), names.get(d.getProjectId()),
                        c, (c.equals("RED") ? "交付物逾期：" : "交付物临期：") + d.getName(),
                        "应交付时间 " + d.getDueDate()));
            }
        }
        for (ProjPlan p : planMapper.selectList(null)) {
            String c = ColorUtil.calcCode(p.getDueDate(), "DONE".equals(p.getPlanType()));
            if ("RED".equals(c) && "TODO".equals(p.getPlanType())) {
                list.add(warn("PLAN", p.getId(), p.getProjectId(), names.get(p.getProjectId()),
                        "RED", "计划超期：" + p.getTitle(), "待办计划已超期，请提交办结申请"));
            }
        }
        return list;
    }

    private SysWarning warn(String bizType, Long bizId, Long projectId, String projectName,
                            String level, String title, String content) {
        SysWarning w = new SysWarning();
        w.setBizType(bizType);
        w.setBizId(bizId);
        w.setProjectId(projectId);
        w.setProjectName(projectName);
        w.setWarnLevel(level);
        w.setTitle(title);
        w.setContent(content);
        w.setReceiver("PROJECT_TEAM,MANAGEMENT");
        w.setIsRead(0);
        return w;
    }
}
