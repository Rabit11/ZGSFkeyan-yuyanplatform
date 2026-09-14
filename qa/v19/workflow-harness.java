import com.comac.rpm.common.*;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.modules.milestone.controller.MilestoneController;
import com.comac.rpm.modules.acceptance.controller.AcceptanceController;
import com.comac.rpm.modules.transform.controller.TransformController;
import com.comac.rpm.modules.fund.controller.FundController;
import com.comac.rpm.modules.milestone.entity.*;
import com.comac.rpm.modules.project.entity.*;
import com.comac.rpm.modules.system.entity.*;
import com.comac.rpm.modules.declaration.entity.*;
import com.comac.rpm.modules.acceptance.entity.*;
import com.comac.rpm.modules.deliverable.entity.*;
import com.comac.rpm.modules.fund.entity.*;
import com.comac.rpm.modules.transform.entity.*;
import com.comac.rpm.modules.evaluation.entity.*;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Production controller unit integration, single-project mapper fixtures.
 * All workflow decisions execute original Java source. Mapper doubles implement
 * only read fixtures / sparse writes; they are not a workflow or an SQL engine.
 */
class WorkflowHarness {
  static final List<Map<String,Object>> cases = new ArrayList<>();
  static final Map<Class<?>,List<Object>> rows = new HashMap<>();
  static final List<String> trace = new ArrayList<>();
  static MilestoneController ms; static AcceptanceController ac; static FundController fund; static TransformController tx;
  static FlowAuditGuard guard; static SysUser user;
  static ProjInfo project; static ProjMilestone milestone; static ProjAcceptance acceptance;
  static AchvTransform transform; static ProjDeliverable deliverable; static FundBudget budget;
  static int writes;
  interface Action { void run() throws Exception; }
  static void expect(boolean ok, String message) { if (!ok) throw new AssertionError(message); trace.add(message); }
  static void denied(Action action, String contains) throws Exception {
    int before = writes;
    try { action.run(); } catch (BusinessException e) {
      expect(contains == null || e.getMessage().contains(contains), "rejection=" + e.getCode() + ":" + e.getMessage());
      expect(writes == before, "rejected before persistence write"); return;
    }
    throw new AssertionError("Expected business rejection but call succeeded; writes=" + (writes-before));
  }
  static void test(String id, String title, String requirement, Action action) throws Exception {
    reset(); String status = "PASS"; String detail;
    try { action.run(); detail = String.join("; ", trace); }
    catch (AssertionError e) { status = "FAIL"; detail = e.getMessage() + "; " + String.join("; ", trace); }
    catch (Throwable e) { status = "BLOCKED"; detail = "Harness/runtime failure: " + e; }
    cases.add(Map.of("id", id, "title", title, "status", status, "evidence", "Unmodified Java controllers + real FlowAuditGuard, mocked mapper persistence. " + detail,
      "requirementIds", List.of(requirement)));
  }
  static Object get(Object o, String prop) throws Exception { return o.getClass().getMethod("get"+prop).invoke(o); }
  static void put(Object o, String prop, Object v) throws Exception {
    for (Method m:o.getClass().getMethods()) if(m.getName().equals("set"+prop)) {m.invoke(o,v); return;}
  }
  static void add(Object o) { rows.computeIfAbsent(o.getClass(), x->new ArrayList<>()).add(o); }
  static List<Object> table(Class<?> c) { return rows.computeIfAbsent(c, x->new ArrayList<>()); }
  static Class<?> entityType(Class<?> mapper) {
    for(Type t:mapper.getGenericInterfaces()) if(t instanceof ParameterizedType p && p.getActualTypeArguments()[0] instanceof Class<?> c) return c;
    throw new IllegalStateException("Unknown mapper "+mapper);
  }
  static Object mapper(Class<?> type) {
    Class<?> entity = entityType(type);
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "fixture"), entity);
    return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args)-> {
      String n=method.getName(); List<Object> list=table(entity);
      if(n.equals("write")) { trace.add("audit="+args[1]+":"+args[4]); return null; }
      if(n.equals("selectById")) return list.stream().filter(o->{try{return Objects.equals(get(o,"Id"),args[0]);}catch(Exception e){throw new RuntimeException(e);}}).findFirst().orElse(null);
      if(n.equals("selectList")) return new ArrayList<>(list);
      if(n.equals("selectOne")) return list.isEmpty()?null:list.get(0);
      if(n.equals("selectCount")) {
        if(entity==ProjMilestone.class && args!=null && args[0] instanceof AbstractWrapper<?,?,?> w && w.getSqlSegment().contains("<>"))
          return list.stream().filter(o->!"DONE".equals(((ProjMilestone)o).getStatus())).count();
        if(entity==ProjEvaluation.class) return list.stream().filter(o->"FAIL".equals(((ProjEvaluation)o).getResult()) && !"DONE".equals(((ProjEvaluation)o).getStatus())).count();
        return (long)list.size();
      }
      if(n.equals("insert")) {Object o=args[0]; if(get(o,"Id")==null)put(o,"Id",100L+list.size());list.add(o);writes++;return 1;}
      if(n.equals("updateById")) {
        Object patch=args[0]; Object id=get(patch,"Id");
        for(Object o:list)if(Objects.equals(id,get(o,"Id")))for(Field f:entity.getDeclaredFields()) {
          if(Modifier.isStatic(f.getModifiers()))continue; f.setAccessible(true);Object v=f.get(patch);if(v!=null)f.set(o,v);
        }
        writes++; return 1;
      }
      if(n.equals("update")){writes++;return 1;}
      if(n.equals("delete")||n.equals("deleteById")){writes++;return 1;}
      if(n.equals("toString"))return "FixtureMapper<"+entity.getSimpleName()+">";
      throw new UnsupportedOperationException(type.getSimpleName()+"."+n);
    });
  }
  static <T>T wire(T controller) throws Exception {
    for(Field field:controller.getClass().getDeclaredFields()) {
      if(Modifier.isStatic(field.getModifiers())) continue;
      field.setAccessible(true);
      if(field.getType()==FlowAuditGuard.class)field.set(controller,guard);
      else if(field.getType().getSimpleName().endsWith("Mapper"))field.set(controller,mapper(field.getType()));
    }
    return controller;
  }
  static void login(String identity, String emp) {
    user=new SysUser(); user.setId(1L);user.setEmployeeNo(emp);user.setRealName("User"+emp);user.setUsername(emp);user.setIdentityCode(identity);user.setIdentity(identity);user.setOrgId(10L);
    table(SysUser.class).clear();add(user); UserContext.set(1L,emp,List.of(),10L);
  }
  static void member(String code,String emp) {
    ProjTeamMember m=new ProjTeamMember();m.setProjectId(1L);m.setRoleCode(code);m.setEmployeeNo(emp);m.setUserName("User"+emp);add(m);
  }
  static void reset() throws Exception {
    rows.clear();trace.clear();writes=0;
    project=new ProjInfo();project.setId(1L);project.setOrgId(10L);project.setOwnerName("User101");project.setName("Offline QA");project.setProjectNo("QA-1");project.setLevelCode("COMPANY");add(project);
    member("PROJECT_LEADER","101");member("PROJECT_CONTACT","102");member("DEPT_HEAD","103");member("UNIT_MINISTER","104");member("UNIT_FIN_MINISTER","105");member("HQ_FINANCE","106");member("HQ_DIRECTOR","107");
    milestone=new ProjMilestone();milestone.setId(11L);milestone.setProjectId(1L);milestone.setYear(LocalDate.now().getYear());milestone.setName("QA milestone");milestone.setPlanDate(LocalDate.now().plusDays(60));milestone.setStatus("DOING");add(milestone);
    ProjMaterial material=new ProjMaterial();material.setId(12L);material.setBizType("MILESTONE");material.setBizId(11L);material.setFieldCode("EVIDENCE");material.setFileUrl("/isolated-fixture/evidence.pdf");add(material);
    ProjAnnualPlan plan=new ProjAnnualPlan();plan.setId(13L);plan.setProjectId(1L);plan.setYear(LocalDate.now().getYear());plan.setFinishStatus("DONE");add(plan);
    acceptance=new ProjAcceptance();acceptance.setId(14L);acceptance.setProjectId(1L);acceptance.setStatus("NOT_STARTED");add(acceptance);
    deliverable=new ProjDeliverable();deliverable.setId(15L);deliverable.setProjectId(1L);deliverable.setStatus("DELIVERED");deliverable.setName("QA delivered");add(deliverable);
    transform=new AchvTransform();transform.setId(16L);transform.setProjectId(1L);transform.setStatus("NOT_STARTED");transform.setAchievementNo("CG-QA-1");add(transform);
    budget=new FundBudget();budget.setId(17L);budget.setProjectId(1L);budget.setMilestoneId(11L);budget.setMilestoneName("项目经费总核");budget.setStatus("FINAL_PENDING");add(budget);
    guard=wire(new FlowAuditGuard());ms=wire(new MilestoneController());ac=wire(new AcceptanceController());fund=wire(new FundController());tx=wire(new TransformController());login("owner","101");
  }
  static FundPayment payment() {FundPayment p=new FundPayment();p.setProjectId(1L);p.setBudgetId(11L);p.setFlowType("WRITEOFF");p.setAmount(new BigDecimal("10"));p.setOccurDate(LocalDate.now());p.setRemark("purpose||voucher.pdf||/isolated-fixture/voucher.pdf");return p;}
  static long todoCount() {return ((List<?>)ms.board(LocalDate.now().getYear()).getData().get("todos")).size();}
  public static void main(String[] args) throws Exception {
    test("WF-001","里程碑负责人提交→承担部门→单位科研管理负责人→完成","OVERRIDE-MILESTONE",()->{
      ms.close(11L,Map.of());expect("CLOSE_DEPT_AUDIT".equals(milestone.getStatus()),"submit -> CLOSE_DEPT_AUDIT");
      login("deptHead","103");ms.auditClose(11L,Map.of("pass",true));expect("CLOSE_UNIT_AUDIT".equals(milestone.getStatus()),"dept -> CLOSE_UNIT_AUDIT");
      login("unitHead","104");ms.auditClose(11L,Map.of("pass",true));expect("DONE".equals(milestone.getStatus())&&"GREEN".equals(milestone.getColorStatus()),"unit -> DONE/GREEN");
    });
    test("WF-002","里程碑无完成佐证禁止提交","V19.1-NODE-5.4",()->{table(ProjMaterial.class).clear();denied(()->ms.close(11L,Map.of()),"佐证");});
    test("WF-003","里程碑模板不能替代完成佐证","V19.1-NODE-5.4",()->{((ProjMaterial)table(ProjMaterial.class).get(0)).setFieldCode("PLAN_TEMPLATE");denied(()->ms.close(11L,Map.of()),"佐证");});
    test("WF-004","承担部门当前节点仅指定工号可审核","V19.1-NODE-3",()->{milestone.setStatus("CLOSE_DEPT_AUDIT");login("deptHead","999");denied(()->ms.auditClose(11L,Map.of("pass",true)),"指定办理人");});
    test("WF-005","单位负责人不得跳过承担部门节点","OVERRIDE-MILESTONE",()->{milestone.setStatus("CLOSE_DEPT_AUDIT");login("unitHead","104");denied(()->ms.auditClose(11L,Map.of("pass",true)),"指定办理人");});
    test("WF-006","里程碑退回补正保留材料且可重新提交","V19.1-NODE-3",()->{milestone.setStatus("CLOSE_DEPT_AUDIT");login("deptHead","103");ms.auditClose(11L,Map.of("pass",false,"remark","补充说明"));expect("DOING".equals(milestone.getStatus()),"return -> DOING");expect(table(ProjMaterial.class).size()==1,"material retained");login("owner","101");ms.close(11L,Map.of());expect("CLOSE_DEPT_AUDIT".equals(milestone.getStatus()),"resubmit -> dept");});
    test("WF-007","审核中的里程碑禁止重复提交","V19.1-NODE-9",()->{milestone.setStatus("CLOSE_DEPT_AUDIT");denied(()->ms.close(11L,Map.of()),"审核中");});
    test("WF-008","已完成里程碑禁止重开提交","V19.1-NODE-3",()->{milestone.setStatus("DONE");denied(()->ms.close(11L,Map.of()),null);});
    test("WF-009","里程碑待办随审核节点切换且排除同岗位他人","V19.1-NODE-9",()->{milestone.setStatus("CLOSE_DEPT_AUDIT");login("deptHead","103");expect(todoCount()==1,"named dept todo=1");login("deptHead","999");expect(todoCount()==0,"other dept todo=0");login("unitHead","104");expect(todoCount()==0,"unit before its node=0");milestone.setStatus("CLOSE_UNIT_AUDIT");expect(todoCount()==1,"named unit todo=1");login("deptHead","103");expect(todoCount()==0,"previous dept todo=0");});
    test("WF-010","项目负责人继承联系人材料权限","OVERRIDE-OWNER-CONTACT",()->{ProjAcceptanceItem item=new ProjAcceptanceItem();item.setId(18L);item.setAcceptanceId(14L);item.setFieldCode("UNIT_0");item.setLocked(0);add(item);ac.upload(1L,Map.of("fieldCode","UNIT_0","fileUrl","/isolated-fixture/a.pdf"));expect("UPLOADED".equals(item.getStatus()),"owner uploaded contact-permitted material");});
    test("WF-011","里程碑未闭环禁止核销","V19.1-NODE-5.7",()->{login("finHead","105");denied(()->fund.createPayment(payment()),"闭环");});
    test("WF-012","闭环后本级财务核销直接完成无总部审批","OVERRIDE-WRITEOFF",()->{milestone.setStatus("DONE");login("finHead","105");FundPayment p=payment();fund.createPayment(p);expect("WRITTEN".equals(p.getWriteoffStatus()),"createPayment -> WRITTEN");login("finHq","106");expect(((List<?>)fund.pendingFundReviews().getData()).isEmpty(),"no HQ writeoff approval todo");});
    test("WF-013","项目负责人不能替代二级财务核销","OVERRIDE-WRITEOFF",()->{milestone.setStatus("DONE");denied(()->fund.createPayment(payment()),"岗位");});
    test("WF-014","核销缺付款凭证被拒绝","V19.1-NODE-5.7",()->{milestone.setStatus("DONE");login("finHead","105");FundPayment p=payment();p.setRemark("purpose");denied(()->fund.createPayment(p),"必填");});
    test("WF-015","经费总核按二级财务→总部财务流转","OVERRIDE-FUND-FINAL",()->{milestone.setStatus("DONE");login("finHead","105");FundBudget b=new FundBudget();b.setStatus("FINAL_UNIT_OK");fund.updateBudget(17L,b);expect("FINAL_UNIT_OK".equals(budget.getStatus()),"unit -> FINAL_UNIT_OK");login("finHq","106");expect(((List<?>)fund.pendingFundReviews().getData()).size()==1,"HQ final-review todo=1");b=new FundBudget();b.setStatus("FINAL_DONE");fund.updateBudget(17L,b);expect("FINAL_DONE".equals(budget.getStatus()),"HQ -> FINAL_DONE");});
    test("WF-016","总部总核不得跳过二级财务总核","OVERRIDE-FUND-FINAL",()->{milestone.setStatus("DONE");login("finHq","106");FundBudget b=new FundBudget();b.setStatus("FINAL_DONE");denied(()->fund.updateBudget(17L,b),null);});
    test("WF-017","验收里程碑未闭环禁止提交","V19.1-NODE-6.1",()->denied(()->ac.submit(1L),"里程碑"));
    test("WF-018","验收核心交付物未交付禁止提交","V19.1-NODE-6.1",()->{milestone.setStatus("DONE");deliverable.setStatus("PENDING");denied(()->ac.submit(1L),"交付物");});
    test("WF-019","验收经费未核销禁止提交","V19.1-NODE-6.1",()->{milestone.setStatus("DONE");FundPayment p=payment();p.setWriteoffStatus("PENDING");add(p);denied(()->ac.submit(1L),"经费");});
    test("WF-020","验收整改未闭环禁止提交","V19.1-NODE-6.1",()->{milestone.setStatus("DONE");ProjEvaluation e=new ProjEvaluation();e.setProjectId(1L);e.setResult("FAIL");e.setStatus("DOING");add(e);denied(()->ac.submit(1L),"整改");});
    test("WF-021","验收缺必填材料禁止提交","V19.1-NODE-6.1",()->{milestone.setStatus("DONE");ProjAcceptanceItem i=new ProjAcceptanceItem();i.setAcceptanceId(14L);i.setRequired(1);i.setLocked(0);i.setStatus("EMPTY");add(i);denied(()->ac.submit(1L),null);});
    test("WF-022","验收当前节点仅指定审核人可办理","V19.1-NODE-10",()->{acceptance.setStatus("APPLYING");login("unitHead","999");denied(()->ac.audit(1L,Map.of("pass",true)),"指定");});
    test("WF-023","未申请验收不得直接审核办结","V19.1-NODE-6.2",()->{login("unitHead","104");denied(()->ac.audit(1L,Map.of("pass",true)),null);});
    test("WF-024","验收初审后应进入总部终审而非直接DONE","V19.1-NODE-6.2",()->{acceptance.setStatus("APPLYING");login("unitHead","104");ac.audit(1L,Map.of("pass",true));expect(!"DONE".equals(acceptance.getStatus()),"unit approval must await HQ; actual="+acceptance.getStatus());});
    test("WF-025","已完成验收禁止重复申请重开","V19.1-NODE-3",()->{milestone.setStatus("DONE");acceptance.setStatus("DONE");denied(()->ac.submit(1L),null);});
    test("WF-026","验收退回补正应区别于审核在途状态","V19.1-NODE-3",()->{acceptance.setStatus("APPLYING");login("unitHead","104");ac.audit(1L,Map.of("pass",false,"opinion","补充材料"));expect(!"APPLYING".equals(acceptance.getStatus()),"return must identify editable correction node; actual="+acceptance.getStatus());});
    test("WF-027","无已交付成果不得新建转化流程","V19.1-NODE-7.1",()->{table(ProjDeliverable.class).clear();AchvTransform t=new AchvTransform();t.setProjectId(1L);t.setName("QA transformation");denied(()->tx.create(t),null);});
    test("WF-028","成果未交付禁止绑定","V19.1-NODE-7.1",()->{deliverable.setStatus("PENDING");denied(()->tx.bind(16L,Map.of("deliverableIds",List.of(15L))),"已交付");});
    test("WF-029","成果包禁止绑定其他项目交付物","V19.1-NODE-10",()->{deliverable.setProjectId(2L);denied(()->tx.bind(16L,Map.of("deliverableIds",List.of(15L))),null);});
    test("WF-030","成果不得由填报人直接改为完成绕过审核","V19.1-NODE-7.1",()->{AchvTransform t=new AchvTransform();t.setStatus("DONE");denied(()->tx.update(16L,t),null);});
    test("WF-031","公司领导不得写成果转化","V19.1-NODE-10",()->{login("leader","999");AchvTransform t=new AchvTransform();t.setStatus("DONE");denied(()->tx.update(16L,t),"岗位");});
    test("WF-032","同岗位非指定负责人不得写成果转化","V19.1-NODE-3",()->{login("owner","999");AchvTransform t=new AchvTransform();t.setStatus("DOING");denied(()->tx.update(16L,t),"指定");});
    test("WF-033","成果转化正常绑定已交付成果","V19.1-NODE-7.1",()->{tx.bind(16L,Map.of("deliverableIds",List.of(15L)));expect(table(AchvTransformItem.class).size()==1,"binding created for delivered item");});
    test("WF-034","验收经费核销状态缺失不能视为完成","V19.1-NODE-6.1",()->{milestone.setStatus("DONE");FundPayment p=payment();p.setWriteoffStatus(null);add(p);denied(()->ac.submit(1L),null);});
    test("WF-035","里程碑填报接口不能直接改为已完成","OVERRIDE-MILESTONE",()->{ProjMilestone patch=new ProjMilestone();patch.setStatus("DONE");denied(()->ms.update(11L,patch),null);});
    Files.writeString(Path.of(args[0]),new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("suite","workflow","cases",cases)));
    UserContext.clear();
  }
}
