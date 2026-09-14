import assert from 'node:assert/strict';
import fs from 'node:fs';
import {base, root, sql, good, api, login, fixture, clean, draft, upload} from './helpers.mjs';
if (process.env.CHANGE_QA_DB !== 'rpm_change_lab') throw new Error('Run this suite only in the isolated lab');
const {chromium} = await import(process.env.PLAYWRIGHT_MODULE || '/home/dev/.cache/aeroduct-browser/node_modules/playwright/index.mjs');
const results=[], errors=[], fixtures=[];
const artifact=root+'deploy/change-v3/'; fs.mkdirSync(artifact,{recursive:true});
let browser;
async function check(name, fn) { try {await fn(); results.push({name,pass:true}); console.log('PASS', name);} catch(e) {results.push({name,pass:false,error:e.message}); if(browser){const p=browser.contexts()[0]?.pages()[0]; if(p){await p.screenshot({path:artifact+'failure.png',fullPage:true});fs.writeFileSync(artifact+'failure.txt',await p.locator('body').innerText());}} throw e;} }
async function session(user='100012', origin=base) {
 const page=await browser.newPage({viewport:{width:1600,height:1000}});
 page.on('pageerror',e=>errors.push(e.message));
 await page.addLocatorHandler(page.getByRole('button',{name:'稍后处理'}),async b=>b.click());
 await page.goto(origin+'/#/login');await page.getByPlaceholder('请输入工号').fill(user);await page.getByPlaceholder('请输入密码').fill(user);await page.getByRole('button',{name:/^登\s*录$/}).click();await page.waitForURL('**/#/dashboard');await page.waitForLoadState('networkidle');
 return page;
}
async function enter(page) {await page.goto(base+'/?labqa='+Date.now()+'#/implement/change');await page.getByTestId('change-lab').waitFor();}
try {
 await check('L01 backend identifies isolated environment and limits legal candidates', async()=>{
  const c=await good('100012','/context');assert.equal(c.testMode,true);assert.deepEqual(c.legalReviewers.map(u=>u.employeeNo),['100009']);
  assert.equal(c.projects.filter(p=>p.projectNo.startsWith('LAB_CHANGE_')).length,9);
 });
 await check('L02 lab token is rejected by formal backend; formal test flag cannot be forged',async()=>{
  const token=await login('100012');
  const r=await fetch('http://127.0.0.1:6006/api/changes/context?testMode=true',{headers:{Authorization:`Bearer ${token}`}});
  const b=await r.json();assert.notEqual(b.code,0);
 });
 await check('L03 non-qualified legal user cannot be assigned; missing legal blocks only submit',async()=>{
  const f=fixture();fixtures.push(f);
  assert.equal((await api('100012','','POST',draft(f,'totalFund',{legalReviewerId:14}))).code,422);
  let d=await good('100012','','POST',draft(f,'totalFund',{legalReviewerId:null}));d=await upload('100012',d);
  assert.ok(d.readinessIssues.some(s=>s.includes('法务')));
  assert.equal((await api('100012',`/${d.id}/submit`,'POST',{revision:d.revision})).code,422);
 });
 await check('L04 missing downstream staff prevents submission; no stranded approval is created',async()=>{
  const f=fixture();fixtures.push(f);
  sql(`UPDATE proj_team_member SET employee_no='MISSING_QA' WHERE project_id=${f.id} AND role_code IN ('HQ_DIRECTOR','HQ_SUPERVISOR')`);
  let d=await good('100012','','POST',draft(f,'projectGoal'));d=await upload('100012',d);
  assert.ok(d.readinessIssues.some(s=>s.includes('总部')));
  assert.equal((await api('100012',`/${d.id}/submit`,'POST',{revision:d.revision})).code,422);
  assert.equal((await good('100012',`/${d.id}`)).status,'DRAFT');
 });
 await check('L05 technical draft appears in project owner inbox and exposes actual next handlers',async()=>{
  const f=fixture();fixtures.push(f);
  let d=await good('100014','','POST',draft(f,'projectGoal'));d=await upload('100014',d);
  assert.ok((await good('100012','?mine=true&size=200')).records.some(r=>r.id===d.id));
  assert.equal((await good('100014',`/${d.id}`)).canSubmit,false);
  d=await good('100012',`/${d.id}/submit`,'POST',{revision:d.revision});
  assert.deepEqual(d.currentHandlers.map(h=>h.employeeNo),['100005']);
 });
 browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 const page=await session();await enter(page);
 await check('L06 route opens the new workspace and exposes nine detailed scenarios',async()=>{
  assert.equal(await page.locator('.change-heading').evaluate(el=>getComputedStyle(el).backgroundColor),'rgb(35, 68, 91)');
  await page.getByRole('button',{name:'使用场景与操作脚本'}).click();
  assert.equal(await page.getByRole('navigation',{name:'演练场景'}).getByRole('button').count(),9);
  await page.screenshot({path:artifact+'01-lab-workspace.png',fullPage:true});
 });
 await check('L07 scenario prefill retains explicit save/upload/submit and material guidance',async()=>{
  await page.getByRole('button',{name:'开始此场景',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('[aria-label="变更标题"]')?.value === 'S01 一般变更 · 里程碑延期');
  assert.equal(await page.getByLabel('变更标题',{exact:true}).inputValue(),'S01 一般变更 · 里程碑延期');
  assert.equal(await page.getByLabel('第1项调整后日期',{exact:true}).inputValue(),'2027-07-01');
  assert.equal(await page.getByRole('button',{name:'一次提交 1 项',exact:true}).count(),0);
  await page.getByText('建议提供：延期原因、纠偏计划及后续节点影响说明。',{exact:false}).waitFor();
  await page.screenshot({path:artifact+'02-object-inspector.png',fullPage:true});
 });
 await check('L08 browser reload prompts for unsaved content; cancel preserves editor',async()=>{
  const dialogPromise=page.waitForEvent('dialog');
  const reload=page.reload({timeout:5000}).catch(()=>{});
  const dialog=await dialogPromise;assert.equal(dialog.type(),'beforeunload');await dialog.dismiss();await reload;
  assert.equal(await page.getByLabel('变更标题',{exact:true}).inputValue(),'S01 一般变更 · 里程碑延期');
 });
 await check('L09 in-app route leave can be cancelled and preserves unsaved values',async()=>{
  await page.evaluate(()=>{location.hash='#/implement/milestone'});
  await page.getByText('离开项目变更并放弃未保存内容？',{exact:true}).waitFor();
  await page.getByRole('button',{name:'继续编辑',exact:true}).click();
  await page.waitForURL('**#/implement/change');
  assert.equal(await page.getByLabel('变更标题',{exact:true}).inputValue(),'S01 一般变更 · 里程碑延期');
 });
 await check('L10 confirmed route leave removes drawer and leaves other page styles identical',async()=>{
  const other=await session();await other.goto(base+'/?beforecss=1#/implement/milestone');await other.waitForLoadState('networkidle');
  const styles=()=>{const selectors=['.ant-layout-header','.ant-layout-sider','.ant-table-thead th','.page-title'];return Object.fromEntries(selectors.map(s=>{const e=document.querySelector(s);if(!e)return[s,null];const c=getComputedStyle(e);return[s,{background:c.backgroundColor,color:c.color,height:c.height,font:c.fontSize,border:c.borderRadius}]}))};
  const before=await other.evaluate(styles);
  await page.evaluate(()=>{location.hash='#/implement/milestone'});await page.getByRole('button',{name:'放弃并离开',exact:true}).click();await page.waitForURL('**#/implement/milestone');await page.waitForLoadState('networkidle');
  assert.equal(await page.locator('.change-drawer').count(),0);assert.equal(await page.getByTestId('change-lab').count(),0);
  assert.deepEqual(await page.evaluate(styles),before);
  fs.writeFileSync(artifact+'style-isolation.json',JSON.stringify({before,after:await page.evaluate(styles)},null,2));
  await page.screenshot({path:artifact+'03-other-page-after-leave.png',fullPage:true});await other.close();
 });
 await check('L11 explicit role switch logs into isolated backend and keeps scenario selection',async()=>{
  await enter(page);await page.getByRole('button',{name:'使用场景与操作脚本'}).click();
  await page.getByRole('navigation',{name:'演练场景'}).getByRole('button').filter({hasText:'S06'}).click();
  await page.getByRole('button',{name:'技术负责人 · 100014',exact:true}).click();
  await page.getByTestId('change-lab').getByText(/当前身份：沈知行/).waitFor();
  assert.match(await page.locator('.lab-content nav button.active').innerText(),/S06[\s\S]*技术负责人/);
  assert.equal(await page.getByRole('button',{name:'开始此场景',exact:true}).isEnabled(),true);
 });
 await check('L12 narrow inspector has visible action buttons and no viewport overflow',async()=>{
  await page.setViewportSize({width:768,height:1000});await page.getByRole('button',{name:'开始此场景',exact:true}).click();
  await page.getByRole('button',{name:'保存草稿',exact:true}).click({trial:true});
  const box=await page.getByRole('button',{name:'保存草稿',exact:true}).boundingBox();assert.ok(box.x>=0&&box.x+box.width<=768.5);
  const width=await page.locator('.ant-drawer-body').evaluate(e=>({scroll:e.scrollWidth,client:e.clientWidth}));assert.ok(width.scroll<=width.client+1);
  await page.screenshot({path:artifact+'04-narrow-inspector.png',fullPage:true});
  await page.locator('.ant-drawer-content').getByRole('button',{name:/^关\s*闭$/}).click();await page.getByRole('button',{name:'放弃修改'}).click();
 });
 await check('L13 formal workspace has only a test entry; URL parameters cannot enable lab controls',async()=>{
  const p=await session('100012','http://127.0.0.1:6006');await p.goto('http://127.0.0.1:6006/?testMode=true#/implement/change');
  await p.getByRole('heading',{name:'项目变更',exact:true}).waitFor();await p.waitForLoadState('networkidle');
  assert.equal(await p.getByTestId('change-lab').count(),0);assert.ok(await p.getByRole('button',{name:'进入测试模式',exact:true}).isVisible());
  const context=await p.evaluate(async()=>{return (await (await fetch('/api/changes/context?testMode=true',{headers:{Authorization:'Bearer '+localStorage.getItem('rpm_token')}})).json()).data});
  assert.equal(context.testMode,false);assert.deepEqual(context.legalReviewers,[]);
  await p.screenshot({path:artifact+'05-formal-workspace.png',fullPage:true});await p.close();
 });
 assert.deepEqual(errors,[]);
} finally {
 fs.writeFileSync(artifact+'lab-results.json',JSON.stringify({date:new Date().toISOString(),results,errors},null,2));
 if(browser)await browser.close();clean(fixtures);
}
