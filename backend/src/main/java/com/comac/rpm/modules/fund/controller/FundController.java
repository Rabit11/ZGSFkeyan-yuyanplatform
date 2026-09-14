package com.comac.rpm.modules.fund.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.R;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.common.util.SeqUtil;
import com.comac.rpm.modules.fund.entity.FundBudget;
import com.comac.rpm.modules.fund.entity.FundPayment;
import com.comac.rpm.modules.fund.entity.HqFundBudget;
import com.comac.rpm.modules.fund.entity.HqFundQuota;
import com.comac.rpm.modules.fund.entity.HqFundTransfer;
import com.comac.rpm.modules.fund.mapper.FundBudgetMapper;
import com.comac.rpm.modules.fund.mapper.FundPaymentMapper;
import com.comac.rpm.modules.fund.mapper.HqFundBudgetMapper;
import com.comac.rpm.modules.fund.mapper.HqFundQuotaMapper;
import com.comac.rpm.modules.fund.mapper.HqFundTransferMapper;
import com.comac.rpm.modules.milestone.entity.ProjMilestone;
import com.comac.rpm.modules.milestone.mapper.ProjMilestoneMapper;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.project.entity.ProjTeamMember;
import com.comac.rpm.modules.project.mapper.ProjInfoMapper;
import com.comac.rpm.modules.project.mapper.ProjTeamMemberMapper;
import com.comac.rpm.modules.system.entity.SysUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 经费管理：两套相互独立、数据互不联动的管控体系
 * <p>
 * 1) 经费执行管理（项目级）：预算填报 + 年度预算核销，实时同步经费看板；
 * 2) 总部经费预算管控：年度预算编制、额度核定、经费拨付、年度清算。
 */
@RestController
@RequestMapping("/api")
public class FundController {

    @Autowired
    private FundBudgetMapper budgetMapper;
    @Autowired
    private FundPaymentMapper paymentMapper;
    @Autowired
    private HqFundBudgetMapper hqBudgetMapper;
    @Autowired
    private HqFundQuotaMapper quotaMapper;
    @Autowired
    private HqFundTransferMapper transferMapper;
    @Autowired
    private ProjMilestoneMapper milestoneMapper;
    @Autowired
    private ProjInfoMapper projectMapper;
    @Autowired
    private ProjTeamMemberMapper memberMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;

    /* ==================== 经费执行管理（项目级） ==================== */

    @GetMapping("/projects/{projectId}/fund/budgets")
    public R<Object> budgets(@PathVariable("projectId") Long projectId) {
        return R.ok(budgetMapper.selectList(
                new LambdaQueryWrapper<FundBudget>().eq(FundBudget::getProjectId, projectId)));
    }

    @GetMapping("/projects/{projectId}/fund/payments")
    public R<Object> payments(@PathVariable("projectId") Long projectId) {
        return R.ok(paymentMapper.selectList(
                new LambdaQueryWrapper<FundPayment>().eq(FundPayment::getProjectId, projectId)
                        .orderByDesc(FundPayment::getOccurDate)));
    }

    @GetMapping("/fund/reviews/pending")
    public R<Object> pendingFundReviews() {
        SysUser user = flowAuditGuard.currentUser();
        String identity = user.getIdentityCode() == null ? "" : user.getIdentityCode();
        boolean admin = "admin".equals(identity);
        boolean canUnitBudget = admin || "finHead".equals(identity) || "finStaff".equals(identity);
        boolean canUnitFinal = admin || "finHead".equals(identity);
        boolean canUnitWriteoff = admin || "finHead".equals(identity);
        boolean canHqFund = admin || "finHq".equals(identity);
        if (!canUnitBudget && !canUnitFinal && !canUnitWriteoff && !canHqFund) {
            return R.ok(List.of());
        }

        Map<Long, ProjInfo> projectCache = new HashMap<>();
        LinkedHashMap<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (FundBudget budget : budgetMapper.selectList(null)) {
            Long projectId = budget.getProjectId();
            if (projectId == null) {
                continue;
            }
            String status = budget.getStatus() == null ? "" : budget.getStatus();
            boolean isFinal = "项目经费总核".equals(budget.getMilestoneName());
            ProjInfo project = projectCache.computeIfAbsent(projectId, projectMapper::selectById);
            if (project == null) {
                continue;
            }
            if (isFinal && "FINAL_PENDING".equals(status)
                    && canUnitFinal && canActOnProject(user, project, "finHead")) {
                pushFundReview(grouped, "fund-final-" + projectId, project, "待二级单位财务负责人总核",
                        "经费总核", "final", "final", "项目经费总核");
            } else if (isFinal && "FINAL_UNIT_OK".equals(status)
                    && canHqFund && canActOnProject(user, project, "finHq")) {
                pushFundReview(grouped, "fund-final-hq-" + projectId, project, "待总部财务主管总核",
                        "总核复核", "final", "final", "项目经费总核");
            } else if (!isFinal && "PENDING".equals(status)
                    && canUnitBudget && canActOnProject(user, project, "finHead", "finStaff")) {
                pushFundReview(grouped, "fund-budget-unit-" + projectId, project, "待二级单位财务审核",
                        "预算审核", "budget", "unit-budget", budget.getMilestoneName());
            } else if (!isFinal && "UNIT_OK".equals(status)
                    && canHqFund && canActOnProject(user, project, "finHq")) {
                pushFundReview(grouped, "fund-budget-hq-" + projectId, project, "待总部财务复核备案",
                        "预算复核", "budget", "hq-budget", budget.getMilestoneName());
            } else if (!isFinal && "APPROVED".equals(status)
                    && canUnitWriteoff && canActOnProject(user, project, "finHead")
                    && needsWriteoffUpload(projectId, budget.getMilestoneId())) {
                pushFundReview(grouped, "fund-writeoff-upload-" + projectId, project, "二级单位财务上传付款凭证并完成本级核销",
                        "核销填报", "writeoff", "writeoff-upload", budget.getMilestoneName());
            }
        }
        for (FundPayment payment : paymentMapper.selectList(null)) {
            Long projectId = payment.getProjectId();
            if (projectId == null) {
                continue;
            }
            String status = payment.getWriteoffStatus() == null ? "" : payment.getWriteoffStatus();
            ProjInfo project = projectCache.computeIfAbsent(projectId, projectMapper::selectById);
            if (project == null) {
                continue;
            }
            if (("PENDING".equals(status) || "UNIT_OK".equals(status))
                    && canUnitWriteoff && canActOnProject(user, project, "finHead")) {
                pushFundReview(grouped, "fund-writeoff-unit-" + projectId, project, "待二级单位财务完成本级核销",
                        "核销办理", "writeoff", "writeoff", payment.getVoucherNo());
            }
        }
        return R.ok(new ArrayList<>(grouped.values()));
    }

    @PostMapping("/fund/budgets")
    public R<Long> createBudget(@RequestBody FundBudget body) {
        body.setId(null);
        requireAllMilestonesClosed(body.getProjectId());
        if (body.getStatus() == null) {
            body.setStatus("PENDING");
        }
        if ("PENDING".equals(body.getStatus()) || "UNIT_AUDIT".equals(body.getStatus())) {
            flowAuditGuard.requireActors(body.getProjectId(), "经费预算提交", "owner");
        } else {
            flowAuditGuard.requireActors(body.getProjectId(), "经费预算暂存",
                    "owner", "techLead", "projectPm", "contactLogin");
        }
        budgetMapper.insert(body);
        return R.ok(body.getId());
    }

    @PutMapping("/fund/budgets/{id}")
    public R<Boolean> updateBudget(@PathVariable("id") Long id, @RequestBody FundBudget body) {
        body.setId(id);
        FundBudget exist = budgetMapper.selectById(id);
        Long projectId = body.getProjectId() != null ? body.getProjectId()
                : (exist == null ? null : exist.getProjectId());
        requireAllMilestonesClosed(projectId);
        if ("UNIT_OK".equals(body.getStatus())) {
            flowAuditGuard.requireActors(projectId, "二级单位财务审核", "finHead", "finStaff");
        } else if ("FINAL_UNIT_OK".equals(body.getStatus())) {
            flowAuditGuard.requireActors(projectId, "二级单位财务负责人总核", "finHead");
        } else if ("FINAL_DONE".equals(body.getStatus())) {
            flowAuditGuard.requireActors(projectId, "总部财务主管总核复核", "finHq");
        } else if ("APPROVED".equals(body.getStatus())) {
            flowAuditGuard.requireActors(projectId, "总部财务复核", "finHq");
        } else if ("PENDING".equals(body.getStatus())) {
            flowAuditGuard.requireActors(projectId, "经费预算提交", "owner");
        } else if ("DRAFT".equals(body.getStatus())) {
            flowAuditGuard.requireActors(projectId, "经费预算暂存",
                    "owner", "techLead", "projectPm", "contactLogin");
        }
        budgetMapper.updateById(body);
        return R.ok(true);
    }

    @DeleteMapping("/fund/budgets/{id}")
    public R<Boolean> deleteBudget(@PathVariable("id") Long id) {
        FundBudget exist = budgetMapper.selectById(id);
        requireAllMilestonesClosed(exist == null ? null : exist.getProjectId());
        budgetMapper.deleteById(id);
        return R.ok(true);
    }

    @PostMapping("/fund/payments")
    public R<Long> createPayment(@RequestBody FundPayment body) {
        body.setId(null);
        requireAllMilestonesClosed(body.getProjectId());
        if (body.getFlowType() == null) {
            body.setFlowType("PAY");
        }
        if ("WRITEOFF".equals(body.getFlowType())) {
            if (body.getWriteoffStatus() == null) {
                body.setWriteoffStatus("WRITTEN");
            }
            if ("DRAFT".equals(body.getWriteoffStatus())) {
                flowAuditGuard.requireActors(body.getProjectId(), "二级单位财务上传付款凭证并完成本级核销",
                        "finHead");
            } else if ("PENDING".equals(body.getWriteoffStatus()) || "WRITTEN".equals(body.getWriteoffStatus())) {
                flowAuditGuard.requireActors(body.getProjectId(), "二级单位财务完成本级核销并同步总部经费看板",
                        "finHead");
                body.setWriteoffStatus("WRITTEN");
            } else {
                throw new BusinessException("当前核销状态不能提交：" + body.getWriteoffStatus());
            }
        } else {
            if (body.getWriteoffStatus() == null) {
                body.setWriteoffStatus("PENDING");
            }
            if ("PENDING".equals(body.getWriteoffStatus())) {
                flowAuditGuard.requireActors(body.getProjectId(), "经费核销提交", "owner");
            } else if ("DRAFT".equals(body.getWriteoffStatus())) {
                flowAuditGuard.requireActors(body.getProjectId(), "经费核销暂存",
                        "owner", "techLead", "projectPm", "contactLogin");
            } else {
                throw new BusinessException("核销完成须由二级单位财务办理");
            }
        }
        validatePayment(body, !"DRAFT".equals(body.getWriteoffStatus()));
        paymentMapper.insert(body);
        return R.ok(body.getId());
    }

    /** 核销办理：二级单位财务负责人完成本级核销后，数据自动同步总部经费看板。 */
    @PostMapping("/fund/payments/{id}/writeoff")
    public R<Boolean> writeoff(@PathVariable("id") Long id,
                               @RequestBody(required = false) Map<String, Object> body) {
        FundPayment exist = paymentMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("核销记录不存在");
        }
        Long projectId = exist.getProjectId();
        requireAllMilestonesClosed(projectId);
        boolean pass = body == null || !Boolean.FALSE.equals(body.get("pass"));
        FundPayment p = new FundPayment();
        p.setId(id);
        String status = exist.getWriteoffStatus();
        if ("PENDING".equals(status) || "UNIT_OK".equals(status)) {
            flowAuditGuard.requireActors(projectId, "二级单位财务完成本级核销", "finHead");
            if (pass) {
                validatePayment(exist, true);
            }
            p.setWriteoffStatus(pass ? "WRITTEN" : "DRAFT");
        } else {
            throw new BusinessException("当前核销状态不能审批：" + status);
        }
        paymentMapper.updateById(p);
        return R.ok(true);
    }

    private void validatePayment(FundPayment payment, boolean submitted) {
        BigDecimal amount = payment.getAmount();
        if (amount == null || amount.signum() < 0 || (submitted && amount.signum() == 0)) {
            throw new BusinessException("核销金额须为有效数字，正式提交须大于零");
        }
        Long milestoneId = payment.getBudgetId();
        ProjMilestone milestone = milestoneId == null ? null : milestoneMapper.selectById(milestoneId);
        if (milestone == null || !payment.getProjectId().equals(milestone.getProjectId())
                || !"DONE".equals(milestone.getStatus())) {
            throw new BusinessException("核销节点须属于当前项目且已完成闭环");
        }
        if (submitted) {
            String[] material = (payment.getRemark() == null ? "" : payment.getRemark()).split("\\|\\|", -1);
            if (payment.getOccurDate() == null || material.length < 3
                    || material[0].isBlank() || material[1].isBlank() || material[2].isBlank()) {
                throw new BusinessException("核销日期、用途与付款凭证为必填");
            }
        }
    }

    private boolean needsWriteoffUpload(Long projectId, Long milestoneId) {
        if (projectId == null || milestoneId == null) {
            return false;
        }
        List<FundPayment> rows = paymentMapper.selectList(new LambdaQueryWrapper<FundPayment>()
                .eq(FundPayment::getProjectId, projectId)
                .eq(FundPayment::getBudgetId, milestoneId));
        if (rows == null || rows.isEmpty()) {
            return true;
        }
        for (FundPayment row : rows) {
            if ("DRAFT".equals(row.getWriteoffStatus())) {
                return true;
            }
        }
        return false;
    }

    /** 项目级经费流程只能在该项目全部里程碑完成销项后执行。 */
    private void requireAllMilestonesClosed(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("缺少项目，无法校验里程碑闭环状态");
        }
        long total = milestoneMapper.selectCount(new LambdaQueryWrapper<ProjMilestone>()
                .eq(ProjMilestone::getProjectId, projectId));
        long open = milestoneMapper.selectCount(new LambdaQueryWrapper<ProjMilestone>()
                .eq(ProjMilestone::getProjectId, projectId)
                .and(q -> q.ne(ProjMilestone::getStatus, "DONE").or().isNull(ProjMilestone::getStatus)));
        if (total == 0) {
            throw new BusinessException("尚未编制里程碑节点，不能执行项目经费流程");
        }
        if (open > 0) {
            throw new BusinessException("须全部里程碑节点闭环后，才能执行项目经费流程（仍有 " + open + " 个节点未闭环）");
        }
    }

    private void pushFundReview(LinkedHashMap<String, Map<String, Object>> grouped,
                                String key,
                                ProjInfo project,
                                String node,
                                String tag,
                                String mode,
                                String desk,
                                String itemName) {
        Map<String, Object> row = grouped.computeIfAbsent(key, k -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("projectId", project.getId());
            m.put("projectNo", project.getProjectNo());
            m.put("projectName", project.getName());
            m.put("ownerName", project.getOwnerName());
            m.put("node", node);
            m.put("tag", tag);
            m.put("mode", mode);
            m.put("desk", desk);
            m.put("count", 0);
            m.put("itemNames", new ArrayList<String>());
            return m;
        });
        row.put("count", ((Number) row.get("count")).intValue() + 1);
        if (itemName != null && !itemName.isBlank()) {
            @SuppressWarnings("unchecked")
            List<String> names = (List<String>) row.get("itemNames");
            if (!names.contains(itemName)) {
                names.add(itemName);
            }
        }
    }

    private boolean canActOnProject(SysUser user, ProjInfo project, String... identityCodes) {
        String identity = user.getIdentityCode() == null ? "" : user.getIdentityCode();
        if ("admin".equals(identity)) {
            return true;
        }
        boolean identityMatched = false;
        for (String code : identityCodes) {
            if (code != null && code.equals(identity)) {
                identityMatched = true;
                break;
            }
        }
        if (!identityMatched) {
            return false;
        }
        List<ProjTeamMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjTeamMember>().eq(ProjTeamMember::getProjectId, project.getId()));
        for (String code : identityCodes) {
            ProjTeamMember named = findFundMember(members, code);
            if (named != null) {
                return samePerson(user, named);
            }
        }
        if ("finHq".equals(identity)) {
            return true;
        }
        if (project.getOrgId() != null && project.getOrgId().equals(user.getOrgId())) {
            return true;
        }
        String name = user.getRealName() == null ? "" : user.getRealName().trim();
        if (!name.isEmpty() && project.getOwnerName() != null && project.getOwnerName().contains(name)) {
            return true;
        }
        return sameOrgMember(user, members);
    }

    private ProjTeamMember findFundMember(List<ProjTeamMember> members, String identityCode) {
        if (members == null || members.isEmpty() || identityCode == null) {
            return null;
        }
        Set<String> keys = new HashSet<>();
        if ("finHq".equals(identityCode)) {
            keys.add("HQ_FINANCE");
            keys.add("总部财务主管");
        } else if ("finHead".equals(identityCode)) {
            keys.add("UNIT_FIN_MINISTER");
            keys.add("单位财务部长");
        } else if ("finStaff".equals(identityCode)) {
            keys.add("UNIT_FIN_SUPERVISOR");
            keys.add("单位财务主管");
        }
        for (ProjTeamMember m : members) {
            String code = m.getRoleCode() == null ? "" : m.getRoleCode();
            String name = m.getRoleName() == null ? "" : m.getRoleName();
            for (String key : keys) {
                if (key.equals(code) || key.equals(name) || name.contains(key)) {
                    return m;
                }
            }
        }
        return null;
    }

    private boolean sameOrgMember(SysUser user, List<ProjTeamMember> members) {
        if (members == null || members.isEmpty()) {
            return false;
        }
        String emp = digits(user.getEmployeeNo());
        String name = user.getRealName() == null ? "" : user.getRealName().trim();
        for (ProjTeamMember m : members) {
            String mEmp = digits(m.getEmployeeNo());
            if (!emp.isEmpty() && emp.equals(mEmp)) {
                return true;
            }
            if (!name.isEmpty() && name.equals(m.getUserName())) {
                return true;
            }
        }
        return false;
    }

    private boolean samePerson(SysUser user, ProjTeamMember member) {
        String emp = digits(user.getEmployeeNo());
        String mEmp = digits(member.getEmployeeNo());
        if (!emp.isEmpty() && emp.equals(mEmp)) {
            return true;
        }
        String name = user.getRealName() == null ? "" : user.getRealName().trim();
        String mName = member.getUserName() == null ? "" : member.getUserName().trim();
        return !name.isEmpty() && name.equals(mName);
    }

    private String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    /* ==================== 总部经费预算管控 ==================== */

    @GetMapping("/hq-fund/budgets")
    public R<Object> hqBudgets(@RequestParam(value = "year", required = false) Integer year) {
        requireHqFundAccess();
        LambdaQueryWrapper<HqFundBudget> w = new LambdaQueryWrapper<>();
        if (year != null) {
            w.eq(HqFundBudget::getYear, year);
        }
        w.orderByDesc(HqFundBudget::getYear);
        return R.ok(hqBudgetMapper.selectList(w));
    }

    @PostMapping("/hq-fund/budgets")
    public R<Long> createHqBudget(@RequestBody HqFundBudget body) {
        requireHqFundAccess();
        body.setId(null);
        if (body.getStatus() == null) {
            body.setStatus("DRAFT");
        }
        if (body.getApproveStatus() == null) {
            body.setApproveStatus("PENDING");
        }
        hqBudgetMapper.insert(body);
        return R.ok(body.getId());
    }

    /** 预算锁定：经总部管理层审批后正式锁定，作为年度拨付唯一依据 */
    @PostMapping("/hq-fund/budgets/{id}/lock")
    public R<Boolean> lock(@PathVariable("id") Long id) {
        requireHqFundAccess();
        HqFundBudget b = new HqFundBudget();
        b.setId(id);
        b.setStatus("LOCKED");
        b.setApproveStatus("APPROVED");
        hqBudgetMapper.updateById(b);
        return R.ok(true);
    }

    @GetMapping("/hq-fund/quotas")
    public R<Object> quotas(@RequestParam(value = "budgetId", required = false) Long budgetId) {
        requireHqFundAccess();
        LambdaQueryWrapper<HqFundQuota> w = new LambdaQueryWrapper<>();
        if (budgetId != null) {
            w.eq(HqFundQuota::getBudgetId, budgetId);
        }
        return R.ok(quotaMapper.selectList(w));
    }

    @PostMapping("/hq-fund/quotas")
    public R<Long> createQuota(@RequestBody HqFundQuota body) {
        requireHqFundAccess();
        body.setId(null);
        if (body.getUsedAmount() == null) {
            body.setUsedAmount(BigDecimal.ZERO);
        }
        quotaMapper.insert(body);
        return R.ok(body.getId());
    }

    @GetMapping("/hq-fund/transfers")
    public R<Object> transfers(@RequestParam(value = "budgetId", required = false) Long budgetId) {
        requireHqFundAccess();
        LambdaQueryWrapper<HqFundTransfer> w = new LambdaQueryWrapper<>();
        if (budgetId != null) {
            w.eq(HqFundTransfer::getBudgetId, budgetId);
        }
        w.orderByDesc(HqFundTransfer::getApplyAt);
        return R.ok(transferMapper.selectList(w));
    }

    /** 拨付申请：校验不超出该单位年度额度上限 */
    @PostMapping("/hq-fund/transfers")
    public R<Long> createTransfer(@RequestBody HqFundTransfer body) {
        requireHqFundAccess();
        if (body.getQuotaId() != null) {
            HqFundQuota quota = quotaMapper.selectById(body.getQuotaId());
            if (quota != null) {
                BigDecimal quotaAmount = quota.getQuotaAmount() == null ? BigDecimal.ZERO : quota.getQuotaAmount();
                BigDecimal usedAmount = quota.getUsedAmount() == null ? BigDecimal.ZERO : quota.getUsedAmount();
                BigDecimal apply = body.getAmount() == null ? BigDecimal.ZERO : body.getAmount();
                if (usedAmount.add(apply).compareTo(quotaAmount) > 0) {
                    throw new BusinessException("拨付金额超出该单位年度额度上限");
                }
            }
        }
        body.setId(null);
        body.setStatus("PENDING");
        body.setApplyNo(SeqUtil.transferNo(transferMapper.selectCount(null) + 1));
        body.setApplyAt(LocalDateTime.now());
        transferMapper.insert(body);
        return R.ok(body.getId());
    }

    /** 双审：总部科技部 + 财务部双审通过后完成拨付，自动更新拨付台账 */
    @PostMapping("/hq-fund/transfers/{id}/audit")
    public R<Boolean> auditTransfer(@PathVariable("id") Long id,
                                    @RequestBody(required = false) Map<String, Object> body) {
        requireHqFundAccess();
        boolean pass = body != null && Boolean.TRUE.equals(body.get("pass"));
        HqFundTransfer t = transferMapper.selectById(id);
        if (t == null) {
            throw new BusinessException("拨付申请不存在");
        }
        t.setStatus(pass ? "PAID" : "REJECTED");
        t.setApprovedAt(LocalDateTime.now());
        transferMapper.updateById(t);
        if (pass && t.getQuotaId() != null) {
            HqFundQuota q = quotaMapper.selectById(t.getQuotaId());
            if (q != null) {
                BigDecimal used = q.getUsedAmount() == null ? BigDecimal.ZERO : q.getUsedAmount();
                BigDecimal amount = t.getAmount() == null ? BigDecimal.ZERO : t.getAmount();
                q.setUsedAmount(used.add(amount));
                quotaMapper.updateById(q);
            }
        }
        return R.ok(true);
    }

    private void requireHqFundAccess() {
        flowAuditGuard.requireIdentities("总部经费预算管控", "finHq", "hqHead", "hqStaff");
    }
}
