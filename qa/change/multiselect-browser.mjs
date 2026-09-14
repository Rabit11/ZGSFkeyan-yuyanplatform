import assert from 'node:assert/strict';
import fs from 'node:fs';
import {fixture,clean,sql,root} from './helpers.mjs';
if(process.env.CHANGE_QA_DB!=='rpm_change_lab')throw Error('Lab only');
const {chromium}=await import('/home/dev/.cache/aeroduct-browser/node_modules/playwright/index.mjs');
const out=root+'deploy/change-multiselect/', origin='http://127.0.0.1:6006';
fs.mkdirSync(out,{recursive:true});
const f=fixture('04ZXJX','【多选验证】多个类别与业务对象');
f.secondMilestone=Number(sql(`INSERT INTO proj_milestone(project_id,name,plan_date,status) VALUES(${f.id},'新增试验节点','2027-06-02','DOING'); SELECT LAST_INSERT_ID()`));
const results=[],errors=[],saveBodies=[],submitRequests=[],origins=new Set();let browser,page,changeId,retainedProof=null;
const btn=name=>page.getByRole('button',name==='取消' ? {name:/^取\s*消$/} : name.includes('类别与对象') ? {name:new RegExp(name+'$')} : {name,exact:true});
const cards=()=>page.locator('.selected-items .selected-item');
const category=async name=>page.locator('.category-checkboxes .ant-checkbox-wrapper').filter({hasText:name}).click();
const object=async name=>page.locator('.object-choice').filter({hasText:name}).locator('.ant-checkbox-wrapper').click();
async function check(name,fn){await fn();results.push({name,pass:true});console.log('PASS',name);}
async function select(label,value){const el=page.getByLabel(label,{exact:true});await el.click();await el.locator('input').fill(value);await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({hasText:value}).first().click();}
async function date(index,value){const el=page.locator(`#change-item-${index}`);await el.click();await el.fill(value);await el.press('Enter');await el.press('Tab');}
async function save(){await btn('保存草稿').click();await page.locator('#change-materials button').filter({hasText:'上传支撑材料'}).click({trial:true});}
async function close(){await page.locator('.ant-drawer-content').getByRole('button',{name:/^关\s*闭$/}).click();await page.locator('.ant-drawer-content').waitFor({state:'hidden'});}
async function find(){await page.getByLabel('搜索变更',{exact:true}).fill(f.key);await page.getByRole('button',{name:/^查\s*询$/}).click();await page.locator('.application-link').filter({hasText:f.key}).first().click();await page.locator('.ant-drawer-title').filter({hasText:'变更申请详情'}).waitFor();}
async function role(emp,label){if(!await btn(`${label} · ${emp}`).isVisible())await btn('使用场景与操作脚本').click();await btn(`${label} · ${emp}`).click();await page.getByTestId('change-lab').getByText(new RegExp('当前身份：.*'+emp)).waitFor();}
async function submit(){await btn('一次提交 4 项').click();await btn('确认提交').click();await page.locator('.ant-drawer-title').filter({hasText:'变更申请详情'}).waitFor();}
try{
 browser=await chromium.launch({headless:true,args:['--no-sandbox']});page=await browser.newPage({viewport:{width:1440,height:1000}});
 page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{origins.add(new URL(r.url()).origin);if(r.method()==='POST'&&new URL(r.url()).pathname==='/api/changes/lab/changes')saveBodies.push(r.postDataJSON());if(r.method()==='POST'&&r.url().endsWith('/submit'))submitRequests.push(r.url());});
 await page.route('**:6026/**',r=>r.abort());await page.addLocatorHandler(page.getByRole('button',{name:'稍后处理'}),async b=>b.click());
 await page.goto(origin+'/#/login');await page.getByPlaceholder('请输入工号').fill('100012');await page.getByPlaceholder('请输入密码').fill('100012');await page.getByRole('button',{name:/^登\s*录$/}).click();await page.waitForURL('**#/dashboard');await page.goto(origin+'/#/implement/change');await btn('进入测试模式').click();await page.getByTestId('change-lab').waitFor();
 await check('D01 同时勾选3个类别、4个对象，包括两个不同里程碑',async()=>{
  await page.getByRole('button',{name:/发起变更$/}).click();await select('关联项目',f.key);await btn('多选变更类别与对象').click();
  for(const name of ['里程碑延期','交付物','经费调整'])await category(name);
  await object('测试里程碑');await object('新增试验节点');await object('交付物名称');await object('总经费（万元）');
  await page.screenshot({path:out+'01-multiple-categories-objects.png',fullPage:true});await btn('确认选择 4 项').click();await cards().nth(3).waitFor();assert.equal(await cards().count(),4);
  assert.equal(await page.locator('.selection-summary .ant-tag').count(),3);assert.equal(await cards().filter({has:page.locator('.ant-picker')}).count(),2);
 });
 await check('D02 自动展开各自的日期、文本、金额输入；漏填阻止整单保存',async()=>{
  await page.getByLabel('变更标题',{exact:true}).fill(f.key+' 多类别多对象一次提交');await page.getByLabel('变更缘由',{exact:true}).fill('联合调整两个里程碑、交付物与经费');
  await date(0,'2027-07-10');await date(1,'2027-07-11');await page.getByLabel('第3项调整后内容',{exact:true}).fill('联合调整后的交付物');await btn('保存草稿').click();
  await cards().nth(3).getByText('请填写调整后内容',{exact:true}).waitFor();assert.equal(saveBodies.length,0);
  await page.getByLabel('第4项调整后金额',{exact:true}).fill('290');await select('法务办理人','100009');await save();
  assert.equal(saveBodies.length,1);assert.equal(saveBodies[0].items.length,4);assert.deepEqual(saveBodies[0].items.filter(i=>i.targetKey==='milestoneDate').map(i=>Number(i.targetId)),[f.milestone,f.secondMilestone]);
  changeId=Number(sql(`SELECT id FROM proj_change WHERE project_id=${f.id} ORDER BY id DESC LIMIT 1`));assert.equal(sql(`SELECT COUNT(*) FROM proj_change_item WHERE change_id=${changeId}`),'4');assert.equal(sql(`SELECT COUNT(*) FROM proj_change WHERE project_id=${f.id}`),'1');
  await page.locator('.selected-items').scrollIntoViewIfNeeded();await page.screenshot({path:out+'02-independent-editors.png',fullPage:true});
 });
 await check('D03 增减类别需确认丢弃已有内容，取消选择保留4项及输入',async()=>{
  await btn('增减变更类别与对象').click();await category('经费调整');await btn('确认选择 3 项').click();await btn('返回选择').click();await btn('取消').click();
  assert.equal(await cards().count(),4);assert.equal(await page.getByLabel('第4项调整后金额',{exact:true}).inputValue(),'290');
  await btn('增减变更类别与对象').click();await btn('确认选择 4 项').click();assert.equal(await page.getByLabel('第3项调整后内容',{exact:true}).inputValue(),'联合调整后的交付物');
 });
 await check('D04 重开编辑保留独立值，多轮保存不覆盖第一项；一次提交4项',async()=>{
  await close();await find();await btn('编辑草稿').click();await date(0,'2027-07-12');await save();assert.equal(sql(`SELECT after_value FROM proj_change_item WHERE change_id=${changeId} AND target_key='milestoneDate' AND target_id=${f.milestone}`),'2027-07-12');
  assert.equal(sql(`SELECT MAX(round_no) FROM proj_change_round WHERE change_id=${changeId}`),'2');
  await page.locator('#change-materials input[type=file]').setInputFiles({name:'四对象联合变更依据.txt',mimeType:'text/plain',buffer:Buffer.from('两个里程碑、交付物与经费联合调整的真实演练依据。')});await btn('四对象联合变更依据.txt').waitFor();await submit();assert.equal(submitRequests.length,1);await close();
 });
 await check('D05 单位→法务→总部真实审批；4项同单同次生效',async()=>{
  for(const [emp,label,next] of [['100005','单位主管','100009'],['100009','演练法务','100004'],['100004','总部科技主管','全部变更项统一生效']]){
   await role(emp,label);await find();assert.match(await page.getByLabel('下一步周转方',{exact:true}).innerText(),new RegExp(next));assert.equal(sql(`SELECT plan_date FROM proj_milestone WHERE id=${f.milestone}`),'2027-06-01');
   assert.equal(await page.locator('.inspector-content > .selected-item').count(),4);await btn('办理审核').click();await page.getByLabel('办理意见',{exact:true}).fill('核对两个里程碑、交付物及经费，共4项，一并通过');await btn('确认办理').click();await btn('确认办理').waitFor({state:'hidden'});await close();
  }
  assert.equal(sql(`SELECT status FROM proj_change WHERE id=${changeId}`),'APPROVED');assert.equal(sql(`SELECT plan_date FROM proj_milestone WHERE id=${f.milestone}`),'2027-07-12');assert.equal(sql(`SELECT plan_date FROM proj_milestone WHERE id=${f.secondMilestone}`),'2027-07-11');assert.equal(sql(`SELECT name FROM proj_deliverable WHERE id=${f.deliverable}`),'联合调整后的交付物');assert.equal(sql(`SELECT total_fund FROM proj_info WHERE id=${f.id}`),'290.00');assert.equal(sql(`SELECT COUNT(*) FROM proj_change_history WHERE change_id=${changeId} AND action='APPLY'`),'1');
 });
 await check('D06 数据纠错同样支持多类别多对象，窄屏填写区可用',async()=>{
  await role('100012','项目负责人');await page.getByRole('button',{name:/发起变更$/}).click();await select('关联项目',f.key);await page.getByText('数据变更 · 纠正登记',{exact:true}).click();await btn('多选变更类别与对象').click();
  await category('基本信息纠错');await category('年度任务纠错');await object('主要工作内容纠错');await object('年度目标纠错');await btn('确认选择 2 项').click();assert.equal(await cards().count(),2);
  await page.getByLabel('第1项调整后内容',{exact:true}).fill('核对后的工作内容');await page.getByLabel('第2项调整后内容',{exact:true}).fill('核对后的年度目标');await page.setViewportSize({width:390,height:844});await cards().first().scrollIntoViewIfNeeded();await page.screenshot({path:out+'03-mobile-editors.png'});
  assert.equal(await page.locator('.ant-drawer-body').evaluate(el=>el.scrollWidth>el.clientWidth+1),false);assert.deepEqual(errors,[]);assert.deepEqual([...origins],[origin]);
 });
 await check('D07 可确认清空全部对象并重选类型，空清单不会提交',async()=>{
  await page.setViewportSize({width:1440,height:1000});await btn('增减变更类别与对象').click();await category('基本信息纠错');await category('年度任务纠错');await btn('清空已选对象').click();await btn('确认更新选择').click();assert.equal(await cards().count(),0);
  assert.equal(await page.getByRole('radio',{name:'项目变更 · 调整业务',exact:true}).isEnabled(),true);await btn('保存草稿').click();assert.equal(saveBodies.length,1);assert.deepEqual(errors,[]);
 });
 if(process.env.CHANGE_QA_KEEP_MULTIOBJECT_PROOF === '1') {
  const projectNo='LAB_PROOF_MULTI_'+f.id;sql(`UPDATE proj_info SET project_no='${projectNo}' WHERE id=${f.id}`);
  retainedProof={projectNo,projectId:f.id,changeId,itemCount:4,categories:3};
 }
} catch(e){results.push({name:'failure',pass:false,error:e.stack});if(page){await page.screenshot({path:out+'failure.png',fullPage:true});fs.writeFileSync(out+'failure.txt',await page.locator('body').innerText());}throw e;}finally{if(browser)await browser.close();if(!retainedProof)clean([f]);fs.writeFileSync(out+'browser-results.json',JSON.stringify({date:new Date().toISOString(),results,errors,origins:[...origins],saveBodies,submitRequests,retainedProof},null,2));}
