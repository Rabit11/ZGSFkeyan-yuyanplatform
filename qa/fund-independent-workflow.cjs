// Run only on disposable rpm_fund_qa_20260915 database/backend18081.
const fs=require('fs'),cp=require('child_process'),assert=require('assert');
const DB='rpm_fund_qa_20260915',BASE='http://127.0.0.1:18081/api';
const sql=q=>cp.execFileSync('mysql',['--default-character-set=utf8mb4','-N',DB,'-e',q]).toString().trim();
assert.equal(sql('SELECT DATABASE()'),DB);
const roles={owner:'PROJECT_LEADER',projectPm:'PROJECT_SUPERVISOR',finHead:'UNIT_FIN_MINISTER',finStaff:'UNIT_FIN_SUPERVISOR',finHq:'HQ_FINANCE',leader:null,contactLogin:null};
assert.equal(sql("SELECT COUNT(*) FROM sys_user WHERE username LIKE 'fundqa_%'"),'0');
let index=0;
for(const [role,post] of Object.entries(roles)) {
 const emp=String(992100+(index++)),scope=['finHq','leader'].includes(role)?'COMPANY':['finHead','finStaff'].includes(role)?'UNIT':'SELF';
 sql(`INSERT INTO sys_user(username,password,real_name,employee_no,org_id,identity_code,data_scope,status) SELECT 'fundqa_${role}',password,'Fund QA ${role}','${emp}',992992,'${role}','${scope}',1 FROM sys_user WHERE username='100001'`);
}
sql("INSERT INTO proj_info(project_no,name,start_date,end_date,channel_id,channel_name,level_code,status,data_source,org_id,org_name,owner_name,total_fund) SELECT 'FUND_INDEPENDENT_QA','独立经费无里程碑测试','2026-01-01','2028-12-31',id,channel_name,'COMPANY','IMPLEMENTING','FORM_MAINT',992992,'隔离测试单位','Fund QA owner',100 FROM proj_channel LIMIT 1");
const project=Number(sql("SELECT id FROM proj_info WHERE project_no='FUND_INDEPENDENT_QA'"));assert(project>0);
for(const [role,post] of Object.entries(roles))if(post)sql(`INSERT INTO proj_team_member(project_id,group_code,role_code,role_name,user_name,employee_no) SELECT ${project},'MGMT','${post}','${post}',real_name,employee_no FROM sys_user WHERE username='fundqa_${role}'`);
function api(path,token,method='GET',data){const a=['-sS','--max-time','30','-X',method];if(token)a.push('-H','Authorization: Bearer '+token);if(data!==undefined)a.push('-H','Content-Type: application/json','--data-binary',JSON.stringify(data));return JSON.parse(cp.execFileSync('curl',a.concat(BASE+path)).toString())}
const tokens={};for(const role of Object.keys(roles)){const r=api('/auth/login',null,'POST',{username:'fundqa_'+role,password:'100001'});assert.equal(r.code,0,r.msg);tokens[role]=r.data.token;}
let checks=0;
function ok(path,role='owner',method='GET',data){const r=api(path,tokens[role],method,data);assert.equal(r.code,0,path+' '+role+':'+r.msg);checks++;return r.data;}
function denied(path,role,method='POST',data){const r=api(path,tokens[role],method,data);assert.notEqual(r.code,0,'must deny '+path+' '+role);checks++;}
const budget={projectId:project,year:2026,budgetName:'年度试验费',amount:20,status:'DRAFT'};
const id=ok('/fund/budgets','projectPm','POST',budget);
assert.equal(ok('/projects/'+project+'/fund/budgets')[0].budgetName,budget.budgetName);
denied('/fund/budgets/'+id,'projectPm','PUT',{...budget,status:'PENDING'});
ok('/fund/budgets/'+id,'owner','PUT',{...budget,status:'PENDING'});
denied('/fund/budgets/'+id,'owner','DELETE');
denied('/fund/budgets/'+id,'finHq','PUT',{status:'APPROVED'});
denied('/fund/budgets/'+id,'finHead','PUT',{status:'UNIT_OK',amount:19});
denied('/fund/budgets/'+id,'contactLogin','PUT',{status:'UNIT_OK'});
ok('/fund/budgets/'+id,'finStaff','PUT',{status:'UNIT_OK'});
ok('/fund/budgets/'+id,'finHq','PUT',{status:'PENDING'});
ok('/fund/budgets/'+id,'finHead','PUT',{status:'DRAFT'});
ok('/fund/budgets/'+id,'owner','PUT',{...budget,status:'PENDING'});
ok('/fund/budgets/'+id,'finHead','PUT',{status:'UNIT_OK'});
ok('/fund/budgets/'+id,'finHq','PUT',{status:'APPROVED'});
denied('/fund/budgets/'+id,'owner','PUT',{...budget,status:'DRAFT'});
denied('/fund/budgets','owner','POST',{...budget,budgetName:'伪造备案',status:'APPROVED'});
denied('/fund/budgets','owner','POST',{...budget,budgetName:'超总额',amount:99});
denied('/projects/'+project+'/fund/budgets','contactLogin','GET');
ok('/projects/'+project+'/fund/budgets','leader');
console.log('PASS budget independent of milestones, role separation, returns, frozen review and overbudget validation');
const file='/tmp/rpm-fund-evidence.txt';fs.writeFileSync(file,'Isolated finance receipt');
const upload=JSON.parse(cp.execFileSync('curl',['-sS','-H','Authorization: Bearer '+tokens.finHead,'-F','file=@'+file,'-F','bizType=fund',BASE+'/files/upload']).toString());assert.equal(upload.code,0,upload.msg);const f=upload.data;
const pay={projectId:project,flowType:'WRITEOFF',amount:2,occurDate:'2026-09-15',voucherNo:'FUND-QA-001',remark:'试验支付||'+f.fileName+'||'+f.fileUrl,writeoffStatus:'WRITTEN'};
denied('/fund/payments','owner','POST',pay);denied('/fund/payments','finStaff','POST',pay);
denied('/fund/payments','finHead','POST',{...pay,occurDate:'2027-01-01'});
denied('/fund/payments','finHead','POST',{...pay,remark:'用途||fake||/api/files/fund/missing.pdf'});
const payment=ok('/fund/payments','finHead','POST',{...pay,writeoffStatus:'DRAFT'});
ok('/fund/payments/'+payment,'finHead','PUT',pay);
denied('/fund/payments/'+payment,'finHead','PUT',pay);
denied('/fund/payments','finHead','POST',pay);
assert.equal(Number(sql('SELECT COUNT(*) FROM fund_payment WHERE project_id='+project)),1);
assert.equal(sql('SELECT IFNULL(budget_id,\'NULL\') FROM fund_payment WHERE id='+payment),'NULL');
assert.equal(sql('SELECT IFNULL(milestone_id,\'NULL\') FROM fund_budget WHERE id='+id),'NULL');
assert.equal(sql('SELECT COUNT(*) FROM proj_milestone WHERE project_id='+project),'0');
ok('/fund/payments/'+payment+'/reverse','finHead','POST',{reason:'隔离红冲验证'});
denied('/fund/payments/'+payment+'/reverse','finHead','POST',{reason:'重复'});
assert.equal(Number(sql("SELECT SUM(amount) FROM fund_payment WHERE project_id="+project+" AND writeoff_status='WRITTEN'")),0);
ok('/projects/'+project+'/fund/payments','leader');
fs.writeFileSync('/tmp/rpm-fund-qa-fixture.json',JSON.stringify({project,budget:id,payment,checks}));
console.log('FUND_WORKFLOW_QA_PASS '+JSON.stringify({project,checks}));
