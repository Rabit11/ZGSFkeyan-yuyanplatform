package com.comac.rpm.modules.supplement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.*;
import com.comac.rpm.modules.project.entity.*;
import com.comac.rpm.modules.project.mapper.*;
import com.comac.rpm.modules.system.entity.SysUser;
import com.comac.rpm.modules.system.mapper.SysUserMapper;
import com.comac.rpm.modules.system.mapper.SysAuditLogMapper;
import com.comac.rpm.modules.dict.entity.ProjChannel;
import com.comac.rpm.modules.dict.mapper.ProjChannelMapper;
import com.comac.rpm.modules.file.MinioStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.util.stream.Collectors;

/** Imported-project supplement drafts. Submission snapshots never change business workflow status. */
@Service
public class SupplementService {
 @org.springframework.beans.factory.annotation.Autowired private com.comac.rpm.modules.transform.TransformAccess publicationAccess;
 private final JdbcTemplate jdbc;
 private final ObjectMapper json;
 private final ProjInfoMapper projects;
 private final SysUserMapper users;
 private final ProjChannelMapper channels;
 private final MinioStorageService storage;
 private final SysAuditLogMapper auditLog;
 private final SupplementTemplateService templates;
 public SupplementService(JdbcTemplate jdbc,ObjectMapper json,ProjInfoMapper projects,SysUserMapper users,ProjChannelMapper channels,MinioStorageService storage,SysAuditLogMapper auditLog,SupplementTemplateService templates) {
  this.jdbc=jdbc;this.json=json;this.projects=projects;this.users=users;this.channels=channels;this.storage=storage;this.auditLog=auditLog;this.templates=templates;
 }
 @PostConstruct public void initialize() {
  new ResourceDatabasePopulator(new ClassPathResource("db/migration_supplement_v1.sql")).execute(Objects.requireNonNull(jdbc.getDataSource()));
 }
 private SysUser user() {
  SysUser u=UserContext.getUserId()==null?null:users.selectById(UserContext.getUserId());
  if(u==null || !Integer.valueOf(1).equals(u.getStatus())) throw new BusinessException(403,"登录用户无效");
  return u;
 }
 private ProjInfo project(Long id) {
  ProjInfo p=projects.selectById(id);
  if(p==null || !"FORM_MAINT".equals(p.getDataSource()) || Integer.valueOf(1).equals(p.getDeleted())) throw new BusinessException(404,"仅支持表单维护导入的项目");
  return p;
 }
 boolean owner(ProjInfo p,SysUser u) {
  if(u.getEmployeeNo()!=null && !u.getEmployeeNo().isBlank()) {
   Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM proj_team_member WHERE project_id=? AND role_code IN ('PROJECT_LEADER','owner','项目负责人') AND employee_no=?",Integer.class,p.getId(),u.getEmployeeNo());
   if(n!=null && n>0) return true;
  }
  String label=p.getOwnerName()==null?"":p.getOwnerName().trim().replace('（','(').replace('）',')');
  if(label.isBlank()) return false;
  if(u.getEmployeeNo()!=null && !u.getEmployeeNo().isBlank() && label.equals(u.getRealName()+"("+u.getEmployeeNo()+")")) return true;
  if(!label.equals(u.getRealName())) return false;
  Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE real_name=? AND status=1",Integer.class,label);
  return n!=null && n==1;
 }
 private Long projectOrg(ProjInfo p) {
  if(p.getOrgId()!=null) return p.getOrgId();
  List<SysUser> candidates=users.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus,1)).stream().filter(u->owner(p,u)).toList();
  return candidates.size()==1?candidates.get(0).getOrgId():null;
 }
 private boolean reviewer(ProjInfo p,SysUser u,String status) {return SupplementPolicy.canReview(owner(p,u),u.getIdentityCode(),u.getOrgId(),projectOrg(p),status);}
 private boolean readable(ProjInfo p,SysUser u) {
  return owner(p,u) || "admin".equals(u.getIdentityCode()) || reviewer(p,u,"UNIT_REVIEW") || reviewer(p,u,"HQ_REVIEW");
 }
 private void read(ProjInfo p,SysUser u) {if(!readable(p,u)) throw new BusinessException(403,"无权查看此项目补录");}
 private void write(ProjInfo p,SysUser u) {if(!owner(p,u)) throw new BusinessException(403,"仅本项目负责人可补录，管理及审核身份不授予修改权限");}
 private ProjChannel channel(ProjInfo p) {return p.getChannelId()==null?null:channels.selectById(p.getChannelId());}
 private List<Map<String,Object>> schemas(ProjInfo p) {return templates.apply(p,channel(p),SupplementCatalog.sections(p,channel(p)));}
 private Map<String,Object> schema(ProjInfo p,String key) {return schemas(p).stream().filter(s->key.equals(s.get("key"))).findFirst().orElseThrow(()->new BusinessException(404,"补录栏目不存在"));}
 private String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new BusinessException(400,"补录内容格式错误");}}
 private Map<String,Object> decode(Object value) {try{return json.readValue(String.valueOf(value),new TypeReference<Map<String,Object>>(){});}catch(Exception e){throw new BusinessException(500,"补录记录损坏，请联系管理员");}}
 @SuppressWarnings("unchecked") private Map<String,Object> values(Map<String,Object> state) {return (Map<String,Object>)state.getOrDefault("values",new LinkedHashMap<>());}
 @SuppressWarnings("unchecked") private List<Map<String,Object>> rows(Map<String,Object> state) {return (List<Map<String,Object>>)state.getOrDefault("rows",new ArrayList<>());}
 @SuppressWarnings("unchecked") private List<String> fileIds(Map<String,Object> state) {return (List<String>)state.getOrDefault("fileIds",new ArrayList<>());}
 private long version(Map<String,Object> state) {return ((Number)state.getOrDefault("version",0)).longValue();}
 @SuppressWarnings("unchecked") private Map<String,Object> effective(Map<String,Object> schema,Map<String,Object> state) {return state.get("schemaSnapshot") instanceof Map?(Map<String,Object>)state.get("schemaSnapshot"):schema;}
 private Map<String,Object> initial(ProjInfo p,Map<String,Object> schema) {
  Map<String,Object> state=new LinkedHashMap<>();
  state.put("values",schema.getOrDefault("defaultValues",new LinkedHashMap<>()));
  state.put("rows",seedRows(p,String.valueOf(schema.get("key")))); state.put("fileIds",new ArrayList<>());
  state.put("status","DRAFT");state.put("version",0L);state.put("batch",1);
  return state;
 }
 private List<Map<String,Object>> seedRows(ProjInfo p,String key) {
  String sql=switch(key) {
   case "team" -> "SELECT id,role_name AS role,user_name AS name,employee_no AS employeeNo FROM proj_team_member WHERE project_id=?";
   case "milestone" -> "SELECT id,year,name,plan_date AS planDate,actual_date AS actualDate,budget,CASE WHEN status='DONE' THEN '已完成' ELSE '进行中' END AS status FROM proj_milestone WHERE project_id=?";
   case "plan" -> "SELECT id,title AS name,source,owner,due_date AS planDate,finish_date AS actualDate,CASE WHEN status='DONE' THEN '已完成' ELSE '进行中' END AS status FROM proj_plan WHERE project_id=?";
   case "fund" -> "SELECT id,year,amount,milestone_id AS milestoneRef,milestone_name AS description,'预算' AS recordType FROM fund_budget WHERE project_id=?";
   case "deliverable" -> "SELECT id,name,deliver_type AS type,due_date AS planDate,deliver_date AS actualDate,owner_orgs AS ownership,milestone_id AS milestoneRef,achievement_no AS achievementRef,CASE WHEN status='DELIVERED' THEN '已交付' ELSE '未交付' END AS status FROM proj_deliverable WHERE project_id=?";
   case "partner" -> "SELECT id,partner_name AS organization,partner_type AS type,eval_date AS date,score,grade,'有外协' AS occurred FROM partner_eval WHERE project_id=?";
   case "transform" -> "SELECT id,achievement_no AS achievementNo,name,intro AS description,duty_org AS organization,transform_way AS path,transform_form AS form,plan_date AS planDate,actual_date AS actualDate,CASE WHEN status='DONE' THEN '已完成转化' WHEN status='NOT_STARTED' THEN '未启动转化' ELSE '转化中' END AS status FROM achv_transform WHERE project_id=?";
   default -> null;
  };
  if(sql==null) return new ArrayList<>();
  List<Map<String,Object>> seeded=new ArrayList<>(jdbc.queryForList(sql,p.getId()));
  if("fund".equals(key)) seeded.addAll(jdbc.queryForList("SELECT id,amount,voucher_no AS voucherNo,occur_date AS occurredDate,remark AS description,CASE WHEN flow_type='WRITEOFF' THEN '核销' ELSE '支出' END AS recordType FROM fund_payment WHERE project_id=?",p.getId()));
  for(Map<String,Object> row:seeded) {
   Object sourceId=row.remove("id");row.put("_source","已有业务记录："+key+"/"+sourceId);
   row.put("_rowId",UUID.nameUUIDFromBytes((p.getId()+":"+key+":"+row.get("recordType")+":"+sourceId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
   row.replaceAll((k,v)->v instanceof java.sql.Date?v.toString():v);
   if("fund".equals(key)) {row.put("totalFund",p.getTotalFund());row.put("nationalFund",p.getNationalFund());row.put("selfFund",p.getSelfFund());row.put("yearBudget",p.getYearBudget());}
   if("deliverable".equals(key)) row.put("deliverableNo",String.valueOf(sourceId));
  }
  return seeded;
 }
 private Map<String,Object> state(ProjInfo p,Map<String,Object> schema,boolean lock) {
  if(lock) jdbc.update("INSERT IGNORE INTO proj_supplement_section(project_id,section_key,payload) VALUES(?,?,?)",p.getId(),schema.get("key"),encode(initial(p,schema)));
  List<Map<String,Object>> records=jdbc.queryForList("SELECT payload FROM proj_supplement_section WHERE project_id=? AND section_key=?"+(lock?" FOR UPDATE":""),p.getId(),schema.get("key"));
  return records.isEmpty()?initial(p,schema):decode(records.get(0).get("payload"));
 }
 private List<Map<String,Object>> files(Long projectId,Map<String,Object> state) {
  Set<String> ids=new HashSet<>(fileIds(state));
  return jdbc.queryForList("SELECT id,material_code AS code,row_id AS rowId,file_name AS fileName,file_size AS fileSize,file_version AS version,uploader_name AS uploadedBy,created_at AS uploadedAt FROM proj_supplement_file WHERE project_id=? ORDER BY created_at DESC",projectId).stream().filter(f->ids.contains(f.get("id"))).peek(f->f.put("url","/api/supplement/"+projectId+"/files/"+f.get("id"))).collect(Collectors.toList());
 }
 private void persist(ProjInfo p,String key,Map<String,Object> state,SysUser u) {
  state.put("version",version(state)+1);
  jdbc.update("UPDATE proj_supplement_section SET version=?,batch_no=?,status=?,payload=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE project_id=? AND section_key=?",state.get("version"),state.get("batch"),state.get("status"),encode(state),u.getId(),p.getId(),key);
 }
 private void record(ProjInfo p,String key,Map<String,Object> state,SysUser u,String action,String opinion) {
  jdbc.update("INSERT INTO proj_supplement_history(project_id,section_key,batch_no,version,action,payload,actor_id,actor_name,opinion) VALUES(?,?,?,?,?,?,?,?,?)",p.getId(),key,state.get("batch"),state.get("version"),action,encode(state),u.getId(),u.getRealName(),opinion);
 }
 private void revision(Map<String,Object> state,long expected) {if(version(state)!=expected) throw new BusinessException(409,"内容已更新，请刷新后重试");}
 private void editable(ProjInfo p,Map<String,Object> schema,Map<String,Object> state,SysUser u,long expected) {
  write(p,u); SupplementPolicy.editable(String.valueOf(state.get("status")),version(state),expected);
  if(Boolean.FALSE.equals(schema.get("active"))) throw new BusinessException(400,"本栏目当前不适用");
 }
 @SuppressWarnings("unchecked") private Map<String,Object> presentation(ProjInfo p,Map<String,Object> schema,Map<String,Object> state,SysUser u) {
  if(state.get("schemaSnapshot") instanceof Map) schema=(Map<String,Object>)state.get("schemaSnapshot");
  Map<String,Object> result=new LinkedHashMap<>(schema); result.putAll(state);
  List<Map<String,Object>> fs=files(p.getId(),state);
  List<Map<String,Object>> materials=new ArrayList<>();
  for(Map<String,Object> item:(List<Map<String,Object>>)schema.getOrDefault("materials",List.of())) {
   Map<String,Object> m=new LinkedHashMap<>(item);m.put("files",fs.stream().filter(f->Objects.equals(f.get("code"),m.get("code"))).toList());materials.add(m);
  }
  result.put("materials",materials);
  List<String> missing=SupplementPolicy.missing(schema,values(state),rows(state),fs);result.put("missing",missing);
  result.put("informationMissing",SupplementPolicy.informationMissing(schema,values(state),rows(state)));
  result.put("materialPending",SupplementPolicy.requirementsPending(schema,rows(state)));
  boolean edit=owner(p,u) && Set.of("DRAFT","RETURNED").contains(String.valueOf(state.get("status"))) && !Boolean.FALSE.equals(schema.get("active"));
  result.put("canEdit",edit); result.put("canSubmit",edit && missing.isEmpty() && SupplementCatalog.resolved(p,channel(p)));
  result.put("canAudit",reviewer(p,u,String.valueOf(state.get("status"))));
  result.put("canReopen",owner(p,u) && "APPROVED".equals(state.get("status")));
  return result;
 }
 public Map<String,Object> detail(Long id) {
  ProjInfo p=project(id);SysUser u=user();
  if(!readable(p,u)) return publication(id);
  Map<String,Object> result=new LinkedHashMap<>(); result.put("project",p);
  ProjChannel c=channel(p);Map<String,Object> ch=new LinkedHashMap<>();ch.put("code",c==null?"":c.getChannelCode());ch.put("name",c==null?p.getChannelName():c.getChannelName());ch.put("resolved",SupplementCatalog.resolved(p,c));result.put("channel",ch);
  result.put("sections",schemas(p).stream().map(s->presentation(p,s,state(p,s,false),u)).toList());
  result.put("legacyMaterials",jdbc.queryForList("SELECT id,field_name AS fieldName,file_name AS fileName,file_size AS fileSize,uploaded_by AS uploadedBy,uploaded_at AS uploadedAt FROM proj_material WHERE biz_type='FORM_MAINT_MAINTENANCE' AND biz_id=? AND file_name IS NOT NULL AND file_name<>'' ORDER BY uploaded_at DESC",id).stream().peek(m->{m.put("status","待归类");m.put("url","/api/supplement/"+id+"/legacy-files/"+m.get("id"));}).toList());
  result.put("history",jdbc.queryForList("SELECT id,section_key AS sectionKey,batch_no AS batch,version,action,actor_name AS actorName,opinion,created_at AS createdAt FROM proj_supplement_history WHERE project_id=? ORDER BY id DESC",id));
  return result;
 }
 public List<Map<String,Object>> list(String view) {
  if(!Set.of("mine","review","history").contains(view)) throw new BusinessException(400,"无效视图");
  SysUser u=user();List<Map<String,Object>> result=new ArrayList<>();
  for(ProjInfo p:projects.selectList(new LambdaQueryWrapper<ProjInfo>().eq(ProjInfo::getDataSource,"FORM_MAINT").eq(ProjInfo::getDeleted,0).orderByDesc(ProjInfo::getUpdatedAt))) {
   if(!readable(p,u) || "mine".equals(view) && !owner(p,u)) continue;
   List<Map<String,Object>> ss=schemas(p).stream().map(s->presentation(p,s,state(p,s,false),u)).toList();
   if("review".equals(view) && ss.stream().noneMatch(s->Boolean.TRUE.equals(s.get("canAudit")))) continue;
   if("history".equals(view) && jdbc.queryForObject("SELECT COUNT(*) FROM proj_supplement_history WHERE project_id=?",Integer.class,p.getId())==0) continue;
   Map<String,Object> row=json.convertValue(p,new TypeReference<Map<String,Object>>(){});
   long active=ss.stream().filter(s->!Boolean.FALSE.equals(s.get("active"))).count();
   long complete=ss.stream().filter(s->!Boolean.FALSE.equals(s.get("active")) && ((List<?>)s.get("informationMissing")).isEmpty()).count();
   String status=ss.stream().anyMatch(s->"RETURNED".equals(s.get("status")))?"RETURNED":ss.stream().anyMatch(s->String.valueOf(s.get("status")).endsWith("REVIEW"))?"IN_REVIEW":ss.stream().filter(s->!Boolean.FALSE.equals(s.get("active"))).allMatch(s->"APPROVED".equals(s.get("status")))?"APPROVED":"DRAFT";
   row.put("status",status);row.put("businessStatus",p.getStatus());row.put("infoComplete",complete);row.put("infoTotal",active);
   int total=0,uploaded=0;
   for(Map<String,Object> s:ss) {
    if(Boolean.FALSE.equals(s.get("active"))) continue;
    for(Object item:(List<?>)s.get("materials")) {
     @SuppressWarnings("unchecked") Map<String,Object> m=(Map<String,Object>)item;
     if(Boolean.TRUE.equals(m.get("rowScoped"))) {
      for(Map<String,Object> r:rows(s)) if(SupplementPolicy.required(m,r)) {
       total++;
       if(((List<?>)m.get("files")).stream().anyMatch(f->f instanceof Map<?,?> file && Objects.equals(file.get("rowId"),r.get("_rowId")))) uploaded++;
      }
     } else if(SupplementPolicy.required(m,values(s))) {total++;if(!((List<?>)m.get("files")).isEmpty())uploaded++;}
    }
   }
   row.put("materialComplete",uploaded);row.put("materialTotal",total);row.put("materialPending",ss.stream().anyMatch(s->Boolean.TRUE.equals(s.get("materialPending")))); row.put("channelResolved",SupplementCatalog.resolved(p,channel(p)));result.add(row);
  }
  return result;
 }
 @Transactional public Map<String,Object> save(Long id,String key,Map<String,Object> body) {
  ProjInfo p=project(id);SysUser u=user();write(p,u);Map<String,Object> sc=schema(p,key),s=state(p,sc,true);sc=effective(sc,s);editable(p,sc,s,u,expected(body));
  if(!(body.get("values") instanceof Map) || !(body.get("rows") instanceof List)) throw new BusinessException(400,"values 与 rows 格式错误");
  if(((List<?>)body.get("rows")).size()>2000) throw new BusinessException(400,"单次最多维护 2000 条记录");
  Map<String,Object> incomingValues=json.convertValue(body.get("values"),new TypeReference<Map<String,Object>>(){});
  List<Map<String,Object>> incomingRows;
  try { incomingRows=json.convertValue(body.get("rows"),new TypeReference<List<Map<String,Object>>>(){}); }
  catch(IllegalArgumentException ex) {throw new BusinessException(400,"数据行格式错误");}
  @SuppressWarnings("unchecked") List<Map<String,Object>> fieldDefs=(List<Map<String,Object>>)sc.getOrDefault("fields",List.of());
  Set<String> allowed=fieldDefs.stream().map(f->String.valueOf(f.get("key"))).collect(Collectors.toSet());
  incomingValues.keySet().retainAll(allowed);
  for(Map<String,Object> field:fieldDefs) if(Boolean.TRUE.equals(field.get("readonly"))) incomingValues.put(String.valueOf(field.get("key")),((Map<?,?>)sc.get("defaultValues")).get(field.get("key")));
  Set<String> existingRows=rows(s).stream().map(r->String.valueOf(r.get("_rowId"))).collect(Collectors.toSet());
  Set<String> used=new HashSet<>();
  for(Map<String,Object> row:incomingRows) {
   if(row==null) throw new BusinessException(400,"数据行不能为空");
   String rowId=Objects.toString(row.get("_rowId"),"");
   row.keySet().retainAll(allowed);
   if(!existingRows.contains(rowId) || !used.add(rowId)) rowId=UUID.randomUUID().toString();
   row.put("_rowId",rowId);
  }
  s.put("values",incomingValues);s.put("rows",incomingRows);
  // Validate payload shape now; completeness is checked at submission to permit partial drafts.
  try {values(s);for(Object r:rows(s)) if(!(r instanceof Map)) throw new IllegalArgumentException();}catch(Exception e){throw new BusinessException(400,"数据行格式错误");}
  persist(p,key,s,u);record(p,key,s,u,"SAVE","");return presentation(p,sc,s,u);
 }
 private long expected(Map<String,Object> body) {if(!(body.get("version") instanceof Number)) throw new BusinessException(400,"必须提供当前版本");return ((Number)body.get("version")).longValue();}
 @Transactional public Map<String,Object> submit(Long id,String key,Map<String,Object> body) {
  ProjInfo p=project(id);SysUser u=user();write(p,u);Map<String,Object> sc=schema(p,key),s=state(p,sc,true);sc=effective(sc,s);editable(p,sc,s,u,expected(body));
  if(!SupplementCatalog.resolved(p,channel(p))) throw new BusinessException(400,"项目来源渠道待核对，不能提交");
  List<String> missing=SupplementPolicy.missing(sc,values(s),rows(s),files(id,s));
  missing.addAll(referenceErrors(p,key,rows(s)));
  if(!missing.isEmpty()) throw new BusinessException(400,String.join("；",missing));
  s.put("schemaSnapshot",sc);s.put("status","UNIT_REVIEW");s.put("submittedBy",u.getId());persist(p,key,s,u);record(p,key,s,u,"SUBMIT","");return presentation(p,sc,s,u);
 }
 private Set<String> references(ProjInfo p,String key) {
  Set<String> result=new HashSet<>();Map<String,Object> sc=schema(p,key);
  for(Map<String,Object> row:rows(state(p,sc,false))) for(String field:List.of("_rowId","name","deliverableNo","achievementNo")) if(row.get(field)!=null && !row.get(field).toString().isBlank()) result.add(row.get(field).toString());
  String table=switch(key){case "milestone"->"proj_milestone";case "deliverable"->"proj_deliverable";case "transform"->"achv_transform";default->throw new IllegalArgumentException();};
  for(Map<String,Object> row:jdbc.queryForList("SELECT id,name FROM "+table+" WHERE project_id=?",p.getId())) {result.add(String.valueOf(row.get("id")));if(row.get("name")!=null)result.add(String.valueOf(row.get("name")));}
  return result;
 }
 private List<String> referenceErrors(ProjInfo p,String key,List<Map<String,Object>> rows) {
  List<String> errors=new ArrayList<>();Map<String,String> fields=switch(key){
   case "milestone"->Map.of("deliverableRefs","deliverable");
   case "fund"->Map.of("milestoneRef","milestone");
   case "deliverable"->Map.of("milestoneRef","milestone","achievementRef","transform");
   case "transform"->Map.of("deliverableRef","deliverable");
   default->Map.of();
  };
  for(Map.Entry<String,String> entry:fields.entrySet()) {
   Set<String> valid=references(p,entry.getValue());int rowNumber=0;
   for(Map<String,Object> row:rows) {
    rowNumber++;String raw=Objects.toString(row.get(entry.getKey()),"").trim();
    if(raw.isEmpty() || "fund".equals(key)&&"无".equals(raw) || "transform".equals(key)&&"暂无成果".equals(row.get("status"))) continue;
    if(!SupplementPolicy.referencesValid(raw,valid)) errors.add("第"+rowNumber+"行关联记录不属于本项目或尚未保存："+raw+"（可填写本项目记录编号或完整名称）");
   }
  }
  return errors;
 }
 @Transactional public Map<String,Object> audit(Long id,String key,Map<String,Object> body) {
  ProjInfo p=project(id);SysUser u=user();Map<String,Object> sc=schema(p,key),s=state(p,sc,true);sc=effective(sc,s);revision(s,expected(body));
  if(!reviewer(p,u,String.valueOf(s.get("status"))) || Objects.equals(String.valueOf(s.get("submittedBy")),String.valueOf(u.getId()))) throw new BusinessException(403,"仅当前审核节点人员可审核，禁止自审");
  String action=String.valueOf(body.get("action")),opinion=String.valueOf(body.getOrDefault("opinion",""));
  if(!Set.of("APPROVE","RETURN").contains(action)) throw new BusinessException(400,"请选择通过或退回");
  if(opinion.isBlank()) throw new BusinessException(400,"请填写审核意见");
  s.put("status","RETURN".equals(action)?"RETURNED":"UNIT_REVIEW".equals(s.get("status"))?"HQ_REVIEW":"APPROVED");
  s.put("opinion",opinion);persist(p,key,s,u);record(p,key,s,u,action,opinion);return presentation(p,sc,s,u);
 }
 @Transactional public Map<String,Object> reopen(Long id,String key,Map<String,Object> body) {
  ProjInfo p=project(id);SysUser u=user();write(p,u);Map<String,Object> sc=schema(p,key),s=state(p,sc,true);sc=effective(sc,s);revision(s,expected(body));
  if(!"APPROVED".equals(s.get("status"))) throw new BusinessException(400,"仅已通过栏目可新建补录批次");
  s.remove("schemaSnapshot");s.put("batch",((Number)s.get("batch")).intValue()+1);s.put("status","DRAFT");persist(p,key,s,u);record(p,key,s,u,"NEW_BATCH","");return presentation(p,sc,s,u);
 }
 @SuppressWarnings("unchecked") @Transactional public Map<String,Object> upload(Long id,String key,String code,long expected,String rowId,MultipartFile file) {
  ProjInfo p=project(id);SysUser u=user();write(p,u);Map<String,Object> sc=schema(p,key),s=state(p,sc,true);sc=effective(sc,s);editable(p,sc,s,u,expected);
  if(((List<Map<String,Object>>)sc.getOrDefault("materials",List.of())).stream().noneMatch(m->code.equals(m.get("code")))) throw new BusinessException(400,"材料项不属于本渠道当前栏目");
  if(Boolean.TRUE.equals(sc.get("repeatable")) && rows(s).stream().noneMatch(r->rowId!=null && rowId.equals(r.get("_rowId")))) throw new BusinessException(400,"请先保存记录并选择附件关联行");
  if(file.isEmpty() || file.getSize()>100L*1024*1024) throw new BusinessException(400,"文件不能为空且不能超过 100MB");
  Map<String,Object> stored=storage.upload(file,"supplement-private");String fileId=UUID.randomUUID().toString();
  Integer max=jdbc.queryForObject("SELECT COALESCE(MAX(file_version),0) FROM proj_supplement_file WHERE project_id=? AND section_key=? AND material_code=?",Integer.class,id,key,code);
  try {
   jdbc.update("INSERT INTO proj_supplement_file(id,project_id,section_key,material_code,row_id,object_key,file_name,file_size,content_type,file_version,uploaded_by,uploader_name) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",fileId,id,key,code,rowId,stored.get("objectKey"),stored.get("fileName"),file.getSize(),file.getContentType(),max+1,u.getId(),u.getRealName());
   List<String> ids=new ArrayList<>(fileIds(s));ids.add(fileId);s.put("fileIds",ids);persist(p,key,s,u);record(p,key,s,u,"UPLOAD",String.valueOf(stored.get("fileName")));
  } catch(RuntimeException e) {storage.delete(String.valueOf(stored.get("objectKey")));throw e;}
  return presentation(p,sc,s,u);
 }
 public Map<String,Object> file(Long id,String fileId) {
  ProjInfo p=project(id);SysUser u=user();
  if(!readable(p,u)) {
   publicationAccess.requireReadable(id);
   boolean publishedFile=publicationEvents(List.of(id)).stream().anyMatch(r->fileIds(SupplementPublication.snapshot(r)).contains(fileId));
   if(!publishedFile) throw new BusinessException(403,"附件尚未提交，无权查看");
  }
  List<Map<String,Object>> found=jdbc.queryForList("SELECT * FROM proj_supplement_file WHERE id=? AND project_id=?",fileId,id);
  if(found.isEmpty()) throw new BusinessException(404,"文件不存在");auditLog.write("SUPPLEMENT","DOWNLOAD","PROJECT",id,"查看或下载补录材料："+found.get(0).get("file_name"));return found.get(0);
 }
 public Map<String,Object> history(Long id,Long historyId) {
  ProjInfo p=project(id);SysUser u=user();read(p,u);
  List<Map<String,Object>> records=jdbc.queryForList("SELECT * FROM proj_supplement_history WHERE id=? AND project_id=?",historyId,id);
  if(records.isEmpty()) throw new BusinessException(404,"记录不存在");Map<String,Object> r=records.get(0),snapshot=decode(r.remove("payload"));
  Map<String,Object> present=presentation(p,schema(p,String.valueOf(r.get("section_key"))),snapshot,u);
  present.put("canEdit",false);present.put("canSubmit",false);present.put("canAudit",false);present.put("canReopen",false);r.put("snapshot",present);return r;
 }
 /** Last accepted snapshot per section stays visible while a later batch is a draft. */
 public List<Map<String,Object>> approved(Long id) {
  ProjInfo p=project(id);SysUser u=user();
  boolean team=u.getEmployeeNo()!=null && !u.getEmployeeNo().isBlank() && jdbc.queryForObject("SELECT COUNT(*) FROM proj_team_member WHERE project_id=? AND employee_no=?",Integer.class,id,u.getEmployeeNo())>0;
  if(!readable(p,u) && !team) publicationAccess.requireReadable(id);
  Set<String> seen=new HashSet<>();List<Map<String,Object>> result=new ArrayList<>();
  for(Map<String,Object> record:jdbc.queryForList("SELECT * FROM proj_supplement_history WHERE project_id=? AND action='APPROVE' ORDER BY id DESC",id)) {
   String key=String.valueOf(record.get("section_key"));Map<String,Object> snapshot=decode(record.get("payload"));
   if(!"APPROVED".equals(snapshot.get("status")) || !seen.add(key)) continue;
   Map<String,Object> item=presentation(p,schema(p,key),snapshot,u);item.put("canEdit",false);item.put("canSubmit",false);item.put("canAudit",false);item.put("canReopen",false);item.put("approvedAt",record.get("created_at"));result.add(item);
  }
  return result;
 }

 private List<Map<String,Object>> publicationEvents(List<Long> ids) {
  if(ids.isEmpty())return List.of();
  String marks=String.join(",",Collections.nCopies(ids.size(),"?"));
  List<Map<String,Object>> events=jdbc.queryForList("SELECT id,project_id,section_key,batch_no,version,action,actor_name,opinion,created_at,payload FROM proj_supplement_history WHERE project_id IN ("+marks+") AND action IN ('SUBMIT','APPROVE','RETURN') ORDER BY id DESC",ids.toArray());
  for(var event:events)event.put("snapshot",decode(event.remove("payload")));
  return events;
 }
 private Map<String,Object> publicationSummary(ProjInfo p,List<Map<String,Object>> events) {
  var latest=SupplementPublication.latest(events,false);var approved=SupplementPublication.latest(events,true);
  var active=schemas(p).stream().filter(s->!Boolean.FALSE.equals(s.get("active"))).map(s->String.valueOf(s.get("key"))).toList();
  var statuses=latest.entrySet().stream().filter(e->active.contains(e.getKey())).map(e->String.valueOf(SupplementPublication.snapshot(e.getValue()).get("status"))).toList();
  Map<String,Object> result=new LinkedHashMap<>();
  result.put("status",SupplementPublication.status(statuses,active.size()));
  result.put("pending",statuses.stream().filter(s->Set.of("UNIT_REVIEW","HQ_REVIEW").contains(s)).count());
  result.put("returned",statuses.stream().filter("RETURNED"::equals).count());
  result.put("approved",statuses.stream().filter("APPROVED"::equals).count());
  result.put("total",active.size());result.put("submitted",latest.size());
  result.put("updatedAt",events.isEmpty()?null:events.get(0).get("created_at"));
  // Payload stays in the supplement tables. These accepted snapshots are a read projection,
  // never a write to workflow tables or a second copy added to business totals.
  result.put("approvedSections",approved.entrySet().stream().map(e->{
   var s=SupplementPublication.snapshot(e.getValue());Map<String,Object> row=new LinkedHashMap<>();row.put("key",e.getKey());row.put("values",s.get("values"));row.put("rows",s.get("rows"));return row;
  }).toList());
  return result;
 }
 public void enrichPublished(List<ProjInfo> records) {
  var imports=records.stream().filter(p->"FORM_MAINT".equals(p.getDataSource())).toList();
  var grouped=publicationEvents(imports.stream().map(ProjInfo::getId).toList()).stream().collect(Collectors.groupingBy(e->((Number)e.get("project_id")).longValue()));
  for(var p:imports)p.setSupplement(publicationSummary(p,grouped.getOrDefault(p.getId(),List.of())));
 }
 /** Project members/leadership may inspect submitted snapshots and opinions, never drafts. */
 public Map<String,Object> publication(Long id) {
  ProjInfo p=project(id);SysUser u=user();publicationAccess.requireReadable(id);
  var events=publicationEvents(List.of(id));var latest=SupplementPublication.latest(events,false);
  var sections=new ArrayList<Map<String,Object>>();
  for(var e:latest.entrySet()) {
   var section=presentation(p,schema(p,e.getKey()),SupplementPublication.snapshot(e.getValue()),u);
   for(String permission:List.of("canEdit","canSubmit","canAudit","canReopen"))section.put(permission,false);
   section.put("publishedAt",e.getValue().get("created_at"));sections.add(section);
  }
  var history=new ArrayList<Map<String,Object>>();
  for(var e:events) {Map<String,Object> row=new LinkedHashMap<>();
   row.put("id",e.get("id"));row.put("sectionKey",e.get("section_key"));row.put("batch",e.get("batch_no"));row.put("version",e.get("version"));row.put("action",e.get("action"));row.put("status",SupplementPublication.snapshot(e).get("status"));row.put("actorName",e.get("actor_name"));row.put("opinion",e.get("opinion"));row.put("createdAt",e.get("created_at"));history.add(row);
  }
  Map<String,Object> result=new LinkedHashMap<>();result.put("project",p);result.put("summary",publicationSummary(p,events));result.put("sections",sections);result.put("history",history);return result;
 }

 public void guardSourceEdit(Long id) {
  if(jdbc.queryForObject("SELECT COUNT(*) FROM proj_supplement_section WHERE project_id=?",Integer.class,id)>0) throw new BusinessException(403,"项目已进入补录维护，源数据已锁定，请从导入项目补录办理");
 }
 public Map<String,Object> legacyFile(Long id,Long materialId) {
  ProjInfo p=project(id);read(p,user());
  List<Map<String,Object>> records=jdbc.queryForList("SELECT file_name,file_url FROM proj_material WHERE id=? AND biz_id=? AND biz_type='FORM_MAINT_MAINTENANCE'",materialId,id);
  if(records.isEmpty()) throw new BusinessException(404,"历史材料不存在");
  Map<String,Object> m=records.get(0);String url=Objects.toString(m.remove("file_url"),"");
  String prefix="/api/files/download?objectKey=";
  if(!url.startsWith(prefix)) throw new BusinessException(400,"历史附件地址暂不支持安全预览，请核对原始文件后补充上传");
  String key=java.net.URLDecoder.decode(url.substring(prefix.length()),java.nio.charset.StandardCharsets.UTF_8);
  if(key.isBlank() || key.contains("..") || key.startsWith("/")) throw new BusinessException(400,"历史附件地址无效");
  m.put("object_key",key);m.put("content_type","application/octet-stream");auditLog.write("SUPPLEMENT","DOWNLOAD","PROJECT",id,"下载待归类历史材料："+m.get("file_name"));return m;
 }
}
