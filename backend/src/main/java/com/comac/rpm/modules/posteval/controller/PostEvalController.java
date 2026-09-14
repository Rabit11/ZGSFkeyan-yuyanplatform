package com.comac.rpm.modules.posteval.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comac.rpm.common.PageVO;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.R;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.common.util.ColorUtil;
import com.comac.rpm.modules.posteval.entity.ProjPostEval;
import com.comac.rpm.modules.posteval.mapper.ProjPostEvalMapper;
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

/**
 * 项目后评价：全生命周期最终闭环环节，终审完成后 3 年内办理
 */
@RestController
@RequestMapping("/api/post-evals")
public class PostEvalController {

    @Autowired
    private ProjPostEvalMapper postEvalMapper;
    @Autowired
    private FlowAuditGuard flowAuditGuard;

    @GetMapping
    public R<PageVO<ProjPostEval>> page(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "10") long size,
            @RequestParam(value = "projectId", required = false) Long projectId) {
        LambdaQueryWrapper<ProjPostEval> w = new LambdaQueryWrapper<>();
        if (projectId != null) {
            w.eq(ProjPostEval::getProjectId, projectId);
        }
        w.orderByDesc(ProjPostEval::getId);
        Page<ProjPostEval> p = postEvalMapper.selectPage(new Page<>(page, size), w);
        for (ProjPostEval e : p.getRecords()) {
            e.setColorStatus(ColorUtil.calcCode(e.getDueDate(), "DONE".equals(e.getStatus())));
        }
        return R.ok(PageVO.of(p));
    }

    @GetMapping("/{id}")
    public R<ProjPostEval> detail(@PathVariable("id") Long id) {
        return R.ok(postEvalMapper.selectById(id));
    }

    @PostMapping
    public R<Long> create(@RequestBody ProjPostEval body) {
        flowAuditGuard.requireActors(body.getProjectId(), "新增后评价", "owner", "unitHead", "hqStaff");
        body.setId(null);
        if (body.getStatus() == null) {
            body.setStatus("PENDING");
        }
        body.setColorStatus(ColorUtil.calcCode(body.getDueDate(), false));
        postEvalMapper.insert(body);
        return R.ok(body.getId());
    }

    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody ProjPostEval body) {
        ProjPostEval existing = writable(id, "修改后评价", "owner", "unitHead", "hqStaff");
        body.setId(id);
        body.setProjectId(existing.getProjectId());
        postEvalMapper.updateById(body);
        return R.ok(true);
    }

    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable("id") Long id) {
        writable(id, "删除后评价", "owner", "unitHead", "hqStaff");
        postEvalMapper.deleteById(id);
        return R.ok(true);
    }

    /** 提交后评价：结果归集至项目台账与可视化看板 */
    @PostMapping("/{id}/submit")
    public R<Boolean> submit(@PathVariable("id") Long id) {
        writable(id, "提交后评价", "owner", "unitHead");
        ProjPostEval e = new ProjPostEval();
        e.setId(id);
        e.setStatus("DONE");
        e.setColorStatus("GREEN");
        postEvalMapper.updateById(e);
        return R.ok(true);
    }

    private ProjPostEval writable(Long id, String action, String... identities) {
        flowAuditGuard.requireIdentities(action, identities);
        ProjPostEval existing = postEvalMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("后评价不存在");
        }
        flowAuditGuard.requireActors(existing.getProjectId(), action, identities);
        return existing;
    }
}
