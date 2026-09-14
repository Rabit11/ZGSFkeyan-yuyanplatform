package com.comac.rpm.modules.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.common.util.ColorUtil;
import com.comac.rpm.modules.change.entity.ProjChange;
import com.comac.rpm.modules.change.mapper.ProjChangeMapper;
import com.comac.rpm.modules.deliverable.entity.ProjDeliverable;
import com.comac.rpm.modules.deliverable.mapper.ProjDeliverableMapper;
import com.comac.rpm.modules.dict.entity.ProjChannel;
import com.comac.rpm.modules.dict.mapper.ProjChannelMapper;
import com.comac.rpm.modules.fund.entity.FundBudget;
import com.comac.rpm.modules.fund.entity.FundPayment;
import com.comac.rpm.modules.fund.mapper.FundBudgetMapper;
import com.comac.rpm.modules.fund.mapper.FundPaymentMapper;
import com.comac.rpm.modules.milestone.entity.ProjMilestone;
import com.comac.rpm.modules.milestone.mapper.ProjMilestoneMapper;
import com.comac.rpm.modules.partner.entity.PartnerEval;
import com.comac.rpm.modules.partner.mapper.PartnerBlacklistMapper;
import com.comac.rpm.modules.partner.mapper.PartnerEvalMapper;
import com.comac.rpm.modules.plan.entity.ProjPlan;
import com.comac.rpm.modules.plan.mapper.ProjPlanMapper;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.entity.ProjTeamMember;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import com.comac.rpm.modules.project.mapper.ProjTeamMemberMapper;
import com.comac.rpm.modules.system.entity.SysUser;
import com.comac.rpm.modules.system.mapper.SysUserMapper;
import com.comac.rpm.modules.transform.entity.AchvTransform;
import com.comac.rpm.modules.transform.mapper.AchvTransformMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 企业级驾驶舱聚合：先按用户权限过滤项目，再按筛选条件计算 KPI / 图表 / 风险榜。
 */
@Service
public class DashboardService {

    private static final String[] LEVELS = {"NATIONAL", "LOCAL", "COMPANY"};
    private static final String[] LEVEL_NAMES = {"国家级", "地方级", "公司级"};
    private static final String[] STATUSES = {"申报中", "立项中", "实施中", "验收中", "已验收", "已终止"};
    private static final String[][] DELIV = {
            {"PATENT", "专利"}, {"PAPER", "论文"}, {"SOFTWARE", "软件著作权"},
            {"STANDARD", "技术标准"}, {"PROTOTYPE", "原理样机"}, {"EQUIPMENT", "设备"},
            {"TECH_PACKAGE", "成套技术成果"}
    };
    private static final String[][] TF_STAGES = {
            {"NOT_STARTED", "未启动"}, {"NEGOTIATING", "洽谈中"}, {"SIGNED", "已签协议"}, {"DONE", "已完成"}
    };
    private static final Set<String> HQ_BOARD = new HashSet<>(Arrays.asList(
            "admin", "leader", "hqHead", "hqStaff", "finHq"));

    private final ProjInfoMapper projectMapper;
    private final ProjChannelMapper channelMapper;
    private final ProjMilestoneMapper milestoneMapper;
    private final ProjPlanMapper planMapper;
    private final ProjDeliverableMapper deliverableMapper;
    private final AchvTransformMapper transformMapper;
    private final FundBudgetMapper budgetMapper;
    private final FundPaymentMapper paymentMapper;
    private final ProjTeamMemberMapper teamMapper;
    private final SysUserMapper userMapper;
    private final PartnerBlacklistMapper blacklistMapper;
    private final PartnerEvalMapper partnerEvalMapper;
    private final ProjChangeMapper changeMapper;

    public DashboardService(ProjInfoMapper projectMapper,
                            ProjChannelMapper channelMapper,
                            ProjMilestoneMapper milestoneMapper,
                            ProjPlanMapper planMapper,
                            ProjDeliverableMapper deliverableMapper,
                            AchvTransformMapper transformMapper,
                            FundBudgetMapper budgetMapper,
                            FundPaymentMapper paymentMapper,
                            ProjTeamMemberMapper teamMapper,
                            SysUserMapper userMapper,
                            PartnerBlacklistMapper blacklistMapper,
                            PartnerEvalMapper partnerEvalMapper,
                            ProjChangeMapper changeMapper) {
        this.projectMapper = projectMapper;
        this.channelMapper = channelMapper;
        this.milestoneMapper = milestoneMapper;
        this.planMapper = planMapper;
        this.deliverableMapper = deliverableMapper;
        this.transformMapper = transformMapper;
        this.budgetMapper = budgetMapper;
        this.paymentMapper = paymentMapper;
        this.teamMapper = teamMapper;
        this.userMapper = userMapper;
        this.blacklistMapper = blacklistMapper;
        this.partnerEvalMapper = partnerEvalMapper;
        this.changeMapper = changeMapper;
    }

    public Map<String, Object> build(Map<String, String> query) {
        return build(query, false);
    }

    public Map<String, Object> build(Map<String, String> query, boolean hqOnly) {
        try {
            SysUser user = UserContext.getUserId() == null ? null : userMapper.selectById(UserContext.getUserId());
            if ((hqOnly || "pre-research".equals(str(query.get("screen")))) && !canAccessPreResearch(user)) {
                throw new BusinessException(403, "当前身份无权访问可视化看板");
            }
            List<ProjTeamMember> members = teamMapper.selectList(null);
            Map<Long, List<ProjTeamMember>> membersByProj = members.stream()
                    .collect(Collectors.groupingBy(ProjTeamMember::getProjectId));
            List<ProjInfo> scoped = visibleProjects(projectMapper.selectList(null), user, membersByProj);
            List<ProjInfo> projects = applyFilters(scoped, query);
            Set<Long> ids = projects.stream().map(ProjInfo::getId).collect(Collectors.toSet());
            String mode = dataMode(projects);

            List<ProjMilestone> milestones = filterByIds(milestoneMapper.selectList(null), ids, ProjMilestone::getProjectId);
            List<ProjPlan> plans = filterByIds(planMapper.selectList(null), ids, ProjPlan::getProjectId);
            List<ProjDeliverable> deliverables = filterByIds(deliverableMapper.selectList(null), ids, ProjDeliverable::getProjectId);
            List<AchvTransform> transforms = filterByIds(transformMapper.selectList(null), ids, AchvTransform::getProjectId);
            List<FundBudget> budgets = filterByIds(budgetMapper.selectList(null), ids, FundBudget::getProjectId);
            List<FundPayment> payments = filterByIds(paymentMapper.selectList(null), ids, FundPayment::getProjectId);
            List<ProjChange> changes = filterByIds(changeMapper.selectList(null), ids, ProjChange::getProjectId);
            List<PartnerEval> evals = filterByIds(partnerEvalMapper.selectList(null), ids, PartnerEval::getProjectId);
            List<ProjChannel> channels = channelMapper.selectList(null);
            Map<Long, ProjChannel> channelMap = channels.stream()
                    .collect(Collectors.toMap(ProjChannel::getId, c -> c, (a, b) -> a));

            double totalFund = sum(projects, ProjInfo::getTotalFund);
            double national = sum(projects, ProjInfo::getNationalFund);
            double self = sum(projects, ProjInfo::getSelfFund);
            double outsource = sum(projects, ProjInfo::getOutsourceAmount);
            double inner = Math.max(0, round2(totalFund - outsource));
            double expenseTotal = sum(projects, ProjInfo::getExpenseTotal);
            double yearBudget = sum(projects, ProjInfo::getYearBudget);
            double yearExpense = sum(projects, ProjInfo::getYearExpense);
            List<ProjInfo> running = projects.stream().filter(p -> isRunning(p.getStatus())).collect(Collectors.toList());
            double runningFund = sum(running, ProjInfo::getTotalFund);

            List<Map<String, Object>> byLevel = new ArrayList<>();
            for (int i = 0; i < LEVELS.length; i++) {
                String code = LEVELS[i];
                List<ProjInfo> rows = projects.stream().filter(p -> code.equals(p.getLevelCode())).collect(Collectors.toList());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", LEVEL_NAMES[i]);
                m.put("level", code);
                m.put("value", rows.size());
                m.put("count", rows.size());
                m.put("fund", round2(sum(rows, ProjInfo::getTotalFund)));
                byLevel.add(m);
            }

            Map<String, double[]> chAgg = new LinkedHashMap<>();
            for (ProjInfo p : projects) {
                ProjChannel ch = p.getChannelId() == null ? null : channelMap.get(p.getChannelId());
                String name;
                if ("form-ledger".equals(mode)) {
                    name = nz(p.getChannelName(), "未填渠道") + " / " + nz(p.getProjectType(), "未填类型");
                } else {
                    name = first(p.getChannelName(), ch == null ? null : ch.getChannelName(), "未填渠道");
                }
                double[] v = chAgg.computeIfAbsent(name, k -> new double[2]);
                v[0] += 1;
                v[1] += n(p.getTotalFund());
            }
            List<Map<String, Object>> byChannel = chAgg.entrySet().stream()
                    .map(e -> channelNode(e.getKey(), (int) e.getValue()[0], e.getValue()[1]))
                    .sorted((a, b) -> Integer.compare((int) b.get("count"), (int) a.get("count")))
                    .collect(Collectors.toList());

            Map<String, Map<String, Object>> unitAgg = new LinkedHashMap<>();
            for (ProjInfo p : projects) {
                String name = shortUnit(first(p.getLeadOrgName(), p.getOrgName(), "未填单位"));
                String key = String.valueOf(p.getLeadOrgId() != null ? p.getLeadOrgId() : p.getOrgId() != null ? p.getOrgId() : name);
                Map<String, Object> cur = unitAgg.computeIfAbsent(key, k -> unitSeed(name, p.getLeadOrgId() != null ? p.getLeadOrgId() : p.getOrgId()));
                cur.put("count", (int) cur.get("count") + 1);
                cur.put("fund", (double) cur.get("fund") + n(p.getTotalFund()));
                String color = nz(p.getWarnColor(), "BLUE");
                if ("RED".equals(color)) cur.put("red", (int) cur.get("red") + 1);
                else if ("YELLOW".equals(color)) cur.put("yellow", (int) cur.get("yellow") + 1);
                else if ("GREEN".equals(color)) cur.put("green", (int) cur.get("green") + 1);
                else cur.put("blue", (int) cur.get("blue") + 1);
                if (isRunning(p.getStatus())) cur.put("running", (int) cur.get("running") + 1);
                if ("已验收".equals(stdStatus(p.getStatus()))) cur.put("accepted", (int) cur.get("accepted") + 1);
                @SuppressWarnings("unchecked")
                Map<String, Integer> levels = (Map<String, Integer>) cur.get("levels");
                if (p.getLevelCode() != null) {
                    levels.put(p.getLevelCode(), levels.getOrDefault(p.getLevelCode(), 0) + 1);
                }
            }
            List<Map<String, Object>> byUnit = unitAgg.values().stream()
                    .peek(u -> u.put("fund", round2((double) u.get("fund"))))
                    .sorted((a, b) -> Integer.compare((int) b.get("count"), (int) a.get("count")))
                    .collect(Collectors.toList());
            List<String> unitNames = byUnit.stream().map(u -> String.valueOf(u.get("name"))).collect(Collectors.toList());
            List<Object> orgIds = byUnit.stream().map(u -> u.get("orgId")).collect(Collectors.toList());
            List<Map<String, Object>> matrixSeries = new ArrayList<>();
            for (int i = 0; i < LEVELS.length; i++) {
                String code = LEVELS[i];
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("name", LEVEL_NAMES[i]);
                s.put("level", code);
                List<Integer> data = new ArrayList<>();
                for (Map<String, Object> u : byUnit) {
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> levels = (Map<String, Integer>) u.get("levels");
                    data.add(levels.getOrDefault(code, 0));
                }
                s.put("data", data);
                matrixSeries.add(s);
            }
            Map<String, Object> unitLevelMatrix = new LinkedHashMap<>();
            unitLevelMatrix.put("units", unitNames);
            unitLevelMatrix.put("series", matrixSeries);
            unitLevelMatrix.put("orgIds", orgIds);

            int thisYear = LocalDate.now().getYear();
            List<Map<String, Object>> fundsTrend = new ArrayList<>();
            for (int y = thisYear - 4; y <= thisYear; y++) {
                final int year = y;
                double budget = budgets.stream().filter(b -> b.getYear() != null && b.getYear() == year).mapToDouble(b -> n(b.getAmount())).sum();
                double expense = payments.stream().filter(p -> p.getOccurDate() != null && p.getOccurDate().getYear() == year)
                        .mapToDouble(p -> n(p.getAmount())).sum();
                if (budget == 0 && expense == 0) {
                    for (ProjInfo p : projects) {
                        if (!overlapsYear(p, year)) continue;
                        if (year == thisYear) {
                            budget += n(p.getYearBudget());
                            expense += n(p.getYearExpense());
                        } else {
                            int span = durationYears(p);
                            budget += n(p.getTotalFund()) / span;
                            expense += n(p.getExpenseTotal()) / span;
                        }
                    }
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("year", year);
                row.put("budget", round2(budget));
                row.put("expense", round2(expense));
                row.put("rate", rate(expense, budget));
                fundsTrend.add(row);
            }

            List<Map<String, Object>> statusDist = new ArrayList<>();
            for (String st : STATUSES) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", st);
                m.put("value", projects.stream().filter(p -> st.equals(stdStatus(p.getStatus()))).count());
                statusDist.add(m);
            }

            Map<String, Map<String, Object>> majorAgg = new LinkedHashMap<>();
            for (ProjInfo p : projects) {
                String major1 = p.getMajor1() != null && p.getMajor1().matches("^\\d{2}-.*") ? p.getMajor1() : "未填专业";
                Map<String, Object> cur = majorAgg.computeIfAbsent(major1, k -> {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("name", stripMajor(k));
                    x.put("major1", k);
                    x.put("count", 0);
                    x.put("fund", 0d);
                    x.put("children", new LinkedHashMap<String, Integer>());
                    return x;
                });
                cur.put("count", (int) cur.get("count") + 1);
                cur.put("fund", (double) cur.get("fund") + n(p.getTotalFund()));
                if (p.getMajor2() != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> children = (Map<String, Integer>) cur.get("children");
                    children.put(p.getMajor2(), children.getOrDefault(p.getMajor2(), 0) + 1);
                }
            }
            List<Map<String, Object>> byMajor1 = majorAgg.values().stream()
                    .peek(x -> {
                        x.put("fund", round2((double) x.get("fund")));
                        x.put("value", x.get("fund"));
                        @SuppressWarnings("unchecked")
                        Map<String, Integer> children = (Map<String, Integer>) x.get("children");
                        List<Map<String, Object>> childList = new ArrayList<>();
                        children.forEach((name, count) -> {
                            Map<String, Object> c = new LinkedHashMap<>();
                            c.put("name", name);
                            c.put("count", count);
                            childList.add(c);
                        });
                        x.put("children", childList);
                    })
                    .sorted((a, b) -> Double.compare((double) b.get("fund"), (double) a.get("fund")))
                    .collect(Collectors.toList());

            List<Map<String, Object>> delivByType = new ArrayList<>();
            if (deliverables.isEmpty() && !transforms.isEmpty()) {
                delivByType.add(pkgAsDeliv("向型号转化", "MODEL", transforms));
                delivByType.add(pkgAsDeliv("向市场转化", "MARKET", transforms));
            } else {
                for (String[] t : DELIV) {
                    List<ProjDeliverable> rows = deliverables.stream().filter(d -> t[0].equals(d.getDeliverType())).collect(Collectors.toList());
                    long done = rows.stream().filter(d -> "DELIVERED".equals(d.getStatus()) || d.getDeliverDate() != null).count();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", t[1]);
                    m.put("code", t[0]);
                    m.put("delivered", done);
                    m.put("pending", rows.size() - done);
                    m.put("total", rows.size());
                    delivByType.add(m);
                }
            }

            List<Map<String, Object>> transform = new ArrayList<>();
            for (String[] st : TF_STAGES) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", st[1]);
                m.put("code", st[0]);
                m.put("value", transforms.stream().filter(t -> st[0].equals(t.getStatus())).count());
                transform.add(m);
            }
            long overduePkg = transforms.stream().filter(t -> {
                boolean done = "DONE".equals(t.getStatus()) || t.getActualDate() != null;
                return "RED".equals(ColorUtil.calcCode(t.getPlanDate(), done));
            }).count();
            Map<String, Object> transformSummary = new LinkedHashMap<>();
            transformSummary.put("total", transforms.size());
            transformSummary.put("done", transforms.stream().filter(t -> "DONE".equals(t.getStatus())).count());
            transformSummary.put("progressing", transforms.stream().filter(t -> "NEGOTIATING".equals(t.getStatus()) || "SIGNED".equals(t.getStatus())).count());
            transformSummary.put("notStarted", transforms.stream().filter(t -> "NOT_STARTED".equals(t.getStatus())).count());
            transformSummary.put("overdue", overduePkg);

            Map<String, int[]> modelMap = new LinkedHashMap<>();
            for (AchvTransform t : transforms) {
                if (!"MODEL".equals(t.getTransformWay())) continue;
                String name = parseModelTarget(t.getIntro(), t.getTransformForm());
                int[] v = modelMap.computeIfAbsent(name, k -> new int[2]);
                if ("DONE".equals(t.getStatus())) v[0]++;
                else v[1]++;
            }
            List<Map<String, Object>> modelTransform = modelMap.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue()[0] + b.getValue()[1], a.getValue()[0] + a.getValue()[1]))
                    .limit(6)
                    .map(e -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", e.getKey());
                        m.put("done", e.getValue()[0]);
                        m.put("doing", e.getValue()[1]);
                        return m;
                    }).collect(Collectors.toList());

            List<ProjPlan> finished = plans.stream()
                    .filter(p -> "DONE".equals(p.getStatus()) || "DONE".equals(p.getPlanType()) || p.getFinishDate() != null)
                    .collect(Collectors.toList());
            Set<Long> finishedIds = finished.stream().map(ProjPlan::getId).collect(Collectors.toSet());
            int red = 0, yellow = 0, blue = 0;
            for (ProjPlan p : plans) {
                if (finishedIds.contains(p.getId())) continue;
                String c = ColorUtil.calcCode(p.getDueDate(), false);
                if ("RED".equals(c)) red++;
                else if ("YELLOW".equals(c)) yellow++;
                else blue++;
            }
            Map<String, Object> colors = new LinkedHashMap<>();
            colors.put("red", red);
            colors.put("yellow", yellow);
            colors.put("blue", blue);
            colors.put("green", finished.size());
            Map<String, Object> planStats = new LinkedHashMap<>();
            planStats.put("total", plans.size());
            planStats.put("done", finished.size());
            planStats.put("todo", plans.size() - finished.size());
            planStats.put("finishRate", rate(finished.size(), plans.size()));
            planStats.put("colors", colors);
            planStats.put("cmosSyncAt", null);
            planStats.put("cmosText", "CMOS接口待联调");

            Map<Long, String> names = projects.stream().collect(Collectors.toMap(ProjInfo::getId, p -> nz(p.getName(), ""), (a, b) -> a));
            List<Map<String, Object>> risks = new ArrayList<>();
            for (ProjMilestone m : milestones) {
                boolean done = "DONE".equals(m.getStatus()) || m.getActualDate() != null;
                String c = ColorUtil.calcCode(m.getPlanDate(), done);
                if ("RED".equals(c) || "YELLOW".equals(c)) {
                    risks.add(risk("ms-" + m.getId(), m.getProjectId(), names.get(m.getProjectId()), "里程碑", m.getName(), m.getPlanDate(), c));
                }
            }
            for (ProjDeliverable d : deliverables) {
                boolean done = "DELIVERED".equals(d.getStatus()) || d.getDeliverDate() != null;
                String c = ColorUtil.calcCode(d.getDueDate(), done);
                if ("RED".equals(c)) {
                    risks.add(risk("dv-" + d.getId(), d.getProjectId(), names.get(d.getProjectId()), "交付物", d.getName(), d.getDueDate(), "RED"));
                }
            }
            if ("form-ledger".equals(mode) && milestones.isEmpty()) {
                for (ProjInfo p : projects) {
                    if ("RED".equals(p.getWarnColor()) || "YELLOW".equals(p.getWarnColor())) {
                        risks.add(risk("pj-" + p.getId(), p.getId(), p.getName(), "台账项目",
                                "RED".equals(p.getWarnColor()) ? "项目逾期" : "项目临期", p.getEndDate(), p.getWarnColor()));
                    }
                }
            }
            risks.sort((a, b) -> {
                String ca = String.valueOf(a.get("color"));
                String cb = String.valueOf(b.get("color"));
                if (!ca.equals(cb)) return "RED".equals(ca) ? -1 : 1;
                return Integer.compare((int) a.get("remain"), (int) b.get("remain"));
            });
            if (risks.size() > 12) risks = new ArrayList<>(risks.subList(0, 12));
            Set<Long> redProjects = risks.stream().filter(r -> "RED".equals(r.get("color")))
                    .map(r -> (Long) r.get("projectId")).collect(Collectors.toSet());

            long deliveredCount = deliverables.stream().filter(d -> "DELIVERED".equals(d.getStatus()) || d.getDeliverDate() != null).count();
            long blacklistCount = blacklistMapper.selectCount(null);
            if (blacklistCount == 0) {
                blacklistCount = evals.stream().filter(e -> "FAIL".equals(e.getGrade())).count();
            }
            long approving = changes.stream().filter(c -> Arrays.asList("APPROVING", "SUBMITTED", "HQ_AUDIT", "UNIT_AUDIT").contains(nz(c.getStatus(), ""))).count();

            Map<String, Object> kpis = new LinkedHashMap<>();
            kpis.put("projectCount", projects.size());
            kpis.put("runningCount", running.size());
            kpis.put("totalFund", round2(totalFund));
            kpis.put("totalFundYi", roundScale(totalFund / 10000d, 4));
            kpis.put("nationalFund", round2(national));
            kpis.put("selfFund", round2(self));
            kpis.put("innerFund", inner);
            kpis.put("yearBudget", round2(yearBudget));
            kpis.put("yearExpense", round2(yearExpense));
            kpis.put("execRateTotal", rate(expenseTotal, totalFund));
            kpis.put("execRateYear", rate(yearExpense, yearBudget));
            kpis.put("overdueCount", redProjects.size());
            kpis.put("packageCount", transforms.size());
            kpis.put("packageDone", transformSummary.get("done"));
            kpis.put("planFinishRate", planStats.get("finishRate"));
            kpis.put("planTodo", planStats.get("todo"));
            kpis.put("deliveredCount", deliveredCount);
            kpis.put("blacklistCount", blacklistCount);
            kpis.put("approvingCount", approving);

            Map<String, Object> fundStructure = new LinkedHashMap<>();
            fundStructure.put("total", round2(totalFund));
            fundStructure.put("national", round2(national));
            fundStructure.put("self", round2(self));
            fundStructure.put("inner", inner);
            fundStructure.put("running", round2(runningFund));

            List<Map<String, Object>> unitsMeta = new ArrayList<>();
            Set<String> seenUnit = new HashSet<>();
            Set<String> types = new HashSet<>();
            for (ProjInfo p : scoped) {
                if (p.getProjectType() != null) types.add(p.getProjectType());
                String name = first(p.getLeadOrgName(), p.getOrgName(), null);
                if (name == null) continue;
                String key = String.valueOf(p.getLeadOrgId() != null ? p.getLeadOrgId() : p.getOrgId() != null ? p.getOrgId() : name);
                if (seenUnit.add(key)) {
                    Map<String, Object> u = new LinkedHashMap<>();
                    u.put("id", p.getLeadOrgId() != null ? p.getLeadOrgId() : p.getOrgId());
                    u.put("name", name);
                    unitsMeta.add(u);
                }
            }
            Map<String, Object> filterMeta = new LinkedHashMap<>();
            filterMeta.put("units", unitsMeta);
            filterMeta.put("projectTypes", new ArrayList<>(types));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("today", LocalDate.now().toString());
            data.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            data.put("year", query.get("year") == null || query.get("year").isBlank()
                    ? thisYear : Integer.parseInt(query.get("year")));
            data.put("dataMode", mode);
            data.put("kpis", kpis);
            data.put("byLevel", byLevel);
            data.put("byUnit", byUnit);
            data.put("unitLevelMatrix", unitLevelMatrix);
            data.put("byChannel", byChannel);
            data.put("channelTop8", byChannel.size() > 8 ? byChannel.subList(0, 8) : byChannel);
            data.put("fundsTrend", fundsTrend);
            data.put("statusDist", statusDist);
            data.put("byMajor1", byMajor1);
            data.put("delivByType", delivByType);
            data.put("transform", transform);
            data.put("transformSummary", transformSummary);
            data.put("modelTransform", modelTransform);
            data.put("planStats", planStats);
            data.put("fundStructure", fundStructure);
            data.put("risks", risks);
            data.put("filterMeta", filterMeta);
            data.put("projectCount", projects.size());
            data.put("runningCount", running.size());
            data.put("overdueCount", redProjects.size());
            data.put("totalFund", round2(totalFund));
            data.put("yearBudget", round2(yearBudget));
            data.put("yearExpense", round2(yearExpense));
            data.put("deliverableCount", deliverables.size());
            data.put("transformCount", transforms.size());
            data.put("levelDist", byLevel.stream().map(x -> node(String.valueOf(x.get("name")), (int) x.get("count"))).collect(Collectors.toList()));
            data.put("channelDist", byChannel.stream().map(x -> node(String.valueOf(x.get("name")), (int) x.get("count"))).collect(Collectors.toList()));
            data.put("fundTrend", fundsTrend.stream().map(x -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("month", String.valueOf(x.get("year")));
                m.put("budget", x.get("budget"));
                m.put("expense", x.get("expense"));
                return m;
            }).collect(Collectors.toList()));
            return data;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "驾驶舱数据计算失败：" + e.getMessage());
        }
    }

    private List<ProjInfo> visibleProjects(List<ProjInfo> all, SysUser user, Map<Long, List<ProjTeamMember>> membersByProj) {
        if (user == null) return all;
        if (UserContext.isAdmin() || "COMPANY".equals(user.getDataScope()) || (user.getRoles() != null && user.getRoles().contains("ADMIN"))) {
            return all;
        }
        boolean unitScope = "UNIT".equals(user.getDataScope());
        List<ProjInfo> list = new ArrayList<>();
        for (ProjInfo p : all) {
            boolean member = isMember(p.getId(), user, membersByProj);
            if (unitScope) {
                if ((user.getOrgId() != null && (user.getOrgId().equals(p.getOrgId()) || user.getOrgId().equals(p.getLeadOrgId()))) || member) {
                    list.add(p);
                }
            } else if (member) {
                list.add(p);
            }
        }
        return list;
    }

    private boolean isMember(Long projectId, SysUser user, Map<Long, List<ProjTeamMember>> membersByProj) {
        List<ProjTeamMember> list = membersByProj.get(projectId);
        if (list == null) return false;
        for (ProjTeamMember m : list) {
            if (user.getEmployeeNo() != null && user.getEmployeeNo().equals(m.getEmployeeNo())) return true;
            if (user.getRealName() != null && user.getRealName().equals(m.getUserName())) return true;
            if (user.getUsername() != null && user.getUsername().equals(m.getEmployeeNo())) return true;
        }
        return false;
    }

    private boolean canAccessPreResearch(SysUser user) {
        if (user == null) return UserContext.isAdmin();
        if (UserContext.isAdmin()) return true;
        if ("COMPANY".equals(user.getDataScope())) return true;
        if (user.getIdentityCode() != null && HQ_BOARD.contains(user.getIdentityCode())) return true;
        String ident = nz(user.getIdentity(), "");
        return ident.contains("系统管理员") || ident.contains("公司领导")
                || (ident.contains("总部") && !ident.contains("单位"));
    }

    private List<ProjInfo> applyFilters(List<ProjInfo> list, Map<String, String> q) {
        Integer year = parseInt(q.get("year"));
        String level = normalizeLevel(q.get("level"));
        String sourceChannel = str(q.get("sourceChannel"));
        String orgOffice = str(q.get("orgOffice"));
        String projectType = str(q.get("projectType"));
        String major1 = str(q.get("major1"));
        String major2 = str(q.get("major2"));
        String unit = str(q.get("unit"));
        List<ProjChannel> channels = channelMapper.selectList(null);
        Map<Long, ProjChannel> cmap = channels.stream().collect(Collectors.toMap(ProjChannel::getId, c -> c, (a, b) -> a));
        return list.stream().filter(p -> {
            if (year != null && !overlapsYear(p, year)) return false;
            if (!level.isEmpty() && !level.equals(p.getLevelCode())) return false;
            if (!sourceChannel.isEmpty()) {
                ProjChannel ch = p.getChannelId() == null ? null : cmap.get(p.getChannelId());
                boolean hit = sourceChannel.equals(p.getChannelName())
                        || (ch != null && (sourceChannel.equals(ch.getChannelName()) || sourceChannel.equals(ch.getChannelCode())));
                if (!hit) return false;
            }
            if (!orgOffice.isEmpty() && !orgOffice.equals(nz(p.getBureauOffice(), ""))) return false;
            if (!projectType.isEmpty() && !projectType.equals(nz(p.getProjectType(), ""))) return false;
            if (!major1.isEmpty() && !major1.equals(nz(p.getMajor1(), ""))) return false;
            if (!major2.isEmpty() && !major2.equals(nz(p.getMajor2(), ""))) return false;
            if (!unit.isEmpty()) {
                boolean hit = unit.equals(String.valueOf(p.getOrgId()))
                        || unit.equals(String.valueOf(p.getLeadOrgId()))
                        || unit.equals(p.getOrgName())
                        || unit.equals(p.getLeadOrgName())
                        || unit.equals(shortUnit(p.getOrgName()))
                        || unit.equals(shortUnit(p.getLeadOrgName()));
                if (!hit) return false;
            }
            return true;
        }).collect(Collectors.toList());
    }

    private String dataMode(List<ProjInfo> list) {
        boolean plat = list.stream().anyMatch(p -> p.getDataSource() == null || "PLATFORM".equals(p.getDataSource()));
        boolean form = list.stream().anyMatch(p -> "FORM_MAINT".equals(p.getDataSource()));
        if (plat && form) return "mixed";
        if (form) return "form-ledger";
        return "projects";
    }

    private boolean overlapsYear(ProjInfo p, int year) {
        int sy = p.getStartDate() == null ? 0 : p.getStartDate().getYear();
        int ey = p.getEndDate() == null ? 9999 : p.getEndDate().getYear();
        return sy <= year && ey >= year;
    }

    private int durationYears(ProjInfo p) {
        if (p.getStartDate() == null || p.getEndDate() == null) return 1;
        return Math.max(1, p.getEndDate().getYear() - p.getStartDate().getYear() + 1);
    }

    static String stdStatus(String status) {
        String s = status == null ? "" : status;
        if (s.contains("终止") || s.contains("中止") || "TERMINATED".equals(s)) return "已终止";
        if (s.contains("已验收") || s.contains("验收完成") || s.contains("结题")
                || "COMPANY_ACCEPTED".equals(s) || "GOV_ACCEPTED".equals(s) || "FINISHED".equals(s)) return "已验收";
        if (s.contains("验收") || "ACCEPTING".equals(s)) return "验收中";
        if (s.contains("立项") || "FILING".equals(s)) return "立项中";
        if (s.contains("申报") || s.contains("草稿") || "DRAFT".equals(s) || "DECLARING".equals(s)) return "申报中";
        return "实施中";
    }

    static boolean isRunning(String status) {
        String std = stdStatus(status);
        return "实施中".equals(std) || "验收中".equals(std) || (status != null && status.contains("进行中"));
    }

    private String normalizeLevel(String v) {
        String s = str(v);
        if ("国家级".equals(s) || "NATIONAL".equals(s)) return "NATIONAL";
        if ("地方级".equals(s) || "LOCAL".equals(s)) return "LOCAL";
        if ("公司级".equals(s) || "COMPANY".equals(s)) return "COMPANY";
        return s;
    }

    private Map<String, Object> pkgAsDeliv(String name, String way, List<AchvTransform> transforms) {
        List<AchvTransform> rows = transforms.stream().filter(t -> way.equals(t.getTransformWay())).collect(Collectors.toList());
        long done = rows.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("code", way);
        m.put("delivered", done);
        m.put("pending", rows.size() - done);
        m.put("total", rows.size());
        return m;
    }

    private String parseModelTarget(String intro, String form) {
        if (intro != null) {
            Matcher m = Pattern.compile("应用对象[：:]\\s*([^\\s，,；;]+)").matcher(intro);
            if (m.find()) return m.group(1);
        }
        if ("INSTALLED".equals(form)) return "型号装机";
        if ("UNINSTALLED".equals(form)) return "型号未装机";
        return "未明确对象";
    }

    private Map<String, Object> risk(String id, Long projectId, String projectName, String type, String title, LocalDate due, String color) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("projectId", projectId);
        m.put("projectName", projectName);
        m.put("type", type);
        m.put("title", title);
        m.put("dueDate", due == null ? null : due.toString());
        m.put("remain", due == null ? 0 : (int) ChronoUnit.DAYS.between(LocalDate.now(), due));
        m.put("color", color);
        return m;
    }

    private Map<String, Object> unitSeed(String name, Long orgId) {
        Map<String, Object> cur = new LinkedHashMap<>();
        cur.put("name", name);
        cur.put("orgId", orgId);
        cur.put("count", 0);
        cur.put("fund", 0d);
        cur.put("red", 0);
        cur.put("yellow", 0);
        cur.put("blue", 0);
        cur.put("green", 0);
        cur.put("running", 0);
        cur.put("accepted", 0);
        Map<String, Integer> levels = new HashMap<>();
        levels.put("NATIONAL", 0);
        levels.put("LOCAL", 0);
        levels.put("COMPANY", 0);
        cur.put("levels", levels);
        return cur;
    }

    private Map<String, Object> channelNode(String name, int count, double fund) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("count", count);
        m.put("value", count);
        m.put("fund", round2(fund));
        return m;
    }

    private Map<String, Object> node(String name, int value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", value);
        return m;
    }

    private <T> List<T> filterByIds(List<T> list, Set<Long> ids, java.util.function.Function<T, Long> getter) {
        if (ids.isEmpty()) return new ArrayList<>();
        return list.stream().filter(x -> {
            Long id = getter.apply(x);
            return id != null && ids.contains(id);
        }).collect(Collectors.toList());
    }

    private double sum(List<ProjInfo> list, java.util.function.Function<ProjInfo, BigDecimal> getter) {
        return list.stream().mapToDouble(p -> n(getter.apply(p))).sum();
    }

    private double n(BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    private double round2(double v) {
        return roundScale(v, 2);
    }

    private double roundScale(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private double rate(double a, double b) {
        if (b == 0) return 0;
        return round2(a / b * 100);
    }

    private String stripMajor(String name) {
        if (name == null) return "未填专业";
        return name.replaceFirst("^\\d{2,4}-", "");
    }

    private String shortUnit(String name) {
        if (name == null) return "未填单位";
        return name.replace("上海飞机设计研究院", "上飞院")
                .replace("上海飞机制造有限公司", "上飞公司")
                .replace("北京民用飞机技术研究中心", "北研中心")
                .replace("中国商飞总部", "总部");
    }

    private String first(String a, String b, String def) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return def;
    }

    private String nz(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    private String str(String v) {
        return v == null ? "" : v.trim();
    }

    private Integer parseInt(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
