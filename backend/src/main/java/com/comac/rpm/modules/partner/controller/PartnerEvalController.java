package com.comac.rpm.modules.partner.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.R;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.modules.partner.entity.PartnerBlacklist;
import com.comac.rpm.modules.partner.entity.PartnerEval;
import com.comac.rpm.modules.partner.mapper.PartnerBlacklistMapper;
import com.comac.rpm.modules.partner.mapper.PartnerEvalMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 协作单位评价：技术能力、交付质量、进度履约、服务配合、合规性 5 维度各 20 分
 * <p>
 * 评级：优秀（≥90）、良好（≥80）、合格（≥60）、不合格（&lt;60，纳入黑名单）。
 */
@RestController
@RequestMapping("/api")
public class PartnerEvalController {

    @Autowired
    private PartnerEvalMapper partnerEvalMapper;
    @Autowired
    private PartnerBlacklistMapper blacklistMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;

    @GetMapping("/projects/{projectId}/partner-evals")
    public R<List<PartnerEval>> list(@PathVariable("projectId") Long projectId) {
        return R.ok(partnerEvalMapper.selectList(
                new LambdaQueryWrapper<PartnerEval>().eq(PartnerEval::getProjectId, projectId)
                        .orderByAsc(PartnerEval::getId)));
    }

    @GetMapping("/partners/blacklist")
    public R<List<PartnerBlacklist>> blacklist() {
        return R.ok(blacklistMapper.selectList(
                new LambdaQueryWrapper<PartnerBlacklist>().orderByDesc(PartnerBlacklist::getInDate)));
    }

    @PostMapping("/partner-evals")
    public R<Long> create(@RequestBody PartnerEval body) {
        flowAuditGuard.requireActors(body.getProjectId(), "新增协作单位评价", "owner");
        body.setId(null);
        body.setStatus("PENDING");
        body.setScore(0);
        partnerEvalMapper.insert(body);
        return R.ok(body.getId());
    }

    @PutMapping("/partner-evals/{id}")
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody PartnerEval body) {
        PartnerEval existing = writable(id, "修改协作单位评价");
        body.setId(id);
        body.setProjectId(existing.getProjectId());
        // 重新计算总分与等级
        int score = nz(body.getTechScore()) + nz(body.getQualityScore()) + nz(body.getProgressScore())
                + nz(body.getServiceScore()) + nz(body.getComplianceScore());
        body.setScore(score);
        body.setGrade(grade(score));
        partnerEvalMapper.updateById(body);
        return R.ok(true);
    }

    @DeleteMapping("/partner-evals/{id}")
    public R<Boolean> delete(@PathVariable("id") Long id) {
        writable(id, "删除协作单位评价");
        partnerEvalMapper.deleteById(id);
        return R.ok(true);
    }

    /**
     * 提交评价：计算总分与等级；不合格单位自动纳入黑名单
     */
    @PostMapping("/partner-evals/{id}/submit")
    public R<Boolean> submit(@PathVariable("id") Long id) {
        PartnerEval e = writable(id, "提交协作单位评价");
        int score = nz(e.getTechScore()) + nz(e.getQualityScore()) + nz(e.getProgressScore())
                + nz(e.getServiceScore()) + nz(e.getComplianceScore());
        String g = grade(score);
        e.setScore(score);
        e.setGrade(g);
        e.setStatus("DONE");
        e.setEvalDate(LocalDate.now());
        e.setEvaluator(UserContext.getUsername());
        partnerEvalMapper.updateById(e);

        if ("FAIL".equals(g)) {
            long exists = blacklistMapper.selectCount(
                    new LambdaQueryWrapper<PartnerBlacklist>().eq(PartnerBlacklist::getPartnerName, e.getPartnerName()));
            if (exists == 0) {
                PartnerBlacklist b = new PartnerBlacklist();
                b.setPartnerName(e.getPartnerName());
                b.setReason("协作单位评价得分 " + score + " 分，评级不合格，需按程序协调本单位法律部门纳入黑名单");
                b.setInDate(LocalDate.now());
                b.setCreateBy(UserContext.getUsername());
                blacklistMapper.insert(b);
            }
        }
        return R.ok(true);
    }

    /**
     * 供成果转化模块复用：同步刷新某项目下所有评价的等级
     */
    @PostMapping("/partner-evals/refresh/{projectId}")
    public R<Boolean> refresh(@PathVariable("projectId") Long projectId) {
        flowAuditGuard.requireActors(projectId, "刷新协作单位评价", "owner");
        List<PartnerEval> list = partnerEvalMapper.selectList(
                new LambdaQueryWrapper<PartnerEval>().eq(PartnerEval::getProjectId, projectId));
        for (PartnerEval e : list) {
            int score = nz(e.getTechScore()) + nz(e.getQualityScore()) + nz(e.getProgressScore())
                    + nz(e.getServiceScore()) + nz(e.getComplianceScore());
            partnerEvalMapper.update(null, new LambdaUpdateWrapper<PartnerEval>()
                    .eq(PartnerEval::getId, e.getId())
                    .set(PartnerEval::getScore, score)
                    .set(PartnerEval::getGrade, grade(score)));
        }
        return R.ok(true);
    }

    private PartnerEval writable(Long id, String action) {
        flowAuditGuard.requireIdentities(action, "owner");
        PartnerEval existing = partnerEvalMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("评价记录不存在");
        }
        flowAuditGuard.requireActors(existing.getProjectId(), action, "owner");
        return existing;
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private String grade(int score) {
        if (score >= 90) {
            return "EXCELLENT";
        }
        if (score >= 80) {
            return "GOOD";
        }
        if (score >= 60) {
            return "PASS";
        }
        return "FAIL";
    }
}
