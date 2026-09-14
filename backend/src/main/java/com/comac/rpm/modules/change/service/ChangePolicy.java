package com.comac.rpm.modules.change.service;

import com.comac.rpm.common.BusinessException;
import java.util.*;

/** V19.1 P839–914：渠道细则优先，重大事项必须独立法务审核。 */
public final class ChangePolicy {
  private ChangePolicy() {}

  public static final Set<String> MAJOR = Set.of("FUND", "PERIOD", "OUTSOURCE");
  public static final Set<String> EDITABLE = Set.of("DRAFT", "REJECTED");
  public static final Set<String> IMPLEMENTING = Set.of("IMPLEMENTING", "DELAYED");
  public static final Map<String, String> LABELS =
      Map.of(
          "UNIT_REVIEW",
          "二级单位主管部门初审",
          "UNIT_FINAL",
          "二级单位主管部门终审",
          "UNIT_CONFIRM",
          "二级单位审查确认",
          "LEGAL",
          "法务审核",
          "HQ_REVIEW",
          "总部对应管理部门终审",
          "HQ_CONFIRM",
          "总部科技主管确认",
          "EXTERNAL_ARCHIVE",
          "GXB 线下上报材料归档");

  public static List<String> route(String type, String category, String channel) {
    List<String> route = new ArrayList<>();
    if ("DATA".equals(type)) return List.of("UNIT_REVIEW", "HQ_CONFIRM");
    if (!"PROJECT".equals(type)) throw new BusinessException(422, "请选择项目变更或数据变更");
    if ("SHKJCX".equals(channel)) {
      if (MAJOR.contains(category)) route.addAll(List.of("UNIT_REVIEW", "LEGAL"));
      route.add("UNIT_FINAL");
    } else if (Set.of("CLM", "BOKH").contains(channel)) {
      route.add("UNIT_REVIEW");
      if (MAJOR.contains(category)) route.add("LEGAL");
      route.add("UNIT_CONFIRM");
    } else {
      route.add("UNIT_REVIEW");
      if (MAJOR.contains(category)) route.add("LEGAL");
      route.add("HQ_REVIEW");
    }
    if ("MJKY".equals(channel)) route.add("EXTERNAL_ARCHIVE");
    return List.copyOf(route);
  }

  public static void editable(String status) {
    if (!EDITABLE.contains(status)) throw new BusinessException(409, "仅草稿或已驳回申请可以编辑、删除和提交");
  }

  public static void revision(Integer supplied, int current) {
    if (supplied == null || supplied != current)
      throw new BusinessException(409, "申请已被更新，请刷新后重新办理");
  }

  public static String required(String value, String label, int max) {
    String s = value == null ? "" : value.trim();
    if (s.isEmpty()) throw new BusinessException(422, "请填写" + label);
    if (s.length() > max) throw new BusinessException(422, label + "不能超过 " + max + " 字");
    return s;
  }
}
