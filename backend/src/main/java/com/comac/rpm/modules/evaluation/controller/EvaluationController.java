package com.comac.rpm.modules.evaluation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.R;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.modules.evaluation.entity.ProjEvaluation;
import com.comac.rpm.modules.evaluation.mapper.ProjEvaluationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评估检查：中期/季度/年度/阶段/督导材料归档，不合格需启动整改
 */
@RestController
@RequestMapping("/api")
public class EvaluationController {

    @Autowired
    private ProjEvaluationMapper evaluationMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;

    @GetMapping("/projects/{projectId}/evaluations")
    public R<List<ProjEvaluation>> list(@PathVariable("projectId") Long projectId) {
        return R.ok(evaluationMapper.selectList(
                new LambdaQueryWrapper<ProjEvaluation>().eq(ProjEvaluation::getProjectId, projectId)
                        .orderByDesc(ProjEvaluation::getDueDate)));
    }

    @PostMapping("/evaluations")
    public R<Long> create(@RequestBody ProjEvaluation body) {
        flowAuditGuard.requireActors(body.getProjectId(), "新增评估检查", "owner", "projectPm", "contactLogin");
        body.setId(null);
        if (body.getStatus() == null) {
            body.setStatus("PENDING");
        }
        evaluationMapper.insert(body);
        return R.ok(body.getId());
    }

    @PutMapping("/evaluations/{id}")
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody ProjEvaluation body) {
        ProjEvaluation existing = writable(id, "修改评估检查");
        body.setId(id);
        body.setProjectId(existing.getProjectId());
        evaluationMapper.updateById(body);
        return R.ok(true);
    }

    @DeleteMapping("/evaluations/{id}")
    public R<Boolean> delete(@PathVariable("id") Long id) {
        writable(id, "删除评估检查");
        evaluationMapper.deleteById(id);
        return R.ok(true);
    }

    private ProjEvaluation writable(Long id, String action) {
        flowAuditGuard.requireIdentities(action, "owner", "projectPm", "contactLogin");
        ProjEvaluation existing = evaluationMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("评估检查不存在");
        }
        flowAuditGuard.requireActors(existing.getProjectId(), action, "owner", "projectPm", "contactLogin");
        return existing;
    }
}
