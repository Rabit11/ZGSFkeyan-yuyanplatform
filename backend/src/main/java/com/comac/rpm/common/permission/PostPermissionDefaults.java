package com.comac.rpm.common.permission;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目岗位办理权限：14 岗位 × 18 权限推荐默认矩阵
 */
public final class PostPermissionDefaults {

    private PostPermissionDefaults() {
    }

    public static final List<String> POST_CODES = Collections.unmodifiableList(Arrays.asList(
            "contact", "owner", "tech", "pm", "chief1", "chief2",
            "hqHead", "hqStaff", "unitDeptHead", "unitStaff", "deptHead", "finHq", "finHead", "finStaff"
    ));

    public static final List<String> PERM_CODES = Collections.unmodifiableList(Arrays.asList(
            "baseinfo_edit", "milestone_plan", "milestone_close", "plan_manage",
            "funds_submit", "funds_voucher", "deliverable_manage", "eval_collaborator",
            "transform_update", "declare_submit", "filing_upload", "initiate_approval",
            "assess_submit", "assess_archive", "change_submit", "contract_register",
            "accept_apply", "members_edit"
    ));

    public static Map<String, List<String>> defaultMatrix() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("contact", Arrays.asList(
                "baseinfo_edit", "milestone_plan", "milestone_close", "funds_submit",
                "transform_update", "declare_submit", "filing_upload", "initiate_approval",
                "assess_submit", "change_submit", "members_edit"));
        m.put("owner", Arrays.asList(
                "baseinfo_edit", "milestone_plan", "milestone_close", "funds_submit", "funds_voucher",
                "deliverable_manage", "eval_collaborator", "transform_update", "declare_submit", "filing_upload",
                "initiate_approval", "assess_submit", "change_submit", "accept_apply", "members_edit"));
        m.put("tech", Arrays.asList(
                "baseinfo_edit", "milestone_plan", "milestone_close", "funds_submit",
                "deliverable_manage", "initiate_approval", "change_submit"));
        m.put("pm", Arrays.asList(
                "baseinfo_edit", "plan_manage", "funds_submit", "initiate_approval",
                "assess_submit", "change_submit", "contract_register"));
        m.put("chief1", Collections.singletonList("funds_submit"));
        m.put("chief2", Collections.singletonList("funds_submit"));
        m.put("hqHead", Collections.singletonList("assess_archive"));
        m.put("hqStaff", Collections.singletonList("assess_archive"));
        m.put("unitDeptHead", Arrays.asList("milestone_close", "initiate_approval", "assess_archive", "members_edit"));
        m.put("unitStaff", Arrays.asList("milestone_close", "assess_archive", "members_edit"));
        m.put("deptHead", Arrays.asList("initiate_approval", "assess_archive", "members_edit"));
        m.put("finHq", Collections.singletonList("funds_submit"));
        m.put("finHead", Collections.singletonList("funds_submit"));
        m.put("finStaff", Collections.emptyList());
        return m;
    }
}
