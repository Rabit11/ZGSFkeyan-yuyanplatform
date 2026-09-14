import {selectOneObject} from './select-objects.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
import {fixture,sql,root,quote} from './helpers.mjs';
if(process.env.CHANGE_QA_DB!=='rpm_change_lab')throw new Error('This verification uses lab data only');
const {chromium}=await import('/home/dev/.cache/aeroduct-browser/node_modules/playwright/index.mjs');
const origin=process.env.CHANGE_TUNNEL_ORIGIN || 'http://127.0.0.1:6006', output=root+'deploy/change-entry/';
const results=[],errors=[],requests=[],browserOrigins=[];let browser,page,proof;
const formalSql=s=>execFileSync('python3',[root+'qa/change/local-db.py'],{input:s,encoding:'utf8',env:{...process.env,CHANGE_QA_DB:'rpm'}}).trim();
const snapshot=()=>formalSql('SELECT COUNT(*),COALESCE(SUM(id),0) FROM proj_change; SELECT COUNT(*) FROM proj_change_control; SELECT COUNT(*) FROM proj_change_history; SELECT COUNT(*) FROM proj_change_attachment;SELECT id,goal FROM proj_info ORDER BY id;');
const before=snapshot();
async function check(name,fn){try{await fn();results.push({name,pass:true});console.log('PASS',name);}catch(e){results.push({name,pass:false,error:e.message});if(page)await page.screenshot({path:output+'failure.png',fullPage:true});throw e;}}
async function select(label,text){if(label === "变更对象")return selectOneObject(page,text);const control=page.getByLabel(label,{exact:true});await control.click();await control.locator('input').fill(text);await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({hasText:text}).first().click();}
async function findProof(){await page.getByLabel('搜索变更',{exact:true}).fill(proof.key);await page.getByRole('button',{name:/^查\s*询$/}).click();await page.locator('.application-link').filter({hasText:proof.key}).first().click();await page.locator('.ant-drawer-title').filter({hasText:'变更申请详情'}).waitFor();}
async function close(){await page.locator('.ant-drawer-content').getByRole('button',{name:/^关\s*闭$/}).click();await page.locator('.ant-drawer-content').waitFor({state:'hidden'});}
async function role(employee,label){await page.getByRole('button',{name:`${label} · ${employee}`,exact:true}).click();await page.waitForFunction(e=>JSON.parse(sessionStorage.getItem('rpm_change_lab_session_v2')||'null')?.user?.employeeNo===e,employee);await page.getByTestId('change-lab').getByText(new RegExp('当前身份：.*'+employee)).waitFor();}
try{
 browser=await chromium.launch({headless:true,args:['--no-sandbox']});page=await browser.newPage({viewport:{width:1440,height:1000}});
 page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{browserOrigins.push(new URL(r.url()).origin);if(r.url().includes('/api/changes/lab/'))requests.push({method:r.method(),path:new URL(r.url()).pathname});});
 if(process.env.CHANGE_TUNNEL_EMULATE === '1') await page.route(origin+'/**',async route=>{
  const url=new URL(route.request().url());
  // A single public HTTPS origin; /api is sent straight to the original backend.
  const upstream=(url.pathname.startsWith('/api/')?'http://127.0.0.1:8080':'http://127.0.0.1:6006')+url.pathname+url.search;
  const response=await route.fetch({url:upstream});await route.fulfill({response});
 });
 await page.route('**:6026/**',r=>r.abort('connectionrefused'));
 await page.addLocatorHandler(page.getByRole('button',{name:'稍后处理'}),async b=>b.click());
 await page.goto(origin+'/#/login');await page.getByPlaceholder('请输入工号').fill('100012');await page.getByPlaceholder('请输入密码').fill('100012');await page.getByRole('button',{name:/^登\s*录$/}).click();await page.waitForURL('**#/dashboard');
 await page.goto(origin+'/#/implement/change');await page.getByRole('button',{name:'进入测试模式',exact:true}).waitFor();
 const formalToken=await page.evaluate(()=>localStorage.getItem('rpm_token'));
 await check('E01 failed lab connection shows actionable error and never falls back to mock or formal writes',async()=>{
  await page.route('**/api/changes/lab/auth/login',r=>r.fulfill({status:502,contentType:'application/json',body:JSON.stringify({code:502,msg:'测试服务暂不可用，请重试'})}));
  await page.getByRole('button',{name:'进入测试模式',exact:true}).click();await page.getByText('测试服务暂不可用，请重试',{exact:true}).waitFor();
  assert.equal(await page.getByTestId('change-lab').count(),0);assert.equal(await page.evaluate(()=>sessionStorage.getItem('rpm_change_lab_session_v2')),null);assert.equal(snapshot(),before);
  await page.unroute('**/api/changes/lab/auth/login');
 });
 await check('E02 real entry stays on one origin while all browser connections to 6026 are blocked',async()=>{
  await page.getByRole('button',{name:'重试进入',exact:true}).click();await page.getByTestId('change-lab').waitFor();
  assert.equal(new URL(page.url()).origin,origin);assert.equal(browser.contexts().length,1);assert.equal(await page.evaluate(()=>localStorage.getItem('rpm_token')),formalToken);
  const session=await page.evaluate(()=>JSON.parse(sessionStorage.getItem('rpm_change_lab_session_v2')));assert.equal(session.user.employeeNo,'100012');assert.notEqual(session.user.token,formalToken);
  await page.screenshot({path:output+'01-entry.png',fullPage:true});
 });
 await check('E03 UI saves a real change row and uploads a real attachment into isolated storage',async()=>{
  proof=fixture('04ZXJX','【入口验证】真实变更流程，独立演练数据');
  await page.reload();await page.getByTestId('change-lab').waitFor();await page.getByRole('button',{name:/发起变更$/}).click();
  await select('关联项目',proof.key);await select('变更对象','项目目标 / 核心指标');
  await page.getByLabel('变更标题',{exact:true}).fill(proof.key+' 入口修复验证');await page.getByLabel('第1项调整后内容',{exact:true}).fill('通过真实数据库、真实材料上传与两级审批核验');await page.getByLabel('变更缘由',{exact:true}).fill('核验从原平台按钮进入隔离测试模式后的端到端持久化。');
  await page.getByRole('button',{name:'保存草稿',exact:true}).click();await page.locator('#change-materials button').filter({hasText:'上传支撑材料'}).click({trial:true});
  proof.changeId=Number(sql(`SELECT id FROM proj_change WHERE project_id=${proof.id} ORDER BY id DESC LIMIT 1`));assert.ok(proof.changeId>0);assert.equal(sql(`SELECT status FROM proj_change WHERE id=${proof.changeId}`),'DRAFT');
  await page.locator('#change-materials input[type=file]').setInputFiles({name:'真实接口验证依据.txt',mimeType:'text/plain',buffer:Buffer.from('这份文件由浏览器上传至独立 MinIO 存储，作为真实演练依据。')});await page.getByRole('button',{name:'真实接口验证依据.txt',exact:true}).waitFor();
  assert.equal(sql(`SELECT COUNT(*) FROM proj_change_attachment WHERE change_id=${proof.changeId}`),'1');
  const waiting=page.waitForEvent('download');await page.getByRole('button',{name:'真实接口验证依据.txt',exact:true}).click();const download=await waiting;assert.equal(await download.failure(),null);assert.equal(fs.readFileSync(await download.path(),'utf8'),'这份文件由浏览器上传至独立 MinIO 存储，作为真实演练依据。');
  await page.getByRole('button',{name:'一次提交 1 项',exact:true}).click();await page.getByRole('button',{name:'确认提交',exact:true}).click();await page.locator('.ant-drawer-title').filter({hasText:'变更申请详情'}).waitFor();
  assert.equal(sql(`SELECT status FROM proj_change WHERE id=${proof.changeId}`),'APPROVING');assert.equal(sql(`SELECT goal FROM proj_info WHERE id=${proof.id}`),'原核心指标');await close();
 });
 await check('E04 switching test actors preserves formal session; two actual approvals write exactly once',async()=>{
  await page.getByRole('button',{name:'使用场景与操作脚本',exact:true}).click();
  for(const [employee,label] of [['100005','单位主管'],['100004','总部科技主管']]){
   await role(employee,label);assert.equal(await page.evaluate(()=>localStorage.getItem('rpm_token')),formalToken);
   await page.getByRole('tab',{name:'待我办理',exact:true}).click();await findProof();await page.getByRole('button',{name:'办理审核',exact:true}).click();await page.getByLabel('办理意见',{exact:true}).fill(label+'核验真实材料与调整内容，通过');await page.getByRole('button',{name:'确认办理',exact:true}).click();
   await page.getByRole('button',{name:'确认办理',exact:true}).waitFor({state:'hidden'});await close();
  }
  assert.equal(sql(`SELECT status FROM proj_change WHERE id=${proof.changeId}`),'APPROVED');assert.equal(sql(`SELECT COUNT(*) FROM proj_change_history WHERE change_id=${proof.changeId} AND action='APPLY'`),'1');assert.equal(sql(`SELECT goal FROM proj_info WHERE id=${proof.id}`),'通过真实数据库、真实材料上传与两级审批核验');
  await page.getByRole('tab',{name:'全部变更',exact:true}).click();await findProof();await page.screenshot({path:output+'02-approved-proof.png',fullPage:true});await close();
 });
 await check('E05 exit restores original workbench and original data; no formal business rows changed',async()=>{
  await page.getByRole('button',{name:'退出测试模式',exact:true}).click();await page.getByRole('button',{name:'进入测试模式',exact:true}).waitFor();assert.equal(await page.getByTestId('change-lab').count(),0);assert.equal(await page.evaluate(()=>localStorage.getItem('rpm_token')),formalToken);assert.equal(snapshot(),before);
 });
 assert.deepEqual(errors,[]);assert.deepEqual([...new Set(browserOrigins)],[origin]);
 // Preserve the successful proof as an explicitly named lab record for user inspection.
 sql(`UPDATE proj_info SET project_no=${quote('LAB_PROOF_'+proof.id)} WHERE id=${proof.id}`);
 proof.projectNo='LAB_PROOF_'+proof.id;
}finally{
 fs.writeFileSync(output+'results.json',JSON.stringify({date:new Date().toISOString(),results,errors,requests,browserOrigins:[...new Set(browserOrigins)],proof,formalBusinessUnchanged:snapshot()===before},null,2));if(browser)await browser.close();
}
