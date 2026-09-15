// Integration checks run ONLY against the disposable merge database and backend on 18081.
const fs=require('fs'),cp=require('child_process'),assert=require('assert');
const DB='rpm_merge_qa_20260915',BASE='http://127.0.0.1:18081/api';
const sql=q=>cp.execFileSync('mysql',['--default-character-set=utf8mb4','-N',DB,'-e',q]).toString().trim();
assert.equal(sql('SELECT DATABASE()'),DB);
const roles={owner:'PROJECT_LEADER',projectPm:'PROJECT_SUPERVISOR',unitHead:'UNIT_MINISTER',unitLeader:'UNIT_LEADER',hqStaff:'HQ_SUPERVISOR',hqHead:'HQ_DIRECTOR',chief1:'L1_CHIEF',deptHead:'DEPT_HEAD',leader:null,contactLogin:null};
assert.equal(sql("SELECT COUNT(*) FROM sys_user WHERE username LIKE 'mergeqa_%'"),'0','Use a fresh isolated fixture');
let index=0;
for(const [role,post] of Object.entries(roles)) {
 const emp=String(991100+(index++)),scope=['hqHead','hqStaff'].includes(role)?'COMPANY':['unitHead','unitLeader','deptHead'].includes(role)?'UNIT':'SELF';
 sql(`INSERT INTO sys_user(username,password,real_name,employee_no,org_id,identity_code,data_scope,status) SELECT 'mergeqa_${role}',password,'Merge QA ${role}','${emp}',991991,'${role}','${scope}',1 FROM sys_user WHERE username='100001'`);
}
function makeProject(no,level,source) {
 sql(`INSERT INTO proj_info(project_no,name,goal,start_date,end_date,channel_id,channel_name,level_code,status,data_source,org_id,org_name,owner_name,total_fund) SELECT '${no}','${no}','原目标','2026-01-01','2028-12-31',id,channel_name,'${level}','IMPLEMENTING','${source}',991991,'隔离测试单位','Merge QA owner',10 FROM proj_channel WHERE channel_code='${level==='NATIONAL'?'MJKY':'YYGD'}' LIMIT 1`);
 const id=Number(sql(`SELECT id FROM proj_info WHERE project_no='${no}'`));assert(id>0,'fixture '+no);
 for(const [role,post] of Object.entries(roles)) if(post)sql(`INSERT INTO proj_team_member(project_id,group_code,role_code,role_name,user_name,employee_no) SELECT ${id},'MGMT','${post}','${post}',real_name,employee_no FROM sys_user WHERE username='mergeqa_${role}'`);
 return id;
}
const bpm=makeProject('MERGE_BPM_QA','NATIONAL','PLATFORM'),acc=makeProject('MERGE_ACCEPT_QA','NATIONAL','PLATFORM'),supp=makeProject('MERGE_SUPP_QA','COMPANY','FORM_MAINT');
function api(path,token,method='GET',data) {const args=['-sS','--max-time','40','-X',method];if(token)args.push('-H','Authorization: Bearer '+token);if(data!==undefined)args.push('-H','Content-Type: application/json','--data-binary',JSON.stringify(data));args.push(BASE+path);return JSON.parse(cp.execFileSync('curl',args).toString())}
const tokens={};for(const role of Object.keys(roles)){const r=api('/auth/login',null,'POST',{username:'mergeqa_'+role,password:'100001'});assert.equal(r.code,0,'login '+role+':'+r.msg);tokens[role]=r.data.token;}
function ok(path,role='owner',method='GET',data){const r=api(path,tokens[role],method,data);assert.equal(r.code,0,path+' '+role+': '+r.msg);return r.data;}
function denied(path,role,method='POST',data){const r=api(path,tokens[role],method,data);assert.notEqual(r.code,0,'must deny '+path+' '+role);}
function upload(biz){const file='/tmp/rpm-merge-evidence.txt';fs.writeFileSync(file,'Merge QA evidence '+biz);const r=JSON.parse(cp.execFileSync('curl',['-sS','-H','Authorization: Bearer '+tokens.owner,'-F','file=@'+file,'-F','bizType='+biz,BASE+'/files/upload']).toString());assert.equal(r.code,0,r.msg);return r.data;}
const ov=ok('/projects/'+bpm+'/overview');
const payload={...ov.project,teamMembers:ov.teamMembers||ov.project.teamMembers,goal:'基本信息审批回写验证'};
assert(payload.teamMembers && payload.teamMembers.length,'team fixture');
ok('/projects/'+bpm+'/basic-draft','projectPm','PUT',payload);
assert.equal(sql(`SELECT goal FROM proj_info WHERE id=${bpm}`),'原目标','draft must not alter published info');
ok('/projects/'+bpm+'/basic-draft/submit','projectPm','POST');
denied('/projects/'+bpm+'/basic-draft','projectPm','PUT',payload);
denied('/projects/'+bpm+'/basic-draft/audit','hqStaff','POST',{pass:true});
for(const role of ['owner','unitHead','unitLeader','hqStaff']){const d=ok('/projects/'+bpm+'/basic-draft',role);assert(d.canAudit,'current reviewer '+role+' '+d.flowNode);ok('/projects/'+bpm+'/basic-draft/audit',role,'POST',{pass:true,opinion:'隔离测试通过'});}
assert.equal(ok('/projects/'+bpm+'/basic-draft').status,'APPROVED');
assert.equal(sql(`SELECT goal FROM proj_info WHERE id=${bpm}`),payload.goal);console.log('PASS basic draft privacy, 4-stage audit, permission and final DB writeback');
const mid=ok('/milestones','owner','POST',{projectId:bpm,name:'集成测试节点',planDate:'2026-12-01',year:2026});
ok('/projects/'+bpm+'/annual-plan','owner','PUT',{year:2026,annualGoal:'年度集成验证'});
ok('/projects/'+bpm+'/annual-plan/submit','owner','POST',{year:2026});
denied('/milestones/'+mid,'owner','PUT',{name:'审核中篡改'});denied('/milestones/'+mid,'owner','DELETE');
ok('/milestones/annual-plan/audit?projectId='+bpm+'&year=2026','unitHead','POST',{pass:true,remark:'基线审核'});
assert.equal(ok('/milestones/'+mid).baselinePlanDate,'2026-12-01');
denied('/milestones/'+mid,'owner','PUT',{year:2027});denied('/milestones/'+mid,'owner','PUT',{planDate:'2027-01-01'});
const delay=ok('/milestones/'+mid+'/delay','owner','POST',{newPlanDate:'2027-01-01',reason:'延期集成验证'});
const change=ok('/changes/'+delay.changeId);assert.equal(change.legacy,false);assert(change.canSubmit && change.revision!=null,'controlled change must be actionable');
denied('/milestones/'+mid+'/delay','owner','POST',{newPlanDate:'2027-02-01',reason:'重复申请'});
denied('/milestones/'+mid+'/materials','owner','POST',{fileUrl:'/files/fake.pdf',fileName:'fake.pdf'});
denied('/milestones/'+mid+'/close','owner','POST',{});
const evidence=upload('evidence');ok('/milestones/'+mid+'/materials','owner','POST',evidence);
ok('/milestones/'+mid+'/close','owner','POST',{});
denied('/milestones/'+mid+'/close-audit','unitHead','POST',{pass:true});
ok('/milestones/'+mid+'/close-audit','deptHead','POST',{pass:true,remark:'部门审核'});
ok('/milestones/'+mid+'/close-audit','unitHead','POST',{pass:true,remark:'单位审核'});
assert.equal(ok('/milestones/'+mid).status,'DONE');
assert(!ok('/milestones/mine','contactLogin').some(x=>x.projectId===bpm),'unrelated member must not see same-org milestones');
assert(!ok('/milestones/board','contactLogin').projects.some(x=>(x.projectId||x.id)===bpm));console.log('PASS annual-review lock, baseline, controlled delay, real evidence and two-stage close');
let ad=ok('/acceptance/'+acc);assert.equal(ad.expertReview,1);assert.equal(ad.canEditMaterials,true);
denied('/acceptance/'+acc,'contactLogin','GET');denied('/acceptance/'+acc+'/submit','owner','POST');
const mat=ad.items.find(x=>x.required&&!x.locked);
denied('/acceptance/'+acc+'/materials','owner','POST',{fieldCode:mat.fieldCode,fileUrl:'/files/fake.pdf'});
const acceptanceFile=upload('acceptance');
for(const item of ad.items.filter(x=>x.required&&!x.locked))ok('/acceptance/'+acc+'/materials','owner','POST',{...acceptanceFile,fieldCode:item.fieldCode});
ok('/acceptance/'+acc+'/submit','owner','POST');
denied('/acceptance/'+acc+'/submit','owner','POST');denied('/acceptance/'+acc+'/materials','owner','POST',{...acceptanceFile,fieldCode:mat.fieldCode});
denied('/acceptance/'+acc+'/audit','hqHead','POST',{pass:true});
ok('/acceptance/'+acc+'/audit','unitHead','POST',{pass:false,opinion:'请补充说明',nodeCode:'ACCEPT_UNIT_REVIEW'});
assert.equal(ok('/acceptance/'+acc).currentNode,'ACCEPT_APPLY');ok('/acceptance/'+acc+'/submit','owner','POST');
for(const [role,node] of [['unitHead','ACCEPT_UNIT_REVIEW'],['chief1','ACCEPT_CHIEF_REVIEW'],['hqStaff','ACCEPT_HQ_TECH'],['hqHead','ACCEPT_HQ_FINAL']]) {
 ad=ok('/acceptance/'+acc,role);assert.equal(ad.currentNode,node);assert.equal(ad.canAudit,true,'reviewer UI flag '+role);assert.equal(ad.canEditMaterials,false);assert.equal(ok('/acceptance/'+acc,'leader').canAudit,false);
 ok('/acceptance/'+acc+'/audit',role,'POST',{pass:true,opinion:'集成验证通过',nodeCode:node});
}
ad=ok('/acceptance/'+acc);assert.equal(ad.status,'DONE');assert(ad.partnerDueDate);assert.equal(sql(`SELECT status FROM proj_info WHERE id=${acc}`),'GOV_ACCEPTED');
denied('/acceptance/'+acc+'/submit','owner','POST');assert.equal(ok('/acceptance/'+acc+'/result-handoff','leader').resultReady,true);
console.log('PASS national acceptance materials, return/resubmit, chief/HQ chain, read-only review and completion lock');
fs.writeFileSync('/tmp/rpm-merge-qa-fixture.json',JSON.stringify({bpm,acc,supp}));
console.log('MERGE_WORKFLOW_QA_PASS '+JSON.stringify({bpm,acc,supp}));
