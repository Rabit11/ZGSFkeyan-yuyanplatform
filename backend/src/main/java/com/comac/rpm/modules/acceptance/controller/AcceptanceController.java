package com.comac.rpm.modules.acceptance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.R;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.modules.acceptance.entity.ProjAcceptance;
import com.comac.rpm.modules.acceptance.entity.ProjAcceptanceItem;
import com.comac.rpm.modules.acceptance.mapper.ProjAcceptanceMapper;
import com.comac.rpm.modules.acceptance.mapper.ProjAcceptanceItemMapper;
import com.comac.rpm.modules.deliverable.entity.ProjDeliverable;
import com.comac.rpm.modules.deliverable.mapper.ProjDeliverableMapper;
import com.comac.rpm.modules.fund.entity.FundPayment;
import com.comac.rpm.modules.fund.mapper.FundPaymentMapper;
import com.comac.rpm.modules.evaluation.entity.ProjEvaluation;
import com.comac.rpm.modules.evaluation.mapper.ProjEvaluationMapper;
import com.comac.rpm.modules.milestone.entity.ProjMilestone;
import com.comac.rpm.modules.milestone.mapper.ProjMilestoneMapper;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目验收：前置条件强校验 + 表单智能分级锁定
 * <p>
 * 国家级 → 单位/公司/国家三级；地方级 → 单位/属地主管部门两级；公司级 → 单位/公司两级；
 * 非对应层级材料栏 locked=1，前端锁定不可编辑。
 */
@RestController
@RequestMapping("/api/acceptance")
public class AcceptanceController {

    /** 各验收层级对应的材料清单 */
    private static final Map<String, String[]> LEVEL_MATERIALS = new LinkedHashMap<>();

    static {
        LEVEL_MATERIALS.put("UNIT", new String[]{"验收申请书", "技术总结报告", "经费决算表", "交付物清单"});
        LEVEL_MATERIALS.put("COMPANY", new String[]{"公司级验收申请表", "评审专家意见", "验收结论"});
        LEVEL_MATERIALS.put("NATIONAL", new String[]{"国家级验收申请", "主管机关批复", "综合绩效评价材料"});
        LEVEL_MATERIALS.put("LOCAL", new String[]{"属地验收申请", "科委验收意见", "综合绩效评价材料"});
    }

    @Autowired
    private ProjAcceptanceMapper acceptanceMapper;
    @Autowired
    private ProjAcceptanceItemMapper itemMapper;
    @Autowired
    private ProjInfoMapper projectMapper;
    @Autowired
    private ProjMilestoneMapper milestoneMapper;
    @Autowired
    private ProjDeliverableMapper deliverableMapper;
    @Autowired
    private FundPaymentMapper paymentMapper;
    @Autowired
    private ProjEvaluationMapper evaluationMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;
    @Autowired private com.comac.rpm.modules.file.MinioStorageService storage;

    @GetMapping("/{projectId}")
    public R<Map<String, Object>> detail(@PathVariable("projectId") Long projectId) {
        flowAuditGuard.requireProjectAccess(projectId);
        ProjInfo p = projectMapper.selectById(projectId);
        if (p == null) {
            throw new BusinessException("项目不存在");
        }
        ProjAcceptance acc = acceptanceMapper.selectOne(
                new LambdaQueryWrapper<ProjAcceptance>().eq(ProjAcceptance::getProjectId, projectId).last("LIMIT 1"));
        if (acc == null) {
            acc = new ProjAcceptance();
            acc.setProjectId(projectId);
            acc.setStatus("NOT_STARTED");
            acc.setAcceptLevel(resolveLevel(p.getLevelCode()));
            acc.setExpertReview("NATIONAL".equals(p.getLevelCode()) ? 1 : 0);
            acc.setCurrentNode("ACCEPT_GATE");
            acceptanceMapper.insert(acc);
        }
        List<ProjAcceptanceItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ProjAcceptanceItem>().eq(ProjAcceptanceItem::getAcceptanceId, acc.getId())
                        .orderByAsc(ProjAcceptanceItem::getId));
        if (items.isEmpty()) {
            items = initItems(acc.getId(), p.getLevelCode());
        }
        Map<String, Object> map = new HashMap<>();
        map.putAll(toMap(acc));
        map.put("items", items);
        boolean editable = "NOT_STARTED".equals(acc.getStatus()) || "ACCEPT_APPLY".equals(acc.getCurrentNode());
        map.put("canEditMaterials", editable && canAct(projectId, "owner", "techLead", "projectPm", "contactLogin"));
        map.put("canSubmit", editable && canAct(projectId, "owner"));
        String[] auditActors = auditActors(acc.getCurrentNode());
        map.put("canAudit", !"DONE".equals(acc.getStatus()) && auditActors.length > 0
                && canAct(projectId, auditActors)
                && !FlowAuditGuard.matchesOwnerLabel(p.getOwnerName(), flowAuditGuard.currentUser()));
        return R.ok(map);
    }

    /**
     * 验收办结结果包：供后续成果验收 / 成果转化模块读取。
     * 本接口只暴露项目验收形成的结论、材料与已交付成果入口，不反向办理后续模块业务。
     */
    @GetMapping("/{projectId}/result-handoff")
    public R<Map<String, Object>> resultHandoff(@PathVariable("projectId") Long projectId) {
        flowAuditGuard.requireProjectAccess(projectId);
        ProjInfo p = projectMapper.selectById(projectId);
        if (p == null) {
            throw new BusinessException("项目不存在");
        }
        ProjAcceptance acc = acceptanceMapper.selectOne(
                new LambdaQueryWrapper<ProjAcceptance>().eq(ProjAcceptance::getProjectId, projectId).last("LIMIT 1"));
        List<ProjAcceptanceItem> materials = acc == null ? Collections.emptyList() : itemMapper.selectList(
                new LambdaQueryWrapper<ProjAcceptanceItem>().eq(ProjAcceptanceItem::getAcceptanceId, acc.getId())
                        .orderByAsc(ProjAcceptanceItem::getLevelCode)
                        .orderByAsc(ProjAcceptanceItem::getSort));
        List<ProjDeliverable> deliverables = deliverableMapper.selectList(
                new LambdaQueryWrapper<ProjDeliverable>().eq(ProjDeliverable::getProjectId, projectId)
                        .orderByAsc(ProjDeliverable::getId));
        List<ProjDeliverable> delivered = deliverables.stream()
                .filter(d -> "DELIVERED".equals(d.getStatus()))
                .collect(Collectors.toList());
        Set<String> achievementNos = new LinkedHashSet<>();
        for (ProjDeliverable d : delivered) {
            if (d.getAchievementNo() != null && !d.getAchievementNo().isBlank()) {
                achievementNos.add(d.getAchievementNo());
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("projectId", p.getId());
        m.put("projectNo", p.getProjectNo());
        m.put("projectName", p.getName());
        m.put("acceptanceId", acc == null ? null : acc.getId());
        m.put("acceptLevel", acc == null ? null : acc.getAcceptLevel());
        m.put("acceptanceStatus", acc == null ? "NOT_STARTED" : acc.getStatus());
        m.put("currentNode", acc == null ? "ACCEPT_GATE" : acc.getCurrentNode());
        m.put("acceptedAt", acc == null ? null : acc.getFinishAt());
        m.put("conclusion", acc == null ? null : acc.getConclusion());
        m.put("partnerDueDate", acc == null ? null : acc.getPartnerDueDate());
        m.put("resultReady", acc != null && "DONE".equals(acc.getStatus()));
        m.put("nextBiz", "ACHIEVEMENT_ACCEPTANCE");
        m.put("nextBizStatus", acc != null && "DONE".equals(acc.getStatus()) ? "READY" : "WAIT_ACCEPTANCE_DONE");
        m.put("materialCount", materials.size());
        m.put("deliverableCount", deliverables.size());
        m.put("deliveredDeliverableCount", delivered.size());
        m.put("acceptanceMaterials", materials);
        m.put("deliveredDeliverables", delivered);
        m.put("achievementNos", achievementNos);
        return R.ok(m);
    }

    /**
     * 发起前置校验：里程碑全闭环、核心交付物齐套、经费核销与凭证归档、不合格评估整改
     */
    @PostMapping("/{projectId}/check")
    public R<List<Map<String, Object>>> check(@PathVariable("projectId") Long projectId) {
        flowAuditGuard.requireProjectAccess(projectId);
        List<Map<String, Object>> result = new ArrayList<>();

        long msOpen = milestoneMapper.selectCount(new LambdaQueryWrapper<ProjMilestone>()
                .eq(ProjMilestone::getProjectId, projectId)
                .ne(ProjMilestone::getStatus, "DONE"));
        result.add(item("MILESTONE", "里程碑全闭环", msOpen == 0,
                msOpen == 0 ? "全部里程碑已完成销项" : "仍有 " + msOpen + " 个里程碑未完成闭环"));

        List<ProjDeliverable> dvs = deliverableMapper.selectList(
                new LambdaQueryWrapper<ProjDeliverable>().eq(ProjDeliverable::getProjectId, projectId));
        long dvOverdue = dvs.stream().filter(d -> "OVERDUE".equals(d.getStatus())).count();
        long dvDone = dvs.stream().filter(d -> "DELIVERED".equals(d.getStatus())).count();
        boolean coreOk = dvOverdue == 0 && (dvs.isEmpty() || dvDone == dvs.size());
        result.add(item("CORE_DV", "核心交付物齐套", coreOk,
                coreOk ? "核心交付物已交付 " + dvDone + "/" + dvs.size()
                        : "已交付 " + dvDone + "/" + dvs.size() + "，逾期 " + dvOverdue + " 项"));

        List<FundPayment> pays = paymentMapper.selectList(
                new LambdaQueryWrapper<FundPayment>().eq(FundPayment::getProjectId, projectId));
        long payOpen = pays.stream().filter(p -> p.getWriteoffStatus() != null && !"WRITTEN".equals(p.getWriteoffStatus())).count();
        long written = pays.stream().filter(p -> "WRITTEN".equals(p.getWriteoffStatus())).count();
        java.math.BigDecimal amt = pays.stream()
                .filter(p -> "WRITTEN".equals(p.getWriteoffStatus()) && p.getAmount() != null)
                .map(FundPayment::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        result.add(item("FUND", "经费核销与凭证归档", payOpen == 0,
                payOpen == 0 ? "已归档 " + written + " 张凭证，累计 " + amt + " 万元"
                        : "存在 " + payOpen + " 笔经费未核销"));

        long evalOpen = evaluationMapper.selectCount(new LambdaQueryWrapper<ProjEvaluation>()
                .eq(ProjEvaluation::getProjectId, projectId)
                .eq(ProjEvaluation::getResult, "FAIL")
                .ne(ProjEvaluation::getStatus, "DONE"));
        result.add(item("EVAL_RECTIFY", "不合格评估整改", evalOpen == 0,
                evalOpen == 0 ? "无待整改不合格项" : "仍有 " + evalOpen + " 项不合格评估未整改闭环"));
        return R.ok(result);
    }

    /** 提交验收申请：前置校验未通过则拒绝 */
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/{projectId}/submit")
    public R<Boolean> submit(@PathVariable("projectId") Long projectId) {
        flowAuditGuard.requireActors(projectId, "提交验收申请", "owner");
        List<Map<String, Object>> checks = check(projectId).getData();
        for (Map<String, Object> c : checks) {
            if (!Boolean.TRUE.equals(c.get("passed"))) {
                throw new BusinessException("前置条件未满足：" + c.get("label") + "，" + c.get("message"));
            }
        }
        ProjAcceptance acc = getOrCreate(projectId);
        if (!"NOT_STARTED".equals(acc.getStatus()) && !"ACCEPT_APPLY".equals(acc.getCurrentNode())) {
            throw new BusinessException(409, "验收已提交或已办结，不可重复提交");
        }
        List<ProjAcceptanceItem> missing = itemMapper.selectList(new LambdaQueryWrapper<ProjAcceptanceItem>()
                .eq(ProjAcceptanceItem::getAcceptanceId, acc.getId())
                .eq(ProjAcceptanceItem::getRequired, 1)
                .eq(ProjAcceptanceItem::getLocked, 0)
                .and(q -> q.isNull(ProjAcceptanceItem::getFileUrl).or()
                        .eq(ProjAcceptanceItem::getStatus, "EMPTY")));
        if (!missing.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (ProjAcceptanceItem item : missing) {
                if (names.length() > 0) {
                    names.append("、");
                }
                names.append(item.getMaterialName());
            }
            throw new BusinessException("验收材料未齐套，请先上传：" + names);
        }
        for(var material:itemMapper.selectList(new LambdaQueryWrapper<ProjAcceptanceItem>().eq(ProjAcceptanceItem::getAcceptanceId,acc.getId()).eq(ProjAcceptanceItem::getRequired,1).eq(ProjAcceptanceItem::getLocked,0)))requireStoredMaterial(material.getFileUrl());
        acc.setStatus("APPLYING");
        acc.setCurrentNode("ACCEPT_UNIT_REVIEW");
        acc.setLatestOpinion("项目团队已提交验收申请");
        acc.setLatestProcessAt(LocalDateTime.now());
        acc.setApplyAt(LocalDateTime.now());
        acceptanceMapper.updateById(acc);
        return R.ok(true);
    }

    /** 上传分级验收材料 */
    @PostMapping("/{projectId}/materials")
    public R<Boolean> upload(@PathVariable("projectId") Long projectId, @RequestBody Map<String, Object> body) {
        flowAuditGuard.requireActors(projectId, "上传验收材料", "owner", "techLead", "projectPm", "contactLogin");
        String fieldCode = body.get("fieldCode") == null ? null : String.valueOf(body.get("fieldCode"));
        ProjAcceptance acc = getOrCreate(projectId);
        if(!"NOT_STARTED".equals(acc.getStatus()) && !"ACCEPT_APPLY".equals(acc.getCurrentNode()))throw new BusinessException(403,"审核中或已办结材料不可修改");
        requireStoredMaterial(body.get("fileUrl"));
        ProjAcceptanceItem it = itemMapper.selectOne(new LambdaQueryWrapper<ProjAcceptanceItem>()
                .eq(ProjAcceptanceItem::getAcceptanceId, acc.getId())
                .eq(ProjAcceptanceItem::getFieldCode, fieldCode)
                .last("LIMIT 1"));
        if (it == null) {
            throw new BusinessException("材料栏位不存在");
        }
        if (Integer.valueOf(1).equals(it.getLocked())) {
            throw new BusinessException("该验收层级不适用，页面已锁定");
        }
        String fileName = body.get("fileName") == null ? it.getMaterialName() + ".pdf" : String.valueOf(body.get("fileName"));
        it.setFileName(fileName);
        it.setFileUrl(body.get("fileUrl") == null ? "/files/" + fieldCode + ".pdf" : String.valueOf(body.get("fileUrl")));
        if (body.get("fileSize") != null) {
            try {
                it.setFileSize(Long.valueOf(String.valueOf(body.get("fileSize"))));
            } catch (NumberFormatException ignored) {
                it.setFileSize(null);
            }
        }
        it.setUploadedBy(UserContext.getUsername());
        it.setUploadedAt(LocalDateTime.now());
        it.setStatus("UPLOADED");
        itemMapper.updateById(it);
        return R.ok(true);
    }

    /** 验收审核：按当前节点通过/退回，终审通过后办结并开启 30 天协作单位评价倒计时 */
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/{projectId}/audit")
    public R<Boolean> audit(@PathVariable("projectId") Long projectId, @RequestBody(required = false) Map<String, Object> body) {
        flowAuditGuard.requireActors(projectId, "验收审核", "unitHead", "hqHead", "hqStaff", "chief1", "chief2");
        boolean pass = body != null && Boolean.TRUE.equals(body.get("pass"));
        String opinion = body == null || body.get("opinion") == null ? null : String.valueOf(body.get("opinion")).trim();
        if (!pass && (opinion == null || opinion.isEmpty())) {
            throw new BusinessException("退回时必须填写意见");
        }
        ProjAcceptance acc = getOrCreate(projectId);
        if ("DONE".equals(acc.getStatus())) {
            throw new BusinessException("项目验收已办结");
        }
        String node = acc.getCurrentNode() == null ? "ACCEPT_UNIT_REVIEW" : acc.getCurrentNode();
        String[] actors = auditActors(node);
        if (actors.length == 0) throw new BusinessException(409, "当前节点尚未进入审核，请先提交申请");
        flowAuditGuard.requireActors(projectId,"当前验收节点审核",actors);
        if(FlowAuditGuard.matchesOwnerLabel(projectMapper.selectById(projectId).getOwnerName(),flowAuditGuard.currentUser()))throw new BusinessException(403,"不能审核本人提交的项目");
        String requestedNode = body == null || body.get("nodeCode") == null ? null : String.valueOf(body.get("nodeCode"));
        if (requestedNode != null && !requestedNode.isEmpty() && !requestedNode.equals(node)) {
            throw new BusinessException("验收流程节点已变化，请刷新后重试");
        }
        if (!pass) {
            acc.setStatus("APPLYING");
            acc.setCurrentNode("ACCEPT_APPLY");
            acc.setLatestOpinion("退回：" + opinion);
            acc.setLatestProcessAt(LocalDateTime.now());
            acceptanceMapper.updateById(acc);
            return R.ok(true);
        }
        for (Map<String, Object> c : check(projectId).getData()) {
            if (!Boolean.TRUE.equals(c.get("passed"))) {
                throw new BusinessException("验收门槛已变化：" + c.get("label") + "，" + c.get("message"));
            }
        }

        if ("ACCEPT_UNIT_REVIEW".equals(node)) {
            if (Integer.valueOf(1).equals(acc.getExpertReview())) {
                acc.setStatus("ACCEPTING");
                acc.setCurrentNode("ACCEPT_CHIEF_REVIEW");
            } else {
                acc.setStatus("ACCEPTING");
                acc.setCurrentNode("ACCEPT_HQ_TECH");
            }
            acc.setLatestOpinion(emptyDefault(opinion, "初审通过"));
        } else if ("ACCEPT_CHIEF_REVIEW".equals(node)) {
            acc.setStatus("ACCEPTING");
            acc.setCurrentNode("ACCEPT_HQ_TECH");
            acc.setLatestOpinion(emptyDefault(opinion, "责任总师技术复核通过"));
        } else if ("ACCEPT_HQ_TECH".equals(node)) {
            acc.setStatus("ACCEPTING");
            acc.setCurrentNode("ACCEPT_HQ_FINAL");
            acc.setLatestOpinion(emptyDefault(opinion, "技术初审通过"));
        } else if ("ACCEPT_HQ_FINAL".equals(node)) {
            acc.setStatus("DONE");
            acc.setCurrentNode("ACCEPT_ARCHIVE");
            acc.setFinishAt(LocalDateTime.now());
            acc.setPartnerDueDate(LocalDate.now().plusDays(30));
            acc.setConclusion(emptyDefault(opinion, "验收合格，同意办结"));
            acc.setLatestOpinion(acc.getConclusion());
            syncProjectAcceptState(projectId, acc);
        } else if ("ACCEPT_APPLY".equals(node)) {
            acc.setStatus("APPLYING");
            acc.setCurrentNode("ACCEPT_UNIT_REVIEW");
            acc.setLatestOpinion(emptyDefault(opinion, "补正材料已提交"));
        } else {
            throw new BusinessException("当前节点不可审核：" + node);
        }
        acc.setLatestProcessAt(LocalDateTime.now());
        acceptanceMapper.updateById(acc);
        return R.ok(true);
    }

    private ProjAcceptance getOrCreate(Long projectId) {
        ProjAcceptance acc = acceptanceMapper.selectOne(
                new LambdaQueryWrapper<ProjAcceptance>().eq(ProjAcceptance::getProjectId, projectId).last("LIMIT 1"));
        if (acc == null) {
            detail(projectId);
            acc = acceptanceMapper.selectOne(
                    new LambdaQueryWrapper<ProjAcceptance>().eq(ProjAcceptance::getProjectId, projectId).last("LIMIT 1"));
        }
        return acc;
    }

    private List<ProjAcceptanceItem> initItems(Long acceptanceId, String levelCode) {
        List<String> allow = allowedLevels(levelCode);
        List<ProjAcceptanceItem> list = new ArrayList<>();
        for (Map.Entry<String, String[]> e : LEVEL_MATERIALS.entrySet()) {
            boolean applicable = allow.contains(e.getKey());
            String[] mats = e.getValue();
            for (int i = 0; i < mats.length; i++) {
                ProjAcceptanceItem it = new ProjAcceptanceItem();
                it.setAcceptanceId(acceptanceId);
                it.setLevelCode(e.getKey());
                it.setLevelName(levelName(e.getKey()));
                it.setFieldCode(e.getKey() + "_" + i);
                it.setMaterialName(mats[i]);
                it.setRequired(applicable ? 1 : 0);
                it.setLocked(applicable ? 0 : 1);
                it.setStatus("EMPTY");
                it.setSort(i);
                itemMapper.insert(it);
                list.add(it);
            }
        }
        return list;
    }

    private List<String> allowedLevels(String levelCode) {
        List<String> allow = new ArrayList<>();
        if ("NATIONAL".equals(levelCode)) {
            allow.add("UNIT");
            allow.add("COMPANY");
            allow.add("NATIONAL");
        } else if ("LOCAL".equals(levelCode)) {
            allow.add("UNIT");
            allow.add("LOCAL");
        } else {
            allow.add("UNIT");
            allow.add("COMPANY");
        }
        return allow;
    }

    private String resolveLevel(String levelCode) {
        return "NATIONAL".equals(levelCode) ? "NATIONAL" : "LOCAL".equals(levelCode) ? "LOCAL" : "COMPANY";
    }

    private String levelName(String code) {
        switch (code) {
            case "UNIT":
                return "单位级验收";
            case "COMPANY":
                return "公司级验收";
            case "NATIONAL":
                return "国家级验收";
            case "LOCAL":
                return "属地主管部门验收";
            default:
                return code;
        }
    }

    private Map<String, Object> toMap(ProjAcceptance a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("projectId", a.getProjectId());
        m.put("acceptLevel", a.getAcceptLevel());
        m.put("status", a.getStatus());
        m.put("currentNode", a.getCurrentNode());
        m.put("latestOpinion", a.getLatestOpinion());
        m.put("latestProcessAt", a.getLatestProcessAt());
        m.put("applyAt", a.getApplyAt());
        m.put("finishAt", a.getFinishAt());
        m.put("conclusion", a.getConclusion());
        m.put("expertReview", a.getExpertReview());
        m.put("partnerDueDate", a.getPartnerDueDate());
        return m;
    }

    private Map<String, Object> item(String key, String label, boolean passed, String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("passed", passed);
        m.put("message", message);
        return m;
    }

    private String emptyDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private void syncProjectAcceptState(Long projectId, ProjAcceptance acc) {
        ProjInfo p = projectMapper.selectById(projectId);
        if (p == null) {
            return;
        }
        p.setAcceptStatus(levelName(acc.getAcceptLevel()));
        if ("COMPANY".equals(acc.getAcceptLevel())) {
            p.setStatus("COMPANY_ACCEPTED");
        } else if ("NATIONAL".equals(acc.getAcceptLevel()) || "LOCAL".equals(acc.getAcceptLevel())) {
            p.setStatus("GOV_ACCEPTED");
        }
        projectMapper.updateById(p);
    }
    private boolean canAct(Long projectId, String... actors) {
        try {
            flowAuditGuard.requireActors(projectId, "验收办理", actors);
            return true;
        } catch (BusinessException denied) {
            return false;
        }
    }

    private String[] auditActors(String node) {
        return switch (node == null ? "" : node) {
            case "ACCEPT_UNIT_REVIEW" -> new String[]{"unitHead"};
            case "ACCEPT_CHIEF_REVIEW" -> new String[]{"chief1", "chief2"};
            case "ACCEPT_HQ_TECH" -> new String[]{"hqStaff"};
            case "ACCEPT_HQ_FINAL" -> new String[]{"hqHead"};
            default -> new String[0];
        };
    }

    private void requireStoredMaterial(Object address) {
        String url=java.util.Objects.toString(address,"");String prefix="/api/files/download?objectKey=";
        if(!url.startsWith(prefix))throw new BusinessException(400,"请先上传真实验收附件");
        String key=java.net.URLDecoder.decode(url.substring(prefix.length()),java.nio.charset.StandardCharsets.UTF_8);
        if(!key.startsWith("acceptance/") || key.contains("..") || !storage.exists(key))throw new BusinessException(400,"验收附件不存在或不属于验收材料");
    }

}
