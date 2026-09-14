package com.comac.rpm.modules.change.service;

import static com.comac.rpm.modules.change.service.ChangeTargets.str;

import com.comac.rpm.common.*;
import com.comac.rpm.modules.file.MinioStorageService;
import com.comac.rpm.modules.system.entity.SysUser;
import com.comac.rpm.modules.system.mapper.SysUserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Change-only application boundary: authorization, review history and atomic writeback. */
@Service
public class ChangeService {
  public record SaveRequest(
      Long projectId,
      String changeType,
      String category,
      String title,
      String reason,
      String targetKey,
      Long targetId,
      String afterValue,
      Long legalReviewerId,
      Integer revision,
      String requestKey,
      List<ItemRequest> items) {}

  public record ItemRequest(String targetKey, Long targetId, String category, String afterValue) {}

  public record ReviewRequest(Integer revision, Boolean pass, String opinion) {}

  public record ArchiveRequest(Integer revision, String reference, String opinion) {}

  private static final String SELECT =
      "SELECT"
          + " c.*,x.applicant_id,x.target_key,x.target_id,x.legal_reviewer_id,x.route_json,x.step_index,x.revision,x.applied_at,x.archive_ref"
          + " FROM proj_change c LEFT JOIN proj_change_control x ON x.change_id=c.id JOIN proj_info"
          + " p ON p.id=c.project_id AND p.deleted=0 ";
  private static final Set<String> TEAM = Set.of("owner", "projectPm", "techLead", "contactLogin");
  private static final Set<String> UNIT =
      Set.of("unitHead", "unitStaff", "deptHead", "finHead", "finStaff", "chief2");
  @Value("${rpm.change.test-mode:false}")
  private boolean testMode;
  @Value("${rpm.change.legal-employees:}")
  private String legalEmployees = "";

  private boolean qualifiedLegal(SysUser u) {
    return u != null && Integer.valueOf(1).equals(u.getStatus())
        && !Set.of("admin", "leader").contains(str(u.getIdentityCode()))
        && Arrays.stream(legalEmployees.split(",")).map(String::trim)
            .filter(s -> !s.isEmpty()).anyMatch(s -> s.equals(u.getEmployeeNo()));
  }

  private final JdbcTemplate jdbc;
  private final ChangeTargets targets;
  private final SysUserMapper users;
  private final ObjectMapper json;
  private final MinioStorageService storage;

  public ChangeService(
      JdbcTemplate jdbc,
      ChangeTargets targets,
      SysUserMapper users,
      ObjectMapper json,
      MinioStorageService storage) {
    this.jdbc = jdbc;
    this.targets = targets;
    this.users = users;
    this.json = json;
    this.storage = storage;
  }

  private SysUser user() {
    SysUser u = UserContext.getUserId() == null ? null : users.selectById(UserContext.getUserId());
    if (u == null || !Integer.valueOf(1).equals(u.getStatus()))
      throw new BusinessException(401, "账号不可用，请重新登录");
    return u;
  }

  private Map<String, Object> project(long id, boolean lock) {
    var rows =
        jdbc.queryForList(
            "SELECT p.*,ch.channel_code FROM proj_info p LEFT JOIN proj_channel ch ON"
                + " ch.id=p.channel_id WHERE p.id=? AND p.deleted=0"
                + (lock ? " FOR UPDATE" : ""),
            id);
    if (rows.isEmpty()) throw new BusinessException(404, "项目不存在或已删除");
    return rows.get(0);
  }

  private boolean samePerson(SysUser u, Map<String, Object> m) {
    String emp = str(m.get("employee_no")).trim();
    return !emp.isEmpty()
        ? emp.equals(u.getEmployeeNo())
        : str(m.get("user_name")).equals(u.getRealName());
  }

  private boolean related(SysUser u, Map<String, Object> p) {
    var members =
        jdbc.queryForList(
            "SELECT role_code,employee_no,user_name FROM proj_team_member WHERE project_id=?",
            p.get("id"));
    if (members.stream().anyMatch(m -> samePerson(u, m))) return true;
    // Named membership is authoritative; do not fall back to an ambiguous owner name.
    return members.isEmpty()
        && (str(p.get("owner_name")).equals(u.getRealName())
            || str(p.get("owner_name")).equals(u.getEmployeeNo()));
  }

  private boolean sameOrg(SysUser u, Map<String, Object> p) {
    return u.getOrgId() != null && str(p.get("org_id")).equals(u.getOrgId().toString());
  }

  private boolean canReadProject(SysUser u, Map<String, Object> p) {
    return "COMPANY".equals(u.getDataScope())
        || "admin".equals(u.getIdentityCode())
        || related(u, p)
        || (UNIT.contains(str(u.getIdentityCode())) && sameOrg(u, p));
  }

  private boolean canCreate(SysUser u, Map<String, Object> p) {
    return TEAM.contains(str(u.getIdentityCode()))
        && related(u, p)
        && ChangePolicy.IMPLEMENTING.contains(str(p.get("status")));
  }

  private boolean isUser(Map<String, Object> r, String key, SysUser u) {
    return r.get(key) != null && str(r.get(key)).equals(u.getId().toString());
  }

  private void requireRead(SysUser u, Map<String, Object> r) {
    if (!canReadProject(u, project(num(r, "project_id"), false))
        && !isUser(r, "legal_reviewer_id", u)
        && !isUser(r, "applicant_id", u)) throw new BusinessException(403, "无权查看该项目的变更申请");
  }

  private void requireEditor(SysUser u, Map<String, Object> r) {
    ChangePolicy.editable(str(r.get("status")));
    if (!isUser(r, "applicant_id", u) || !canCreate(u, project(num(r, "project_id"), false)))
      throw new BusinessException(403, "仅本申请发起人可维护，且项目须处于实施阶段");
  }

  private boolean canSubmit(SysUser u, Map<String, Object> r, Map<String, Object> p) {
    if (r.get("revision") == null
        || !ChangePolicy.EDITABLE.contains(str(r.get("status")))
        || "techLead".equals(u.getIdentityCode())
        || !canCreate(u, p)) return false;
    if (isUser(r, "applicant_id", u)) return true;
    SysUser author = users.selectById(num(r, "applicant_id"));
    return author != null
        && "techLead".equals(author.getIdentityCode())
        && actor(u, p, "owner", "PROJECT_LEADER");
  }

  private Map<String, Object> row(long id, boolean lock) {
    if (lock)
      jdbc.queryForList(
          "SELECT change_id FROM proj_change_control WHERE change_id=? FOR UPDATE", id);
    var rows = jdbc.queryForList(SELECT + "WHERE c.id=?" + (lock ? " FOR UPDATE" : ""), id);
    if (rows.isEmpty()) throw new BusinessException(404, "变更申请不存在");
    return rows.get(0);
  }

  private void controlled(Map<String, Object> r) {
    if (r.get("revision") == null) throw new BusinessException(409, "历史申请仅供查阅，请重新发起受控变更");
  }

  private static long num(Map<String, Object> m, String k) {
    return ((Number) m.get(k)).longValue();
  }

  private List<String> route(Map<String, Object> r) {
    if (r.get("route_json") == null) return List.of();
    try {
      return json.readValue(str(r.get("route_json")), new TypeReference<List<String>>() {});
    } catch (Exception e) {
      throw new BusinessException(409, "审批路径数据异常");
    }
  }

  private String current(Map<String, Object> r) {
    var route = route(r);
    int i = r.get("step_index") == null ? 0 : ((Number) r.get("step_index")).intValue();
    return i >= route.size() ? "" : route.get(i);
  }

  private boolean actor(SysUser u, Map<String, Object> p, String identity, String role) {
    if (!identity.equals(u.getIdentityCode())) return false;
    var named =
        jdbc.queryForList(
            "SELECT employee_no,user_name FROM proj_team_member WHERE project_id=? AND role_code=?",
            p.get("id"),
            role);
    return !named.isEmpty()
        ? named.stream().anyMatch(m -> samePerson(u, m))
        : identity.startsWith("hq") || sameOrg(u, p);
  }

  private boolean canAudit(SysUser u, Map<String, Object> r) {
    if (!"APPROVING".equals(r.get("status"))
        || r.get("revision") == null
        || isUser(r, "applicant_id", u)) return false;
    var p = project(num(r, "project_id"), false);
    return switch (current(r)) {
      case "LEGAL" ->
          isUser(r, "legal_reviewer_id", u)
              && qualifiedLegal(u);
      case "UNIT_REVIEW" ->
          Set.of("CLM", "BOKH").contains(str(p.get("channel_code")))
              ? actor(u, p, "unitStaff", "UNIT_SUPERVISOR")
              : actor(u, p, "unitHead", "UNIT_MINISTER");
      case "UNIT_FINAL", "UNIT_CONFIRM" -> actor(u, p, "unitHead", "UNIT_MINISTER");
      case "HQ_REVIEW" ->
          actor(u, p, "hqHead", "HQ_DIRECTOR") || actor(u, p, "hqStaff", "HQ_SUPERVISOR");
      case "HQ_CONFIRM" -> actor(u, p, "hqStaff", "HQ_SUPERVISOR");
      default -> false;
    };
  }

  private List<Map<String, Object>> handlers(Map<String, Object> r, String node) {
    if ("EXTERNAL_ARCHIVE".equals(node)) {
      var u = users.selectById(num(r, "applicant_id"));
      return u == null || !Integer.valueOf(1).equals(u.getStatus()) ? List.of()
          : List.of(Map.of("name", u.getRealName(), "employeeNo", str(u.getEmployeeNo())));
    }
    var probe = new LinkedHashMap<String, Object>(r);
    probe.put("status", "APPROVING");
    probe.put("step_index", route(r).indexOf(node));
    return users.selectList(null).stream().filter(u -> Integer.valueOf(1).equals(u.getStatus()))
        .filter(u -> canAudit(u, probe))
        .map(u -> Map.<String, Object>of("name", u.getRealName(), "employeeNo", str(u.getEmployeeNo())))
        .toList();
  }

  private List<String> readiness(Map<String, Object> r) {
    List<String> result = new ArrayList<>();
    for (String node : route(r)) {
      if (handlers(r, node).isEmpty()) result.add(ChangePolicy.LABELS.get(node)
          + ("LEGAL".equals(node) ? "：请配置并选择法务名单中的在岗人员" : "：缺少在岗办理人，请联系项目管理人员维护岗位"));
    }
    return result;
  }

  public Map<String, Object> context(Long projectId) {
    SysUser u = user();
    List<Map<String, Object>> projects = new ArrayList<>();
    for (var p : jdbc.queryForList("SELECT * FROM proj_info WHERE deleted=0 ORDER BY id DESC")) {
      if (!canReadProject(u, p)) continue;
      Map<String, Object> v = new LinkedHashMap<>();
      for (String k :
          List.of("id", "name", "project_no", "status", "level_code", "channel_name", "org_name"))
        v.put(camel(k), p.get(k));
      v.put("canCreate", canCreate(u, p));
      projects.add(v);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("projects", projects);
    out.put(
        "channels",
        jdbc.queryForList(
            "SELECT id,channel_name AS label,level_code AS levelCode FROM proj_channel ORDER BY"
                + " id"));
    out.put("testMode", testMode);
    out.put("legalReviewers", users.selectList(null).stream()
        .filter(this::qualifiedLegal).filter(v -> !v.getId().equals(u.getId()))
        .map(v -> Map.of("id", v.getId(), "realName", v.getRealName(),
            "employeeNo", v.getEmployeeNo(), "identity", testMode ? "演练法务（仅测试授权）" : "已配置法务办理人"))
        .toList());
    out.put("targets", List.of());
    if (projectId != null) {
      var p = project(projectId, false);
      if (!canReadProject(u, p)) throw new BusinessException(403, "无权访问该项目");
      out.put("targets", targets.options(projectId));
      out.put("channelCode", str(p.get("channel_code")));
    }
    return out;
  }

  public PageVO<Map<String, Object>> page(
      int page,
      int size,
      Long projectId,
      String type,
      String status,
      String keyword,
      boolean mine) {
    if (page < 1 || size < 1 || size > 200) throw new BusinessException(422, "分页范围错误，每页最多 200 条");
    SysUser u = user();
    List<Object> args = new ArrayList<>();
    StringBuilder where = new StringBuilder("WHERE 1=1 ");
    if (projectId != null) {
      where.append("AND c.project_id=? ");
      args.add(projectId);
    }
    if (type != null && !type.isBlank()) {
      where.append("AND c.change_type=? ");
      args.add(type);
    }
    if (status != null && !status.isBlank()) {
      where.append("AND c.status=? ");
      args.add(status);
    }
    if (keyword != null && !keyword.isBlank()) {
      where.append("AND (c.title LIKE ? OR c.change_no LIKE ? OR p.name LIKE ?) ");
      for (int i = 0; i < 3; i++) args.add("%" + keyword.trim() + "%");
    }
    List<Map<String, Object>> visible = new ArrayList<>();
    for (var r : jdbc.queryForList(SELECT + where + "ORDER BY c.id DESC", args.toArray())) {
      try {
        requireRead(u, r);
      } catch (BusinessException e) {
        continue;
      }
      if (mine
          && !canAudit(u, r)
          && !canSubmit(u, r, project(num(r, "project_id"), false))
          && !("AWAITING_ARCHIVE".equals(r.get("status")) && isUser(r, "applicant_id", u)))
        continue;
      visible.add(r);
    }
    var records =
        visible.stream()
            .skip((long) (page - 1) * size)
            .limit(size)
            .map(r -> decorate(r, u, false))
            .toList();
    return PageVO.of(records, visible.size(), page, size);
  }

  public Map<String, Object> detail(long id) {
    SysUser u = user();
    var r = row(id, false);
    requireRead(u, r);
    return decorate(r, u, true);
  }

  private Map<String, Object> decorate(Map<String, Object> r, SysUser u, boolean detail) {
    Map<String, Object> out = new LinkedHashMap<>();
    r.forEach((k, v) -> out.put(camel(k), v));
    out.remove("routeJson");
    out.put(
        "route",
        route(r).stream()
            .map(s -> Map.of("code", s, "label", ChangePolicy.LABELS.get(s)))
            .toList());
    var p = project(num(r, "project_id"), false);
    out.put("projectName", p.get("name"));
    out.put("projectNo", p.get("project_no"));
    boolean edit =
        r.get("revision") != null
            && ChangePolicy.EDITABLE.contains(str(r.get("status")))
            && isUser(r, "applicant_id", u)
            && canCreate(u, p);
    out.put("canEdit", edit);
    out.put("canSubmit", canSubmit(u, r, p));
    out.put("canAudit", canAudit(u, r));
    out.put(
        "canArchive", "AWAITING_ARCHIVE".equals(r.get("status")) && isUser(r, "applicant_id", u));
    out.put("legacy", r.get("revision") == null);
    if (r.get("target_key") != null) {
      var f = targets.field(str(r.get("target_key")));
      out.put("fieldLabel", f.label());
      if (detail)
        out.put("targetLabel", targets.label(f, num(r, "target_id"), num(r, "project_id")));
      out.put("beforeDisplay", targets.display(f, str(r.get("before_value"))));
      out.put("afterDisplay", targets.display(f, str(r.get("after_value"))));
    }
    var items = itemRows(r);
    out.put("itemCount", items.size());
    out.put("categories", items.stream().map(i -> str(i.get("category"))).distinct().toList());
    if (detail) {
      out.put("items", itemViews(r, items));
      var versions = rounds(num(r, "id"));
      out.put("rounds", versions);
      out.put("roundNo", versions.isEmpty() ? 0 : versions.get(0).get("roundNo"));
      out.put("route", route(r).stream().map(node -> Map.of("code", node, "label", ChangePolicy.LABELS.get(node), "handlers", handlers(r, node))).toList());
      var author = r.get("applicant_id") == null ? null : users.selectById(num(r, "applicant_id"));
      out.put("returnTo", author == null ? "原发起人" : author.getRealName() + "（" + author.getEmployeeNo() + "）");
      int nextIndex = ChangePolicy.EDITABLE.contains(str(r.get("status"))) ? 0
          : r.get("step_index") == null ? 0 : (int) num(r, "step_index") + 1;
      String nextNode = nextIndex < route(r).size() ? route(r).get(nextIndex) : "";
      out.put("nextNode", nextNode.isEmpty() ? "全部变更项统一生效，流程办结" : ChangePolicy.LABELS.get(nextNode));
      out.put("nextHandlers", nextNode.isEmpty() ? List.of() : handlers(r, nextNode));
      out.put("submitHandlers", author != null && "techLead".equals(author.getIdentityCode())
          ? users.selectList(null).stream().filter(a -> Integer.valueOf(1).equals(a.getStatus()) && canSubmit(a, r, p))
              .map(a -> Map.of("name", a.getRealName(), "employeeNo", str(a.getEmployeeNo()))).toList()
          : List.of());
      out.put("readinessIssues", r.get("revision") == null ? List.of() : readiness(r));
      out.put("currentHandlers", r.get("revision") == null || current(r).isEmpty() ? List.of()
          : handlers(r, current(r)));
      out.put(
          "history",
          jdbc.queryForList(
              "SELECT id,action,node_name AS nodeName,actor_name AS actorName,opinion,created_at AS"
                  + " createdAt FROM proj_change_history WHERE change_id=? ORDER BY id",
              r.get("id")));
      out.put(
          "attachments",
          jdbc.queryForList(
              "SELECT id,kind,file_name AS fileName,file_size AS fileSize,created_at AS createdAt"
                  + " FROM proj_change_attachment WHERE change_id=? AND removed=0 ORDER BY id",
              r.get("id")));
      if (r.get("legal_reviewer_id") != null) {
        var legal = users.selectById(num(r, "legal_reviewer_id"));
        out.put("legalReviewerName", legal == null ? "已离岗" : legal.getRealName());
      }
    }
    return out;
  }

  private static String camel(String s) {
    StringBuilder b = new StringBuilder();
    boolean upper = false;
    for (char c : s.toCharArray()) {
      if (c == '_') upper = true;
      else {
        b.append(upper ? Character.toUpperCase(c) : c);
        upper = false;
      }
    }
    return b.toString();
  }

  private String serialize(Object v) {
    try {
      return json.writeValueAsString(v);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Map<String, Object> save(Long id, SaveRequest b) {
    SysUser u = user();
    if (b.projectId() == null)
      throw new BusinessException(422, "请选择关联项目与变更对象");
    if (id == null) jdbc.queryForList("SELECT id FROM sys_user WHERE id=? FOR UPDATE", u.getId());
    var existing = id == null ? null : row(id, true);
    if (existing != null) {
      controlled(existing);
      requireEditor(u, existing);
      ChangePolicy.revision(b.revision(), (int) num(existing, "revision"));
      if (num(existing, "project_id") != b.projectId())
        throw new BusinessException(422, "已保存申请不能改换项目，请另建申请");
    }
    String requestKey =
        b.requestKey() == null
            ? UUID.randomUUID().toString()
            : ChangePolicy.required(b.requestKey(), "请求标识", 64);
    if (id == null) {
      var prior =
          jdbc.queryForList(
              "SELECT change_id FROM proj_change_control WHERE applicant_id=? AND request_key=? FOR"
                  + " UPDATE",
              Long.class,
              u.getId(),
              requestKey);
      if (!prior.isEmpty()) return detail(prior.get(0));
    }
    var p = project(b.projectId(), true);
    if (!canCreate(u, p)) throw new BusinessException(403, "仅本项目项目团队可在实施阶段发起变更");
    var input = b.items() == null
        ? Collections.singletonList(new ItemRequest(b.targetKey(), b.targetId(), b.category(), b.afterValue()))
        : b.items();
    var items = prepareItems(p, b.changeType(), input);
    var first = items.get(0);
    var f = targets.field(str(first.get("target_key")));
    long firstId = num(first, "target_id");
    String category = items.stream().map(i -> str(i.get("category")))
        .filter(ChangePolicy.MAJOR::contains).findFirst().orElse(f.category());
    String before = str(first.get("before_value")), after = str(first.get("after_value"));
    String title = ChangePolicy.required(b.title(), "变更标题", 255),
        reason = ChangePolicy.required(b.reason(), "变更缘由", 2000);
    var route = ChangePolicy.route(b.changeType(), category, str(p.get("channel_code")));
    Long legalId = route.contains("LEGAL") ? b.legalReviewerId() : null;
    if (route.contains("LEGAL") && legalId != null) {
      var legal = legalId == null ? null : users.selectById(legalId);
      if (!qualifiedLegal(legal)
          || legalId.equals(u.getId())
          || Set.of("admin", "leader").contains(str(legal.getIdentityCode())))
        throw new BusinessException(422, "请选择名单内独立、在岗的法务办理人；名单由项目变更配置限定");
    }
    if (existing != null && rounds(id).isEmpty()) saveRound(id, existing, itemRows(existing), u);
    if (id == null) {
      String no =
          "BG"
              + LocalDate.now().toString().replace("-", "")
              + UUID.randomUUID()
                  .toString()
                  .replace("-", "")
                  .substring(0, 12)
                  .toUpperCase(Locale.ROOT);
      jdbc.update(
          "INSERT INTO"
              + " proj_change(change_no,project_id,project_name,change_type,category,title,reason,before_value,after_value,legal_review,status,applicant,created_at,updated_at)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,'DRAFT',?,NOW(),NOW())",
          no,
          b.projectId(),
          p.get("name"),
          b.changeType(),
          category,
          title,
          reason,
          before,
          after,
          route.contains("LEGAL") ? 1 : 0,
          u.getUsername());
      id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
      jdbc.update(
          "INSERT INTO"
              + " proj_change_control(change_id,applicant_id,request_key,target_key,target_id,legal_reviewer_id,route_json)"
              + " VALUES(?,?,?,?,?,?,?)",
          id,
          u.getId(),
          requestKey,
          f.key(),
          firstId,
          legalId,
          serialize(route));
      history(id, "CREATE", "草稿", u, "创建申请；共 " + items.size() + " 项变更");
    } else {
      jdbc.update(
          "UPDATE proj_change SET"
              + " change_type=?,category=?,title=?,reason=?,before_value=?,after_value=?,legal_review=?,status='DRAFT',flow_node=NULL,updated_at=NOW()"
              + " WHERE id=?",
          b.changeType(),
          category,
          title,
          reason,
          before,
          after,
          route.contains("LEGAL") ? 1 : 0,
          id);
      jdbc.update(
          "UPDATE proj_change_control SET"
              + " target_key=?,target_id=?,legal_reviewer_id=?,route_json=?,step_index=0,revision=revision+1"
              + " WHERE change_id=?",
          f.key(),
          firstId,
          legalId,
          serialize(route),
          id);
      history(id, "EDIT", "草稿", u, "保存新版本；共 " + items.size() + " 项变更，可在修改版本中查看完整快照");
    }
    jdbc.update("DELETE FROM proj_change_item WHERE change_id=?", id);
    for (int n = 0; n < items.size(); n++) {
      var item = items.get(n);
      jdbc.update("INSERT INTO proj_change_item(change_id,ordinal,target_key,target_id,category,before_value,after_value) VALUES(?,?,?,?,?,?,?)",
          id, n, item.get("target_key"), item.get("target_id"), item.get("category"), item.get("before_value"), item.get("after_value"));
    }
    saveRound(id, row(id, false), items, u);
    return detail(id);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Map<String, Object> submit(long id, Integer revision) {
    SysUser u = user();
    var r = row(id, true);
    controlled(r);
    if (!canSubmit(u, r, project(num(r, "project_id"), false)))
      throw new BusinessException(403, "仅发起人可提交；技术负责人草稿由本项目负责人确认提交");
    ChangePolicy.revision(revision, (int) num(r, "revision"));
    checkSnapshot(r);
    var problems = readiness(r);
    if (!problems.isEmpty()) throw new BusinessException(422, String.join("；", problems));
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM proj_change_attachment WHERE change_id=? AND removed=0 AND"
                + " kind='SUPPORT'",
            Long.class,
            id)
        == 0) throw new BusinessException(422, "请先上传至少一份变更支撑材料");
    var route = route(r);
    advance(id, 0, route);
    history(id, "SUBMIT", ChangePolicy.LABELS.get(route.get(0)), u, "提交审批，锁定申请内容与支撑材料");
    return detail(id);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Map<String, Object> audit(long id, ReviewRequest b) {
    SysUser u = user();
    var r = row(id, true);
    controlled(r);
    requireRead(u, r);
    ChangePolicy.revision(b.revision(), (int) num(r, "revision"));
    if (!canAudit(u, r)) throw new BusinessException(403, "仅当前节点指定办理人可审核，不可跳级或自审");
    if (b.pass() == null) throw new BusinessException(422, "请选择审核结果");
    String opinion = ChangePolicy.required(b.opinion(), "审核意见", 2000),
        node = ChangePolicy.LABELS.get(current(r));
    if (!b.pass()) {
      jdbc.update(
          "UPDATE proj_change SET status='REJECTED',flow_node='退回发起人补正',updated_at=NOW() WHERE"
              + " id=?",
          id);
      jdbc.update("UPDATE proj_change_control SET revision=revision+1 WHERE change_id=?", id);
      history(id, "REJECT", node, u, opinion);
    } else {
      int next = (int) num(r, "step_index") + 1;
      var route = route(r);
      if (next == route.size()) apply(r);
      advance(id, next, route);
      history(id, "APPROVE", node, u, opinion);
    }
    return detail(id);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Map<String, Object> archive(long id, ArchiveRequest b) {
    SysUser u = user();
    var r = row(id, true);
    controlled(r);
    requireRead(u, r);
    ChangePolicy.revision(b.revision(), (int) num(r, "revision"));
    if (!"AWAITING_ARCHIVE".equals(r.get("status")) || !isUser(r, "applicant_id", u))
      throw new BusinessException(403, "仅发起人可在总部终审后归档 GXB 线下材料");
    String ref = ChangePolicy.required(b.reference(), "线下上报文号 / 回执号", 255);
    String opinion = ChangePolicy.required(b.opinion(), "归档意见", 2000);
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM proj_change_attachment WHERE change_id=? AND removed=0 AND"
                + " kind='EXTERNAL'",
            Long.class,
            id)
        == 0) throw new BusinessException(422, "请上传真实的 GXB 线下上报归档材料");
    apply(r);
    jdbc.update("UPDATE proj_change_control SET archive_ref=? WHERE change_id=?", ref, id);
    advance(id, route(r).size(), route(r));
    history(id, "ARCHIVE", "GXB 线下上报材料归档", u, ref + "；" + opinion);
    return detail(id);
  }

  private void advance(long id, int next, List<String> route) {
    String code = next >= route.size() ? "" : route.get(next),
        status =
            code.isEmpty()
                ? "APPROVED"
                : code.equals("EXTERNAL_ARCHIVE") ? "AWAITING_ARCHIVE" : "APPROVING";
    jdbc.update(
        "UPDATE proj_change SET status=?,flow_node=?,updated_at=NOW() WHERE id=?",
        status,
        code.isEmpty() ? "已办结并回写" : ChangePolicy.LABELS.get(code),
        id);
    jdbc.update(
        "UPDATE proj_change_control SET step_index=?,revision=revision+1 WHERE change_id=?",
        next,
        id);
  }

  private void checkSnapshot(Map<String, Object> r) {
    var p = project(num(r, "project_id"), true);
    if (!ChangePolicy.IMPLEMENTING.contains(str(p.get("status"))))
      throw new BusinessException(409, "项目已离开实施阶段，不能继续此变更");
    var items = itemRows(r);
    for (var item : items) {
      var f = targets.field(str(item.get("target_key")));
      var target = targets.read(f, num(item, "target_id"), num(r, "project_id"), true);
      if (!ChangeTargets.canonical(target.get(f.column())).equals(str(item.get("before_value"))))
        throw new BusinessException(409, f.label() + "已变化，整份申请不能回写；请退回后重新核对全部变更项");
    }
    prepareItems(p, str(r.get("change_type")), items.stream().map(i -> new ItemRequest(
        str(i.get("target_key")), num(i, "target_id"), str(i.get("category")), str(i.get("after_value")))).toList());
    String category = items.stream().map(i -> str(i.get("category")))
        .filter(ChangePolicy.MAJOR::contains).findFirst().orElse(str(r.get("category")));
    if (!route(r).equals(ChangePolicy.route(str(r.get("change_type")), category, str(p.get("channel_code")))))
      throw new BusinessException(409, "项目渠道已变化，请退回后重新保存申请以核对审批路径");
  }

  private void apply(Map<String, Object> r) {
    checkSnapshot(r);
    var items = itemRows(r);
    for (var item : items) targets.apply(targets.field(str(item.get("target_key"))),
        num(item, "target_id"), str(item.get("after_value")));
    jdbc.update("UPDATE proj_change_control SET applied_at=NOW() WHERE change_id=?", r.get("id"));
    history(num(r, "id"), "APPLY", "数据回写", user(), "整份申请 " + items.size() + " 项变更已在同一事务中生效");
  }

  private List<Map<String, Object>> prepareItems(Map<String, Object> p, String type, List<ItemRequest> input) {
    if (input == null || input.isEmpty() || input.size() > 50)
      throw new BusinessException(422, "每份申请需包含 1 至 50 个变更项");
    Set<String> seen = new HashSet<>();
    Map<String, String> proposed = new HashMap<>();
    for (var item : input) {
      if (item == null || item.targetId() == null) throw new BusinessException(422, "请选择具体变更对象");
      var f = targets.field(item.targetKey());
      if (!f.type().equals(type) || !f.category().equals(item.category()))
        throw new BusinessException(422, "类型、事项与目标字段不匹配；项目调整和数据纠错须分别申请");
      if (!seen.add(f.key() + ":" + item.targetId())) throw new BusinessException(422, "同一字段不能重复选择，请编辑已选项");
      proposed.put(f.key() + ":" + item.targetId(), str(item.afterValue()).trim());
    }
    var effective = new LinkedHashMap<String, Object>(p);
    String end = proposed.get("projectEnd:" + p.get("id"));
    if (end != null) effective.put("end_date", end);
    List<Map<String, Object>> result = new ArrayList<>();
    for (var item : input) {
      var f = targets.field(item.targetKey());
      var target = targets.read(f, item.targetId(), num(p, "id"), true);
      String after = targets.validate(f, item.afterValue(), target, effective, proposed);
      result.add(Map.of("target_key", f.key(), "target_id", item.targetId(), "category", f.category(),
          "before_value", ChangeTargets.canonical(target.get(f.column())), "after_value", after));
    }
    return result;
  }

  private List<Map<String, Object>> itemRows(Map<String, Object> r) {
    var items = jdbc.queryForList("SELECT * FROM proj_change_item WHERE change_id=? ORDER BY ordinal", r.get("id"));
    return items.isEmpty() && r.get("target_key") != null ? List.of(r) : items;
  }

  private List<Map<String, Object>> itemViews(Map<String, Object> r, List<Map<String, Object>> items) {
    return items.stream().map(i -> {
      var v = new LinkedHashMap<String, Object>();
      for (String k : List.of("target_key", "target_id", "category", "before_value", "after_value")) v.put(camel(k), i.get(k));
      var f = targets.field(str(i.get("target_key")));
      v.put("fieldLabel", f.label());
      v.put("targetLabel", targets.label(f, num(i, "target_id"), num(r, "project_id")));
      v.put("beforeDisplay", targets.display(f, str(i.get("before_value"))));
      v.put("afterDisplay", targets.display(f, str(i.get("after_value"))));
      return (Map<String, Object>) v;
    }).toList();
  }

  private List<Map<String, Object>> rounds(long id) {
    return jdbc.queryForList("SELECT round_no AS roundNo,actor_name AS actorName,created_at AS createdAt,snapshot_json FROM proj_change_round WHERE change_id=? ORDER BY round_no DESC", id)
        .stream().map(v -> {
          try { v.put("snapshot", json.readValue(str(v.remove("snapshot_json")), Map.class)); }
          catch (Exception e) { throw new IllegalStateException("修改版本数据异常", e); }
          return v;
        }).toList();
  }

  private void saveRound(long id, Map<String, Object> r, List<Map<String, Object>> items, SysUser u) {
    Integer n = jdbc.queryForObject("SELECT COALESCE(MAX(round_no),0)+1 FROM proj_change_round WHERE change_id=?", Integer.class, id);
    var snapshot = new LinkedHashMap<String, Object>();
    for (String k : List.of("title", "reason", "change_type", "legal_reviewer_id")) snapshot.put(camel(k), r.get(k));
    snapshot.put("items", itemViews(r, items));
    snapshot.put("route", route(r));
    jdbc.update("INSERT INTO proj_change_round(change_id,round_no,actor_name,snapshot_json) VALUES(?,?,?,?)",
        id, n, u.getRealName() + "（" + u.getEmployeeNo() + "）", serialize(snapshot));
  }

  private void history(long id, String action, String node, SysUser u, String opinion) {
    String text = opinion == null ? "" : opinion;
    jdbc.update(
        "INSERT INTO proj_change_history(change_id,action,node_name,actor_id,actor_name,opinion)"
            + " VALUES(?,?,?,?,?,?)",
        id,
        action,
        node,
        u.getId(),
        u.getRealName() + "（" + u.getEmployeeNo() + "）",
        text.length() > 2000 ? text.substring(0, 2000) : text);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void delete(long id, Integer revision) {
    SysUser u = user();
    var r = row(id, true);
    controlled(r);
    requireEditor(u, r);
    ChangePolicy.revision(revision, (int) num(r, "revision"));
    history(id, "DELETE", "删除草稿", u, "删除草稿，保留审计记录");
    jdbc.update("DELETE FROM proj_change_item WHERE change_id=?", id);
    jdbc.update("DELETE FROM proj_change_control WHERE change_id=?", id);
    jdbc.update("DELETE FROM proj_change WHERE id=?", id);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Map<String, Object> upload(long id, Integer revision, String kind, MultipartFile file) {
    SysUser u = user();
    var r = row(id, true);
    controlled(r);
    ChangePolicy.revision(revision, (int) num(r, "revision"));
    if ("EXTERNAL".equals(kind)) {
      if (!"AWAITING_ARCHIVE".equals(r.get("status")) || !isUser(r, "applicant_id", u))
        throw new BusinessException(403, "当前不能上传线下归档材料");
    } else {
      if (!"SUPPORT".equals(kind)) throw new BusinessException(422, "材料类型错误");
      requireEditor(u, r);
    }
    String name = file == null ? "" : str(file.getOriginalFilename());
    if (file == null
        || file.isEmpty()
        || file.getSize() > 20L * 1024 * 1024
        || name.length() > 255
        || !name.toLowerCase(Locale.ROOT)
            .matches(".*\\.(pdf|doc|docx|xls|xlsx|png|jpg|jpeg|txt|md)$"))
      throw new BusinessException(422, "请选择非空 PDF、Office、图片或文本文件，单份不超过 20 MB");
    var uploaded = storage.upload(file, "change/" + id);
    String key = str(uploaded.get("objectKey"));
    try {
      jdbc.update(
          "INSERT INTO"
              + " proj_change_attachment(change_id,kind,object_key,file_name,file_size,uploaded_by)"
              + " VALUES(?,?,?,?,?,?)",
          id,
          kind,
          key,
          name,
          file.getSize(),
          u.getId());
      jdbc.update("UPDATE proj_change_control SET revision=revision+1 WHERE change_id=?", id);
      history(id, "UPLOAD", kind.equals("SUPPORT") ? "支撑材料" : "线下归档", u, name);
    } catch (RuntimeException e) {
      storage.delete(key);
      throw e;
    }
    return detail(id);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Map<String, Object> removeFile(long id, long fileId, Integer revision) {
    SysUser u = user();
    var r = row(id, true);
    controlled(r);
    if (!"AWAITING_ARCHIVE".equals(r.get("status")) || !isUser(r, "applicant_id", u))
      requireEditor(u, r);
    ChangePolicy.revision(revision, (int) num(r, "revision"));
    if ("AWAITING_ARCHIVE".equals(r.get("status"))
        && !"EXTERNAL".equals(file(id, fileId).get("kind")))
      throw new BusinessException(403, "已提交的支撑材料不可移除");
    if (jdbc.update(
            "UPDATE proj_change_attachment SET removed=1 WHERE id=? AND change_id=? AND removed=0",
            fileId,
            id)
        != 1) throw new BusinessException(404, "附件不存在");
    jdbc.update("UPDATE proj_change_control SET revision=revision+1 WHERE change_id=?", id);
    history(id, "REMOVE_FILE", "支撑材料", u, "移除附件引用 #" + fileId);
    return detail(id);
  }

  public Map<String, Object> file(long id, long fileId) {
    requireRead(user(), row(id, false));
    var files =
        jdbc.queryForList(
            "SELECT * FROM proj_change_attachment WHERE change_id=? AND id=? AND removed=0",
            id,
            fileId);
    if (files.isEmpty()) throw new BusinessException(404, "附件不存在");
    return files.get(0);
  }

  public InputStream download(String key) {
    return storage.download(key);
  }
}
