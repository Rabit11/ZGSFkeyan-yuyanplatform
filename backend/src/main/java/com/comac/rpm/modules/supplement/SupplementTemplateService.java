package com.comac.rpm.modules.supplement;

import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.dict.entity.ProjChannel;
import com.comac.rpm.modules.system.entity.SysUser;
import com.comac.rpm.modules.system.mapper.SysUserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Append-only administrator configuration; never grants write access to project records. */
@Service
public class SupplementTemplateService {
 private final JdbcTemplate jdbc;private final ObjectMapper json;private final SysUserMapper users;
 public SupplementTemplateService(JdbcTemplate jdbc,ObjectMapper json,SysUserMapper users){this.jdbc=jdbc;this.json=json;this.users=users;}
 @PostConstruct public void initialize(){new ResourceDatabasePopulator(new ClassPathResource("db/migration_supplement_templates_v1.sql")).execute(Objects.requireNonNull(jdbc.getDataSource()));}
 private SysUser admin(){
  SysUser u=UserContext.getUserId()==null?null:users.selectById(UserContext.getUserId());
  if(u==null||!Integer.valueOf(1).equals(u.getStatus())||!"admin".equals(u.getIdentityCode()))throw new BusinessException(403,"仅系统管理员可以维护渠道材料模板");return u;
 }
 private ProjInfo templateProject(String code){
  ProjInfo p=new ProjInfo();p.setChannelName(code);p.setStatus("FINISHED");p.setLevelCode("NATIONAL");
  if(!code.equals(SupplementCatalog.channelCode(p,null)))throw new BusinessException(400,"必须使用已识别的标准渠道编码");return p;
 }
 private Map<String,Object> latest(String code){
  List<Map<String,Object>> r=jdbc.queryForList("SELECT version,payload,created_by AS createdBy,created_at AS createdAt FROM proj_supplement_channel_template WHERE channel_code=? ORDER BY version DESC LIMIT 1",code);
  if(r.isEmpty())return new LinkedHashMap<>(Map.of("channelCode",code,"version",0L,"sections",new LinkedHashMap<>()));
  Map<String,Object> result=new LinkedHashMap<>(r.get(0));Object payload=result.remove("payload");
  try{result.put("sections",json.readValue(payload.toString(),new TypeReference<Map<String,Object>>(){}));}catch(Exception ex){throw new BusinessException(500,"渠道模板损坏，请联系管理员");}
  result.put("channelCode",code);return result;
 }
 public Map<String,Object> get(String code){admin();ProjInfo p=templateProject(code);Map<String,Object> result=latest(code);result.put("defaults",SupplementCatalog.sections(p,null));return result;}
 @SuppressWarnings("unchecked") public List<Map<String,Object>> apply(ProjInfo p,ProjChannel c,List<Map<String,Object>> original){
  String code=SupplementCatalog.channelCode(p,c);if(code.isEmpty())return original;
  Map<String,Object> config=latest(code);Map<String,Object> overrides=(Map<String,Object>)config.get("sections");
  for(Map<String,Object> s:original){
   s.put("templateVersion","1.0+"+config.get("version"));
   if(overrides.get(s.get("key")) instanceof Map<?,?> override){
    Map<String,Object> clean=json.convertValue(override,new TypeReference<Map<String,Object>>(){});
    s.put("materials",clean.get("materials"));s.put("applicability",clean.get("applicability"));s.put("configurationPending",false);
    if("NOT_APPLICABLE".equals(clean.get("mode"))){s.put("active",false);s.put("requiredRows",false);}
    else if("inspection".equals(s.get("key"))){
     boolean active=!Set.of("DRAFT","DECLARING","APPROVING","DECLARE","DECLARED","草稿","申报中","已申报","FILING","FILED","PENDING_FILING","已立项","立项中").contains(Objects.toString(p.getStatus(),""));
     s.put("active",active);s.put("requiredRows",active);
     List<String> types=((List<Map<String,Object>>)clean.get("materials")).stream().map(m->String.valueOf(m.get("name"))).toList();
     for(Map<String,Object> field:(List<Map<String,Object>>)s.get("fields"))if("type".equals(field.get("key"))){field.put("type","select");field.put("options",types);}
    }
   }
  }
  return original;
 }
 @Transactional public Map<String,Object> save(String code,Map<String,Object> body){
  SysUser u=admin();ProjInfo p=templateProject(code);
  if(!(body.get("version") instanceof Number expected)||!(body.get("sections") instanceof Map<?,?>))throw new BusinessException(400,"必须提供版本及栏目配置");
  // Serialize versions using the existing exact dictionary row, not a JVM-local lock.
  List<Map<String,Object>> channel=jdbc.queryForList("SELECT id,channel_code,channel_name FROM proj_channel ORDER BY id FOR UPDATE").stream().filter(row->{
   ProjChannel candidate=new ProjChannel();candidate.setChannelCode(Objects.toString(row.get("channel_code"),""));candidate.setChannelName(Objects.toString(row.get("channel_name"),""));
   return code.equals(SupplementCatalog.channelCode(p,candidate));
  }).toList();
  if(channel.size()!=1)throw new BusinessException(400,"渠道字典编码不存在或不唯一");
  long current=((Number)latest(code).get("version")).longValue();if(expected.longValue()!=current)throw new BusinessException(409,"渠道模板已更新，请刷新后重试");
  Map<String,Object> clean=validate(body.get("sections"),SupplementCatalog.sections(p,null));
  try{String encoded=json.writeValueAsString(clean);if(encoded.length()>100000)throw new BusinessException(400,"模板配置过大");jdbc.update("INSERT INTO proj_supplement_channel_template(channel_code,version,payload,created_by) VALUES(?,?,?,?)",code,current+1,encoded,u.getId());}
  catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new BusinessException(400,"模板格式错误");}
  return get(code);
 }
 private static String text(Object x,String label,int max){if(!(x instanceof String s)||s.isBlank()||s.length()>max)throw new BusinessException(400,label+"不能为空或超长");return s;}
 /** Whitelisted shape: config can replace material requirements, never field schemas or project scope. */
 @SuppressWarnings("unchecked") static Map<String,Object> validate(Object raw,List<Map<String,Object>> schemas){
  if(!(raw instanceof Map<?,?> input)||input.size()>30)throw new BusinessException(400,"栏目配置格式错误");
  Map<String,Object> result=new LinkedHashMap<>();Set<String> allCodes=new HashSet<>();
  for(Map.Entry<?,?> entry:input.entrySet()){
   String key=String.valueOf(entry.getKey());Map<String,Object> schema=schemas.stream().filter(s->key.equals(s.get("key"))).findFirst().orElseThrow(()->new BusinessException(400,"未知栏目"));
   if(!(entry.getValue() instanceof Map<?,?> s)||!(s.get("materials") instanceof List<?> materials)||materials.size()>100)throw new BusinessException(400,"材料配置格式错误");
   String mode=text(s.get("mode"),"配置模式",30),reason=text(s.get("applicability"),"适用依据",2000);
   if(!Set.of("CONFIGURED","NOT_APPLICABLE").contains(mode))throw new BusinessException(400,"配置模式无效");
   if("NOT_APPLICABLE".equals(mode)&&!"inspection".equals(key))throw new BusinessException(400,"仅评估检查栏目允许配置不适用");
   if("NOT_APPLICABLE".equals(mode)&&!materials.isEmpty())throw new BusinessException(400,"不适用栏目不能配置必传材料");
   if("CONFIGURED".equals(mode)&&materials.isEmpty())throw new BusinessException(400,"请配置材料，或明确评估检查不适用");
   Set<String> fields=new HashSet<>();for(Map<String,Object> f:(List<Map<String,Object>>)schema.get("fields"))fields.add(String.valueOf(f.get("key")));
   List<Map<String,Object>> out=new ArrayList<>();
   for(Object item:materials){
    if(!(item instanceof Map<?,?> m))throw new BusinessException(400,"材料项格式错误");
    String code=text(m.get("code"),"稳定材料编码",120);if(!code.matches("[A-Za-z0-9_-]+")||!allCodes.add(code))throw new BusinessException(400,"材料编码必须唯一且只含字母数字下划线横线");
    Map<String,Object> clean=new LinkedHashMap<>();clean.put("code",code);clean.put("name",text(m.get("name"),"材料名称",250));clean.put("applicability",text(m.get("applicability"),"材料形成时点",1000));
    if(!(m.get("required") instanceof Boolean))throw new BusinessException(400,"required 必须为布尔值");clean.put("required",m.get("required"));
    clean.put("rowScoped",Boolean.TRUE.equals(schema.get("repeatable")));
    if(m.containsKey("requiredWhen"))clean.put("requiredWhen",condition(m.get("requiredWhen"),fields));
    if(m.containsKey("requiredWhenAll")){
     if(!(m.get("requiredWhenAll") instanceof List<?> conditions)||conditions.isEmpty()||conditions.size()>10)throw new BusinessException(400,"条件列表不能为空");
     clean.put("requiredWhenAll",conditions.stream().map(x->condition(x,fields)).toList());
    }
    if(clean.containsKey("requiredWhen")&&clean.containsKey("requiredWhenAll"))throw new BusinessException(400,"不可同时提供两种条件规则");
    out.add(clean);
   }
   result.put(key,Map.of("mode",mode,"applicability",reason,"materials",out));
  }
  return result;
 }
 private static Map<String,Object> condition(Object raw,Set<String> fields){
  if(!(raw instanceof Map<?,?> c)||!fields.contains(c.get("field")))throw new BusinessException(400,"条件引用未知字段");
  Object value=c.get("value");if(!(value instanceof String||value instanceof Number||value instanceof Boolean))throw new BusinessException(400,"条件值无效");
  return Map.of("field",c.get("field"),"value",value);
 }
}
