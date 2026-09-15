// Disposable database only. Real workflow actions never run against rpm.
const fs=require('fs'),cp=require('child_process'),assert=require('assert');
const DB='rpm_yzh_qa_20260915',BASE='http://127.0.0.1:18081/api';
const sql=q=>cp.execFileSync('mysql',['--default-character-set=utf8mb4','-N',DB,'-e',q]).toString().trim();assert.equal(sql('SELECT DATABASE()'),DB);
const posts={},tokens={};const ids={contact:'contactLogin',leader:'owner',techLeader:'techLead',supervisor:'projectPm',chief1:'chief1',chief2:'chief2',deptHead:'deptHead',hqDirector:'hqHead',hqSupervisor:'hqStaff',unitTechDirector:'unitHead',unitTechSupervisor:'unitStaff',hqFinance:'finHq',unitFinanceDirector:'finHead',unitFinanceSupervisor:'finStaff'};
assert.equal(sql("SELECT COUNT(*) FROM sys_user WHERE username LIKE 'yzhqa_%'"),'0');let i=0;
function api(p,t,m='GET',body){const a=['-sS','--max-time','30','-X',m];if(t)a.push('-H','Authorization: Bearer '+t);if(body!==undefined)a.push('-H','Content-Type: application/json','--data-binary',JSON.stringify(body));return JSON.parse(cp.execFileSync('curl',a.concat(BASE+p)).toString())}
let checks=0;function ok(p,role='leader',m='GET',b){const r=api(p,tokens[role],m,b);assert.equal(r.code,0,p+' '+role+': '+r.msg);checks++;return r.data;}
function deny(p,role,m='POST',b){assert.notEqual(api(p,tokens[role],m,b).code,0,'must deny '+p);checks++;}
for(const [post,identity] of Object.entries(ids)){const emp=String(993100+i++),name='YZH QA '+post;sql(`INSERT INTO sys_user(username,password,real_name,employee_no,org_id,identity_code,data_scope,status) SELECT 'yzhqa_${post}',password,'${name}','${emp}',993993,'${identity}','COMPANY',1 FROM sys_user WHERE username='100001'`);posts[post]=name+'（'+emp+'）';const r=api('/auth/login',null,'POST',{username:'yzhqa_'+post,password:'100001'});assert.equal(r.code,0,r.msg);tokens[post]=r.data.token;}
const channel=Number(sql("SELECT id FROM proj_channel WHERE channel_code='KJZ' LIMIT 1"));assert(channel);
const payload={name:'YZH_INITIATION_QA',channelId:channel,orgId:993993,orgName:'隔离单位',leadOrgName:'隔离单位',applyFund:10,startDate:'2026-01-01',endDate:'2028-01-01',goal:'隔离验证',posts};
const id=ok('/declarations','contact','POST',payload);assert.equal(ok('/declarations/'+id).declaration.posts.__workflow,'yzh-v1');
deny('/declarations','hqDirector','POST',payload);
deny('/declarations/'+id+'/filing-submit','leader');
let mats=ok('/declarations/'+id+'/materials');
if(mats.some(m=>m.required&&!m.locked))deny('/declarations/'+id+'/submit','contact');
const fp='/tmp/rpm-yzh-evidence.txt';fs.writeFileSync(fp,'Isolated yzh declaration materials');
const f=JSON.parse(cp.execFileSync('curl',['-sS','-H','Authorization: Bearer '+tokens.contact,'-F','file=@'+fp,'-F','bizType=declaration',BASE+'/files/upload']).toString());assert.equal(f.code,0,f.msg);
for(const m of mats.filter(m=>!m.locked))ok('/declarations/'+id+'/materials','contact','POST',{...f.data,fieldCode:m.fieldCode});
ok('/declarations/'+id+'/submit','contact','POST');
assert.equal(ok('/declarations/'+id).declaration.flowNode,'项目负责人');assert(ok('/declarations/pending','leader').some(d=>d.id===id));
deny('/declarations/'+id+'/audit','hqDirector','POST',{pass:true});deny('/declarations/'+id,'contact','PUT',payload);
ok('/declarations/'+id+'/audit','leader','POST',{pass:false,opinion:'补正'});assert.equal(ok('/declarations/'+id).declaration.status,'REJECTED');
ok('/declarations/'+id+'/submit','leader','POST');assert.equal(ok('/declarations/'+id).declaration.flowNode,'项目负责人');
for(const role of ['leader','deptHead','chief2','unitFinanceDirector','unitTechDirector','unitTechSupervisor','chief1','hqSupervisor'])ok('/declarations/'+id+'/audit',role,'POST',{pass:true,opinion:'隔离通过'});
assert.equal(ok('/declarations/'+id).declaration.status,'APPROVED');deny('/declarations/'+id+'/audit','hqSupervisor','POST',{pass:true});
const detail=ok('/declarations/'+id);const names=(detail.channel.filingMaterial||'').split(/[,，、;；/|]/).map(x=>x.trim()).filter(Boolean);
for(const name of names)ok('/declarations/'+id+'/materials','leader','POST',{...f.data,fieldCode:'F_'+name,fieldName:name});
const filing=ok('/declarations/'+id+'/filing-submit','leader','POST');deny('/declarations/'+id+'/filing','leader','POST',{pass:true});
ok('/declarations/'+id+'/filing','hqDirector','POST',{pass:true,opinion:'备案通过'});
assert.equal(sql('SELECT status FROM proj_info WHERE id='+filing.projectId),'IMPLEMENTING');assert.equal(Number(sql('SELECT COUNT(*) FROM proj_team_member WHERE project_id='+filing.projectId)),14);
console.log('PASS yzh fixed chain, owner self-submit, return, lock, HQ filing and 14-member DB writeback');
// A separate isolated channel exercises direct reporting; original channel is unchanged.
sql("INSERT INTO proj_channel(channel_code,channel_name,level_code,flow_nodes,declare_material,filing_material) VALUES('YZH_REPORT_QA','隔离报备','COMPANY','直接报备','','')");
const reportChannel=Number(sql("SELECT id FROM proj_channel WHERE channel_code='YZH_REPORT_QA'"));
const report=ok('/declarations','leader','POST',{...payload,name:'YZH_REPORT_QA',channelId:reportChannel});
ok('/declarations/'+report+'/submit','leader','POST');assert.equal(ok('/declarations/'+report).declaration.flowNode,'项目负责人');ok('/declarations/'+report+'/audit','leader','POST',{pass:true,opinion:'通过'});assert.equal(ok('/declarations/'+report).declaration.status,'REPORTED');
fs.writeFileSync('/tmp/rpm-yzh-qa-fixture.json',JSON.stringify({id,project:filing.projectId,report,checks}));console.log('YZH_QA_PASS '+JSON.stringify({id,checks}));
