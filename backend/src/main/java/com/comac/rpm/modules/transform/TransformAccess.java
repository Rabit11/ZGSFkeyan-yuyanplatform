package com.comac.rpm.modules.transform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.modules.project.entity.*;
import com.comac.rpm.modules.project.mapper.*;
import com.comac.rpm.modules.system.entity.SysUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** 本模块的写入权不继承公共审核守卫的管理员放行。 */
@Component
public class TransformAccess {
 @Autowired private FlowAuditGuard guard;
 @Autowired private ProjInfoMapper projects;
 @Autowired private ProjTeamMemberMapper members;
 @Autowired private JdbcTemplate jdbc;
 private static final Set<String> OWNER=Set.of("PROJECT_LEADER","owner","项目负责人");
 private static final Set<String> UNIT=Set.of("UNIT_MINISTER","unitHead","单位科技部长");
 private static final Set<String> HQ=Set.of("HQ_DIRECTOR","HQ_SUPERVISOR","hqHead","hqStaff","总部处室处长","总部处室主管");
 private SysUser user() {
  var u=guard.currentUser();
  if(!Integer.valueOf(1).equals(u.getStatus())) throw new BusinessException(403,"登录用户无效");
  return u;
 }
 private ProjInfo project(Long id) {
  var p=id==null?null:projects.selectById(id);
  if(p==null || Integer.valueOf(1).equals(p.getDeleted())) throw new BusinessException(404,"项目不存在");
  return p;
 }
 private List<ProjTeamMember> team(Long id) {
  var result=members.selectList(new LambdaQueryWrapper<ProjTeamMember>().eq(ProjTeamMember::getProjectId,id));
  return result==null?List.of():result;
 }
 private boolean uniqueName(String name) {
  if(name.isBlank()) return false;
  Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE real_name=? AND status=1",Integer.class,name);
  return Integer.valueOf(1).equals(count);
 }
 private static String clean(String value) {return value==null?"":value.trim();}
 private boolean samePerson(SysUser u,ProjTeamMember m) {
  String emp=clean(u.getEmployeeNo()), memberEmp=clean(m.getEmployeeNo());
  // 工号已指定时不回退姓名，更不去掉字母造成不同工号碰撞。
  if(!memberEmp.isEmpty()) return !emp.isEmpty() && emp.equals(memberEmp);
  String name=clean(u.getRealName());return name.equals(clean(m.getUserName())) && uniqueName(name);
 }
 private static boolean role(ProjTeamMember m,Set<String> roles) {
  return roles.contains(clean(m.getRoleCode())) || roles.contains(clean(m.getRoleName()));
 }
 private boolean owner(ProjInfo p,SysUser u,List<ProjTeamMember> team) {
  var named=team.stream().filter(m->role(m,OWNER)).toList();
  if(!named.isEmpty()) return named.stream().anyMatch(m->samePerson(u,m));
  String label=clean(p.getOwnerName()).replace('（','(').replace('）',')'), name=clean(u.getRealName()), emp=clean(u.getEmployeeNo());
  if(!name.isEmpty() && !emp.isEmpty() && label.equals(name+"("+emp+")")) return true;
  return !name.isEmpty() && label.equals(name) && uniqueName(name);
 }
 private boolean appointed(SysUser u,List<ProjTeamMember> team,Set<String> roles) {
  var named=team.stream().filter(m->role(m,roles)).toList();
  return named.isEmpty() || named.stream().anyMatch(m->samePerson(u,m));
 }
 public void requireOwner(Long projectId) {
  var p=project(projectId);var u=user();
  if(!owner(p,u,team(projectId))) throw new BusinessException(403,"仅本项目负责人可填报和上传成果转化材料");
 }
 public void requireAction(Long projectId,String action) {
  if(Set.of("fill","submit").contains(action)) {requireOwner(projectId);return;}
  var p=project(projectId);var u=user();var team=team(projectId);String identity=clean(u.getIdentityCode());
  if(owner(p,u,team)) throw new BusinessException(403,"项目负责人不能审核或备案本人项目");
  boolean allowed="audit".equals(action) && "unitHead".equals(identity) && p.getOrgId()!=null && p.getOrgId().equals(u.getOrgId()) && appointed(u,team,UNIT)
    || "record".equals(action) && Set.of("hqHead","hqStaff").contains(identity) && appointed(u,team,HQ);
  if(!allowed) throw new BusinessException(403,"无权办理该项目成果转化审核或备案");
 }
 public boolean canRead(Long projectId) {
  try {requireReadable(projectId);return true;} catch(BusinessException e) {return false;}
 }
 /** Accepted team supplements grant read association only, never owner or reviewer rights. */
 private boolean approvedTeamMember(Long projectId,SysUser u) {
  String emp=clean(u.getEmployeeNo());if(emp.isEmpty())return false;
  var snapshots=jdbc.queryForList("SELECT payload FROM proj_supplement_history WHERE project_id=? AND section_key='team' AND action='APPROVE' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.status'))='APPROVED' ORDER BY id DESC LIMIT 1",projectId);
  if(snapshots.isEmpty())return false;
  try {
   var snapshot=new com.fasterxml.jackson.databind.ObjectMapper().readTree(String.valueOf(snapshots.get(0).get("payload")));
   for(var row:snapshot.path("rows"))if(emp.equals(row.path("employeeNo").asText().trim()))return true;
   return false;
  } catch(Exception e) {throw new BusinessException(500,"已审核团队信息损坏，请联系管理员");}
 }
 public void requireReadable(Long projectId) {
  var u=user();String identity=clean(u.getIdentityCode());
  var p=projectId==null?null:projects.selectById(projectId);
  if(p==null || Integer.valueOf(1).equals(p.getDeleted())) {
   if("admin".equals(identity)) return;
   throw new BusinessException(403,"仅管理员可只读查看失去项目关联的历史成果包");
  }
  var team=team(projectId);
  boolean allowed="admin".equals(identity) || "COMPANY".equals(u.getDataScope()) || Set.of("leader","hqHead","hqStaff").contains(identity)
   || ("UNIT".equals(u.getDataScope()) || "unitHead".equals(identity)) && p.getOrgId()!=null && p.getOrgId().equals(u.getOrgId())
   || owner(p,u,team) || Objects.equals(u.getId(),p.getCreateBy()) || team.stream().anyMatch(m->samePerson(u,m))
   || "FORM_MAINT".equals(p.getDataSource()) && approvedTeamMember(projectId,u);
  if(!allowed) throw new BusinessException(403,"无权访问该项目成果包或材料");
 }
 public List<Long> visibleProjectIds() {
  var u=user();
  if("admin".equals(u.getIdentityCode())) return null;
  var active=projects.selectList(new LambdaQueryWrapper<ProjInfo>().eq(ProjInfo::getDeleted,0));
  if("COMPANY".equals(u.getDataScope()) || Set.of("leader","hqHead","hqStaff").contains(clean(u.getIdentityCode()))) return active.stream().map(ProjInfo::getId).toList();
  // 项目入口和文件下载复用同一判定，避免列表中的宽松姓名匹配泄露材料。
  return active.stream().map(ProjInfo::getId).filter(this::canRead).toList();
 }
}
