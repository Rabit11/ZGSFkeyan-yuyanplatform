// A retained, named rehearsal example; idempotent and never touches formal business data.
import fs from 'node:fs';
import {randomUUID} from 'node:crypto';
import {fixture,clean,sql,good,upload,root} from './helpers.mjs';
if(process.env.CHANGE_QA_DB!=='rpm_change_lab')throw Error('Lab only');
const key='LAB_PROOF_BATCH_V3';
const existing=sql(`SELECT id FROM proj_info WHERE project_no='${key}' AND deleted=0`);
if(existing){console.log('Existing retained batch example',existing);process.exit(0);}
const f=fixture('04ZXJX','【多项样例】三版修订、退回补正与法务审批');let kept=false;
const body={projectId:f.id,changeType:'PROJECT',title:'【多项样例】指标与经费联合调整',reason:'第一版：联合调整指标和经费，支撑材料待补充。',requestKey:randomUUID(),legalReviewerId:13,items:[{targetKey:'projectGoal',targetId:f.id,category:'INDICATOR',afterValue:'第一版：增加验证覆盖范围'},{targetKey:'totalFund',targetId:f.id,category:'FUND',afterValue:'280'}]};
const review=(r,emp,pass,opinion)=>good(emp,`/${r.id}/audit`,'POST',{revision:r.revision,pass,opinion});
try{
 let r=await good('100012','','POST',body);
 body.items[0].afterValue='第二版：增加验证覆盖范围并明确验收标准';body.reason='第二版：补充联合测算，指标调整与 280 万元预算统一论证。';
 r=await good('100012',`/${r.id}`,'PUT',{...body,revision:r.revision});r=await upload('100012',r,'SUPPORT','多项联合变更演练依据.txt','仅用于隔离演练：指标及总经费联合调整，演示版本记录和逐级审批。本文件通过真实接口上传。');
 r=await good('100012',`/${r.id}/submit`,'POST',{revision:r.revision});r=await review(r,'100005',false,'请补充指标与经费之间的对应关系。退回发起人修订。');
 body.reason='第三版：补充对应关系——增加验证覆盖范围所需试验与测量投入已纳入 280 万元预算，验收标准按第二版指标执行。';
 r=await good('100012',`/${r.id}`,'PUT',{...body,revision:r.revision});r=await good('100012',`/${r.id}/submit`,'POST',{revision:r.revision});
 for(const [emp,opinion] of [['100005','单位核验第三版补正说明，通过后交法务。'],['100009','演练法务核验本版经费与指标调整，通过后交总部。'],['100004','总部核验全部变更项及材料，终审通过并统一生效。']])r=await review(r,emp,true,opinion);
 if(r.status!=='APPROVED'||r.roundNo!==3||r.itemCount!==2)throw Error('Unexpected example state');
 sql(`UPDATE proj_info SET project_no='${key}' WHERE id=${f.id}`);kept=true;
 fs.writeFileSync(root+'deploy/change-batch/retained-proof.json',JSON.stringify({projectNo:key,projectId:f.id,changeId:r.id,changeNo:r.changeNo,status:r.status,roundNo:r.roundNo,itemCount:r.itemCount},null,2));console.log('Retained example',key,'change',r.id,'rounds',r.roundNo);
}finally{if(!kept)clean([f]);}
