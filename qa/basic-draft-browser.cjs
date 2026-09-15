const fs=require('fs'),http=require('http'),path=require('path'),assert=require('assert/strict');
const {chromium}=require('C:/Users/81172/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve('frontend/dist');
const server=http.createServer((q,s)=>{let f=path.join(root,q.url.split('?')[0]);if(!fs.existsSync(f)||fs.statSync(f).isDirectory())f=path.join(root,'index.html');s.setHeader('content-type',f.endsWith('.js')?'text/javascript':f.endsWith('.css')?'text/css':'text/html');fs.createReadStream(f).pipe(s)});
(async()=>{await new Promise(r=>server.listen(18084,'127.0.0.1',r));const browser=await chromium.launch({headless:true});try{
 const context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage();page.setDefaultTimeout(10000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await context.addInitScript(()=>{localStorage.setItem('rpm_token','test');localStorage.setItem('rpm_roles','["PROJECT"]');localStorage.setItem('rpm_name','申报负责人');localStorage.setItem('rpm_emp','91001')});
 const roles=['PROJECT_LEADER','PROJECT_CONTACT','TECH_LEADER','PROJECT_SUPERVISOR','L1_CHIEF','L2_CHIEF','DEPT_HEAD','HQ_DIRECTOR','HQ_SUPERVISOR','UNIT_MINISTER','UNIT_SUPERVISOR','HQ_FINANCE','UNIT_FIN_MINISTER','UNIT_FIN_SUPERVISOR'];
 const team=roles.map((roleCode,i)=>({roleCode,roleName:'申报旧岗位名称'+i,groupCode:'legacy',employeeNo:String(91001+i),userName:'申报人员'+i}));
 const project={id:41,projectNo:'XM41',name:'申报人员回显验证',dataSource:'PLATFORM',ownerName:team[0].userName,teamMembers:team,participants:[],annualPlans:[]};
 let draft={status:'NONE',canEdit:true,canSubmit:false,canAudit:false,payload:null,flowNodes:[],auditTrail:[]},fail=false,saved,submits=0,audits=0;
 await page.route('**/api/**',async r=>{
  const u=new URL(r.request().url()),method=r.request().method();let data=[];
  if(u.pathname==='/api/projects')data={records:[project],total:1};
  else if(u.pathname==='/api/projects/41')data=project;
  else if(u.pathname==='/api/users/candidates')data=[...team.map(m=>({employeeNo:m.employeeNo,realName:m.userName})),{employeeNo:'92000',realName:'新人员目录成员'}];
  else if(u.pathname==='/api/projects/41/basic-draft') {
   if(fail)return r.fulfill({status:500,json:{code:500,msg:'草稿读取测试失败'}});
   if(method==='PUT'){saved=r.request().postDataJSON();draft={...draft,status:'DRAFT',canSubmit:true,payload:saved}}
   data=draft;
  } else if(u.pathname==='/api/projects/41/basic-draft/submit'){submits++;draft={...draft,status:'APPROVING',canEdit:false,canSubmit:false,flowNodeName:'单位科技部长'};data=null}
  else if(u.pathname==='/api/projects/41/basic-draft/audit'){audits++;draft={...draft,status:'APPROVED',canAudit:false,payload:null};data=null}
  else if(u.pathname==='/api/auth/profile')data={realName:team[0].userName,employeeNo:'91001',roles:['PROJECT'],identityCode:'projectTeam'};
  return r.fulfill({json:{code:0,data}})
 });
 await page.goto('http://127.0.0.1:18084/#/implement/basic?projectId=41');
 const fields=page.locator('.ant-row-middle .ant-select');
 await page.getByRole('button',{name:'保存草稿',exact:true}).waitFor();
 assert.equal(await fields.count(),15);
 for(let i=0;i<14;i++)assert.match(await page.locator('.ant-form').innerText(),new RegExp('申报人员'+i+'（'));
 console.log('PASS declaration14roles prefill by roleCode with legacy names');
 const contact=fields.nth(1);await contact.click();await contact.locator('input').fill('92000');await page.getByTitle('新人员目录成员（92000）',{exact:true}).click();
 await page.getByRole('button',{name:'保存草稿',exact:true}).click();await page.getByText('草稿已保存，可继续编辑或提交审批',{exact:true}).waitFor();
 assert.equal(saved.teamMembers.filter(m=>m.roleCode==='PROJECT_CONTACT').length,1);assert.equal(saved.teamMembers.find(m=>m.roleCode==='PROJECT_CONTACT').employeeNo,'92000');assert.equal(saved.teamMembers.find(m=>m.roleCode==='PROJECT_CONTACT').userName,'新人员目录成员');assert.equal(project.teamMembers[1].employeeNo,'91002');
 await page.reload();await page.getByRole('button',{name:'提交审批',exact:true}).waitFor();assert.match(await fields.nth(1).innerText(),/新人员目录成员/);console.log('PASS edit/save/reload preserves draft and published team');
 await page.getByRole('button',{name:'提交审批',exact:true}).click();await page.getByText('当前节点：单位科技部长',{exact:true}).waitFor();assert.equal(submits,1);assert.equal(await page.getByRole('button',{name:'保存草稿',exact:true}).count(),0);console.log('PASS owner confirmation submits separate draft and locks approving form');
 draft.canAudit=true;await page.reload();await page.getByRole('button',{name:'审核通过',exact:true}).click();assert.equal(await page.getByRole('button',{name:'保存草稿',exact:true}).count(),0);await page.locator('.ant-modal').getByRole('button',{name:/确\s*定/}).click();await page.getByText('已审核通过',{exact:true}).waitFor();assert.equal(audits,1);console.log('PASS reviewer read-only form and dedicated audit API');
 fail=true;await page.reload();await page.getByText('草稿读取测试失败',{exact:true}).first().waitFor();assert.equal(await page.getByRole('button',{name:'保存草稿',exact:true}).count(),0);console.log('PASS draft read failure does not grant editing');
 assert.deepEqual(errors,[]);await page.screenshot({path:path.join(process.env.TEMP,'basic-draft-ui-verified.png')});
}finally{await browser.close();server.closeAllConnections();server.close()}})().catch(e=>{console.error(e);server.closeAllConnections();server.close();process.exit(1)});

