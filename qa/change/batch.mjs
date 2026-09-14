import assert from 'node:assert/strict';
import fs from 'node:fs';
import {randomUUID} from 'node:crypto';
import {fixture,clean,draft,good,api,upload,sql,root} from './helpers.mjs';
if(process.env.CHANGE_QA_DB!=='rpm_change_lab') throw Error('Batch tests require isolated lab');
const results=[], fixtures=[];
const fx=(channel='04ZXJX')=>{const f=fixture(channel,'【多项回归】');fixtures.push(f);return f;};
const item=(f,key,after)=>{const d=draft(f,key);return {targetKey:key,targetId:d.targetId,category:d.category,afterValue:after??d.afterValue};};
const body=(f,items,overrides={})=>({projectId:f.id,changeType:'PROJECT',title:f.key+' 多项变更',reason:'统一变更依据及影响分析',requestKey:randomUUID(),items,...overrides});
const save=(b)=>good('100012','','POST',b);
const review=(r,user,pass=true)=>good(user,`/${r.id}/audit`,'POST',{revision:r.revision,pass,opinion:pass?'核对本版全部变更项，通过':'请补正变更项后重新提交'});
const submit=async(r)=>{r=await upload('100012',r);return good('100012',`/${r.id}/submit`,'POST',{revision:r.revision});};
const fail=async(fn,code)=>assert.equal((await fn()).code,code);
async function test(name,fn){await fn();results.push({name,pass:true});console.log('PASS',name);}
try {
 await test('M01 多项保存、再次编辑、不可变版本和幂等请求',async()=>{
  const f=fx(), b=body(f,[item(f,'projectGoal'),item(f,'deliverableName')]);
  let r=await save(b);assert.equal(r.items.length,2);assert.equal(r.roundNo,1);
  assert.equal((await save(b)).id,r.id);assert.equal((await save(b)).roundNo,1);
  r=await good('100012',`/${r.id}`,'PUT',{...b,revision:r.revision,items:[item(f,'projectGoal','第二轮指标'),item(f,'paymentDate')]});
  assert.equal(r.roundNo,2);assert.equal(r.rounds[1].snapshot.items[1].targetKey,'deliverableName');assert.equal(r.rounds[1].snapshot.items[0].afterValue,'新核心指标');
  assert.equal(r.items[1].targetKey,'paymentDate');assert.equal(sql(`SELECT goal FROM proj_info WHERE id=${f.id}`),'原核心指标');
  assert.ok(r.nextHandlers.some(u=>u.employeeNo==='100005'));assert.ok(r.route[1].handlers.some(u=>u.employeeNo==='100004'));assert.match(r.returnTo,/100012/);
  const updated=await upload('100012',r);assert.equal(updated.roundNo,2);assert.equal(updated.revision,r.revision+1);
 });
 await test('M02 拒绝重复、混合类型、伪造类别、空清单和超过50项',async()=>{
  const f=fx();for(const items of [[item(f,'projectGoal'),item(f,'projectGoal')],[item(f,'projectGoal'),item(f,'projectName')],[{...item(f,'totalFund'),category:'INDICATOR'}],[],Array(51).fill(item(f,'projectGoal'))])
   await fail(()=>api('100012','','POST',body(f,items)),422);
  assert.equal(sql(`SELECT COUNT(*) FROM proj_change WHERE project_id=${f.id}`),'0');
 });
 await test('M03 任一非法或跨项目字段导致全部不保存',async()=>{
  const f=fx(), other=fx();
  for(const bad of [item(f,'totalFund','-1'),item(other,'deliverableName'),{...item(f,'projectGoal'),targetKey:'sqlInjection'}])
   await fail(()=>api('100012','','POST',body(f,[item(f,'paymentDate'),bad])),422);
  assert.equal(sql(`SELECT COUNT(*) FROM proj_change WHERE project_id=${f.id}`),'0');
 });
 await test('M04 普通项在前也不能绕过后续重大项法务；通过后全部一次生效',async()=>{
  const f=fx();let r=await save(body(f,[item(f,'projectGoal'),item(f,'totalFund')],{legalReviewerId:13}));
  assert.deepEqual(r.route.map(n=>n.code),['UNIT_REVIEW','LEGAL','HQ_REVIEW']);r=await submit(r);
  await fail(()=>api('100012',`/${r.id}`,'PUT',{...body(f,[item(f,'projectGoal')]),revision:r.revision}),409);
  r=await review(r,'100005');assert.ok(r.currentHandlers.some(u=>u.employeeNo==='100009'));assert.ok(r.nextHandlers.some(u=>u.employeeNo==='100004'));
  r=await review(r,'100009');assert.equal(sql(`SELECT goal FROM proj_info WHERE id=${f.id}`),'原核心指标');
  r=await review(r,'100004');assert.equal(r.status,'APPROVED');assert.equal(sql(`SELECT goal,total_fund FROM proj_info WHERE id=${f.id}`),'新核心指标\t250.00');
  assert.equal(sql(`SELECT COUNT(*) FROM proj_change_history WHERE change_id=${r.id} AND action='APPLY'`),'1');
  await fail(()=>api('100004',`/${r.id}/audit`,'POST',{revision:r.revision,pass:true,opinion:'重复审核'}),403);
 });
 await test('M05 周期延长与交付日期跨原周期可同单审批（不依赖清单顺序）',async()=>{
  const f=fx();let r=await save(body(f,[item(f,'deliverableDate','2029-06-01'),item(f,'projectEnd','2029-12-31')],{legalReviewerId:13}));
  r=await submit(r);for(const u of ['100005','100009','100004'])r=await review(r,u);
  assert.equal(sql(`SELECT end_date FROM proj_info WHERE id=${f.id}`),'2029-12-31');assert.equal(sql(`SELECT due_date FROM proj_deliverable WHERE id=${f.deliverable}`),'2029-06-01');
 });
 await test('M06 周期缩短可同时收拢交付与付款节点，未覆盖节点仍约束周期',async()=>{
  const f=fx();await fail(()=>api('100012','','POST',body(f,[item(f,'projectEnd','2027-06-15')])),422);
  let r=await save(body(f,[item(f,'projectEnd','2027-06-15'),item(f,'paymentDate','2027-06-10'),item(f,'deliverableDate','2027-06-12')],{legalReviewerId:13}));
  r=await submit(r);for(const u of ['100005','100009','100004'])r=await review(r,u);assert.equal(r.status,'APPROVED');
 });
 await test('M07 任一项快照冲突：终审失败，前项和办理状态均不改变',async()=>{
  const f=fx();let r=await submit(await save(body(f,[item(f,'projectGoal'),item(f,'deliverableName')])));r=await review(r,'100005');
  sql(`UPDATE proj_deliverable SET name='其他流程最新值' WHERE id=${f.deliverable}`);
  await fail(()=>api('100004',`/${r.id}/audit`,'POST',{revision:r.revision,pass:true,opinion:'审批'}),409);
  assert.equal(sql(`SELECT goal FROM proj_info WHERE id=${f.id}`),'原核心指标');assert.equal(sql(`SELECT name FROM proj_deliverable WHERE id=${f.deliverable}`),'其他流程最新值');
  const d=await good('100012',`/${r.id}`);assert.equal(d.revision,r.revision);assert.equal(d.status,'APPROVING');assert.equal(d.appliedAt,null);
 });
 await test('M08 退回、增删并修订、重新首节点提交及版本追溯',async()=>{
  const f=fx(), b=body(f,[item(f,'projectGoal'),item(f,'deliverableName')]);let r=await submit(await save(b));r=await review(r,'100005',false);
  assert.equal(r.status,'REJECTED');assert.ok(r.nextHandlers.some(u=>u.employeeNo==='100005'));
  r=await good('100012',`/${r.id}`,'PUT',{...b,revision:r.revision,items:[item(f,'projectGoal','补正指标'),item(f,'paymentDate')]});assert.equal(r.roundNo,2);
  r=await good('100012',`/${r.id}/submit`,'POST',{revision:r.revision});assert.equal(r.stepIndex,0);r=await review(r,'100005');r=await review(r,'100004');
  assert.equal(r.status,'APPROVED');assert.equal(r.rounds[1].snapshot.items[1].targetKey,'deliverableName');assert.equal(sql(`SELECT name FROM proj_deliverable WHERE id=${f.deliverable}`),'测试交付物');
 });
 await test('M09 多项数据纠错仍交总部科技主管，单位主管不能直接办结',async()=>{
  const f=fx('SHKJCX');let r=await save(body(f,[item(f,'projectName'),item(f,'annualGoal')],{changeType:'DATA'}));
  assert.deepEqual(r.route.map(n=>n.code),['UNIT_REVIEW','HQ_CONFIRM']);r=await submit(r);r=await review(r,'100005');assert.equal(r.status,'APPROVING');r=await review(r,'100004');assert.equal(r.status,'APPROVED');
 });
 await test('M10 多项MJKY明确交回发起人归档，真实附件后统一回写',async()=>{
  const f=fx('MJKY');let r=await submit(await save(body(f,[item(f,'projectGoal'),item(f,'deliverableName')])));r=await review(r,'100005');
  assert.ok(r.nextHandlers.some(u=>u.employeeNo==='100012'));r=await review(r,'100004');assert.equal(r.status,'AWAITING_ARCHIVE');assert.equal(sql(`SELECT goal FROM proj_info WHERE id=${f.id}`),'原核心指标');
  r=await upload('100012',r,'EXTERNAL');r=await good('100012',`/${r.id}/archive`,'POST',{revision:r.revision,reference:'多项归档-001',opinion:'已完成线下上报，核验回执'});assert.equal(r.status,'APPROVED');
 });
 await test('M11 旧客户端单项保存兼容、新旧版本并存、技术负责人周转清楚',async()=>{
  const f=fx();let r=await good('100014','','POST',draft(f));assert.equal(r.items.length,1);assert.ok(r.submitHandlers.some(u=>u.employeeNo==='100012'));assert.equal(r.canSubmit,false);
  await fail(()=>api('100014',`/${r.id}`,'PUT',{...draft(f),revision:r.revision-1}),409);
  assert.equal((await good('100014',`/${r.id}`)).roundNo,1);
 });
 await test('M12 旧受控单项无清单/版本时可读取，首次修改保留原版',async()=>{
  const f=fx();let r=await save(draft(f));sql(`DELETE FROM proj_change_item WHERE change_id=${r.id};DELETE FROM proj_change_round WHERE change_id=${r.id}`);
  r=await good('100012',`/${r.id}`);assert.equal(r.items.length,1);assert.equal(r.roundNo,0);
  r=await good('100012',`/${r.id}`,'PUT',{...body(f,[item(f,'projectGoal','新版本'),item(f,'paymentDate')]),revision:r.revision});
  assert.equal(r.roundNo,2);assert.equal(r.rounds[1].snapshot.items[0].afterValue,'新核心指标');
 });
} catch(e) {results.push({name:'failure',pass:false,error:e.stack});throw e;} finally {
 clean(fixtures);fs.writeFileSync(root+'deploy/change-batch/api-results.json',JSON.stringify({date:new Date().toISOString(),results},null,2));
}
