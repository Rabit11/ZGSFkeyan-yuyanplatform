package com.comac.rpm.modules.change.service;

import com.comac.rpm.common.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Only these explicitly mapped business fields can be written by approved changes. */
@Component
public class ChangeTargets {
  public record Field(
      String key,
      String category,
      String type,
      String table,
      String column,
      String label,
      String valueType,
      int max) {}

  public static final List<Field> FIELDS =
      List.of(
          new Field(
              "milestoneDate",
              "MILESTONE_DELAY",
              "PROJECT",
              "proj_milestone",
              "plan_date",
              "里程碑计划完成日期",
              "date",
              10),
          new Field(
              "projectEnd", "PERIOD", "PROJECT", "proj_info", "end_date", "项目结束日期", "date", 10),
          new Field(
              "totalFund", "FUND", "PROJECT", "proj_info", "total_fund", "总经费（万元）", "money", 20),
          new Field(
              "partnerName",
              "OUTSOURCE",
              "PROJECT",
              "proj_participant",
              "org_name",
              "合作 / 外协单位名称",
              "text",
              128),
          new Field(
              "paymentDate", "PAYMENT", "PROJECT", "proj_plan", "due_date", "付款待办计划日期", "date", 10),
          new Field(
              "projectGoal",
              "INDICATOR",
              "PROJECT",
              "proj_info",
              "goal",
              "项目目标 / 核心指标",
              "text",
              2000),
          new Field(
              "deliverableName",
              "DELIVERABLE",
              "PROJECT",
              "proj_deliverable",
              "name",
              "交付物名称",
              "text",
              255),
          new Field(
              "deliverableDate",
              "DELIVERABLE",
              "PROJECT",
              "proj_deliverable",
              "due_date",
              "交付物应交付日期",
              "date",
              10),
          new Field("projectName", "BASIC", "DATA", "proj_info", "name", "项目名称纠错", "text", 255),
          new Field(
              "mainWork", "BASIC", "DATA", "proj_info", "main_work", "主要工作内容纠错", "text", 2000),
          new Field(
              "annualGoal",
              "ANNUAL",
              "DATA",
              "proj_annual_plan",
              "annual_goal",
              "年度目标纠错",
              "text",
              2000),
          new Field(
              "annualContent",
              "ANNUAL",
              "DATA",
              "proj_annual_plan",
              "plan_content",
              "年度任务内容纠错",
              "text",
              2000),
          new Field(
              "levelChannel",
              "LEVEL",
              "DATA",
              "proj_info",
              "channel_id",
              "项目层级 / 渠道特殊调整",
              "channel",
              20));
  private final JdbcTemplate jdbc;

  public ChangeTargets(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Field field(String key) {
    return FIELDS.stream()
        .filter(f -> f.key().equals(key))
        .findFirst()
        .orElseThrow(() -> new BusinessException(422, "不支持的变更字段"));
  }

  public Map<String, Object> read(Field field, long id, long projectId, boolean lock) {
    String scope = field.table().equals("proj_info") ? "id" : "project_id";
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT * FROM "
                + field.table()
                + " WHERE id=? AND "
                + scope
                + "=?"
                + (lock ? " FOR UPDATE" : ""),
            id,
            projectId);
    if (rows.isEmpty()) throw new BusinessException(422, "变更对象不存在或不属于该项目");
    Map<String, Object> row = rows.get(0);
    if (field.table().equals("proj_milestone")
        && !Set.of("DOING", "OVERDUE", "PENDING").contains(str(row.get("status"))))
      throw new BusinessException(409, "已完成或正在销项审核的里程碑不可变更");
    if (field.table().equals("proj_deliverable") && "DELIVERED".equals(row.get("status")))
      throw new BusinessException(409, "已交付的交付物不可直接变更");
    if (field.key().equals("paymentDate")
        && (!"TODO".equals(row.get("plan_type"))
            || Set.of("DONE", "COMPLETED", "APPROVED").contains(str(row.get("status")))
            || Set.of("PENDING", "APPROVED").contains(str(row.get("apply_status")))
            || !str(row.get("title")).matches(".*(付款|支付|拨付).*")))
      throw new BusinessException(422, "请选择未完成的付款 / 支付 / 拨付计划节点");
    return row;
  }

  public List<Map<String, Object>> options(long projectId) {
    List<Map<String, Object>> options = new ArrayList<>();
    for (Field f : FIELDS) {
      String scope = f.table().equals("proj_info") ? "id" : "project_id";
      for (Map<String, Object> r :
          jdbc.queryForList(
              "SELECT * FROM " + f.table() + " WHERE " + scope + "=? ORDER BY id", projectId)) {
        try {
          read(f, ((Number) r.get("id")).longValue(), projectId, false);
        } catch (BusinessException ex) {
          continue;
        }
        String objectName =
            str(
                r.getOrDefault(
                    "name",
                    r.getOrDefault(
                        "org_name", r.getOrDefault("title", r.getOrDefault("year", "项目")))));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", f.key());
        item.put("id", r.get("id"));
        item.put("category", f.category());
        item.put("changeType", f.type());
        item.put("label", objectName + " · " + f.label());
        item.put("fieldLabel", f.label());
        item.put("valueType", f.valueType());
        item.put("max", f.max());
        item.put("beforeValue", canonical(r.get(f.column())));
        item.put("beforeDisplay", display(f, canonical(r.get(f.column()))));
        options.add(item);
      }
    }
    return options;
  }

  public String validate(
      Field f, String input, Map<String, Object> row, Map<String, Object> project) {
    return validate(f, input, row, project, Map.of());
  }

  public String validate(Field f, String input, Map<String, Object> row, Map<String, Object> project, Map<String, String> proposed) {
    String s = ChangePolicy.required(input, "调整后内容", f.max());
    try {
      if (f.valueType().equals("date")) {
        LocalDate date = LocalDate.parse(s);
        s = date.toString();
        if (date.getYear() < 1900 || date.getYear() > 9999)
          throw new BusinessException(422, "日期年份须在 1900 至 9999 之间");
        LocalDate start =
            project.get("start_date") == null
                ? null
                : LocalDate.parse(canonical(project.get("start_date")));
        if (start != null && date.isBefore(start))
          throw new BusinessException(422, "调整后日期不得早于项目开始日期");
        if (f.key().equals("milestoneDate")
            && row.get(f.column()) != null
            && !date.isAfter(LocalDate.parse(canonical(row.get(f.column())))))
          throw new BusinessException(422, "延期日期必须晚于原计划日期");
        if (f.key().equals("projectEnd")) {
          for (String[] child :
              List.of(
                  new String[] {"proj_milestone", "plan_date"},
                  new String[] {"proj_deliverable", "due_date"},
                  new String[] {"proj_plan", "due_date"})) {
            String key = switch(child[0]) { case "proj_milestone" -> "milestoneDate"; case "proj_deliverable" -> "deliverableDate"; default -> "paymentDate"; };
            if (proposed.isEmpty()) {
              Long later = jdbc.queryForObject("SELECT COUNT(*) FROM " + child[0] + " WHERE project_id=? AND " + child[1] + ">?", Long.class, project.get("id"), s);
              if (later != null && later > 0) throw new BusinessException(422, "项目结束日期不能早于已有里程碑、交付物或计划节点，请先调整相关节点");
            } else {
              for (var node : jdbc.queryForList("SELECT id," + child[1] + " FROM " + child[0] + " WHERE project_id=?", project.get("id"))) {
                String effective = proposed.getOrDefault(key + ":" + node.get("id"), canonical(node.get(child[1])));
                if (!effective.isBlank() && LocalDate.parse(effective).isAfter(date))
                  throw new BusinessException(422, "项目结束日期不能早于本次调整后的里程碑、交付物或计划节点");
              }
            }
          }
        }
        if (!f.key().equals("projectEnd")
            && project.get("end_date") != null
            && date.isAfter(LocalDate.parse(canonical(project.get("end_date")))))
          throw new BusinessException(422, "节点日期不能超过项目结束日期，请先申请项目周期变更");
      } else if (f.valueType().equals("money")) {
        BigDecimal amount = new BigDecimal(s);
        if (amount.signum() < 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 16)
          throw new IllegalArgumentException();
        BigDecimal floor =
            money(project.get("national_fund"))
                .add(money(project.get("self_fund")))
                .max(money(project.get("expense_total")));
        if (amount.compareTo(floor) < 0) throw new BusinessException(422, "总经费不能低于国拨与自筹合计或已发生支出");
        s = amount.stripTrailingZeros().toPlainString();
      } else if (f.valueType().equals("channel")) {
        long channel = Long.parseLong(s);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM proj_channel WHERE id=?", Long.class, channel)
            == 0) throw new BusinessException(422, "所选渠道不存在");
        s = String.valueOf(channel);
      }
    } catch (BusinessException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new BusinessException(422, "调整后内容格式不正确，请检查日期、金额或渠道");
    }
    if (f.key().equals("partnerName")
        && jdbc.queryForObject(
                "SELECT COUNT(*) FROM partner_blacklist WHERE partner_name=?", Long.class, s)
            > 0) throw new BusinessException(422, "该合作单位在黑名单中，不能作为变更后的单位");
    if (canonical(row.get(f.column())).equals(s))
      throw new BusinessException(422, "调整前后内容相同，无需发起变更");
    return s;
  }

  public void apply(Field f, long targetId, String after) {
    if (f.key().equals("levelChannel")) {
      Map<String, Object> ch =
          jdbc.queryForMap("SELECT * FROM proj_channel WHERE id=?", Long.parseLong(after));
      jdbc.update(
          "UPDATE proj_info SET channel_id=?,channel_name=?,level_code=? WHERE id=?",
          ch.get("id"),
          ch.get("channel_name"),
          ch.get("level_code"),
          targetId);
    } else {
      jdbc.update("UPDATE " + f.table() + " SET " + f.column() + "=? WHERE id=?", after, targetId);
    }
    if (f.table().equals("proj_milestone")) {
      LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
      jdbc.update(
          "UPDATE proj_milestone SET color_status=CASE WHEN plan_date<? THEN 'RED' WHEN"
              + " plan_date<=? THEN 'YELLOW' ELSE 'BLUE' END, status=CASE WHEN plan_date<? THEN"
              + " 'OVERDUE' ELSE 'DOING' END WHERE id=?",
          today,
          today.plusDays(30),
          today,
          targetId);
    }
  }

  public String label(Field f, long id, long projectId) {
    String scope = f.table().equals("proj_info") ? "id" : "project_id";
    var rows =
        jdbc.queryForList(
            "SELECT * FROM " + f.table() + " WHERE id=? AND " + scope + "=?", id, projectId);
    if (rows.isEmpty()) return "已移除对象 #" + id + " · " + f.label();
    var row = rows.get(0);
    String name =
        str(
            row.getOrDefault(
                "name",
                row.getOrDefault(
                    "org_name", row.getOrDefault("title", row.getOrDefault("year", "项目")))));
    return name + " · " + f.label() + " (#" + id + ")";
  }

  public String display(Field f, String value) {
    if (f.valueType().equals("channel") && !value.isBlank()) {
      List<Map<String, Object>> rows =
          jdbc.queryForList("SELECT level_code,channel_name FROM proj_channel WHERE id=?", value);
      if (!rows.isEmpty())
        return str(rows.get(0).get("level_code")) + " / " + str(rows.get(0).get("channel_name"));
    }
    return value + (f.valueType().equals("money") && !value.isBlank() ? " 万元" : "");
  }

  public static String canonical(Object value) {
    if (value instanceof BigDecimal d) return d.stripTrailingZeros().toPlainString();
    return str(value);
  }

  private static BigDecimal money(Object value) {
    return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
  }

  public static String str(Object value) {
    return value == null ? "" : value.toString();
  }
}
