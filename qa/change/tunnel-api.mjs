import assert from 'node:assert/strict';
import fs from 'node:fs';
const base='http://127.0.0.1:8080/api', results=[];
async function call(path,method='GET',body,headers={}){
 const r=await fetch(base+path,{method,headers:{...headers,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(10000)});return {status:r.status,body:await r.json()};
}
async function check(name,fn){await fn();results.push({name,pass:true});console.log('PASS',name);}
try{
 await check('T01 original backend rejects unauthenticated lab entry',async()=>assert.equal((await call('/changes/lab/auth/login','POST',{username:'100012',password:'100012'})).status,401));
 const f=await call('/auth/login','POST',{username:'100001',password:'100001'});assert.equal(f.body.code,0);const auth={Authorization:'Bearer '+f.body.data.token};
 let lab;
 await check('T02 lab login works directly through original backend, without any special frontend proxy',async()=>{const r=await call('/changes/lab/auth/login','POST',{username:'100012',password:'100012'},auth);assert.equal(r.body.code,0);lab=r.body.data.token;});
 await check('T03 original identity and independent lab token are both required',async()=>{
  assert.equal((await call('/changes/lab/changes/context','GET',undefined,auth)).status,401);
  assert.equal((await call('/changes/lab/changes/context','GET',undefined,{...auth,'X-Change-Lab-Token':'invalid'})).status,401);
  assert.equal((await call('/changes/lab/changes/context','GET',undefined,{'X-Change-Lab-Token':lab})).status,401);
 });
 await check('T04 authenticated bridge returns real isolated project context',async()=>{const r=await call('/changes/lab/changes/context','GET',undefined,{...auth,'X-Change-Lab-Token':lab});assert.equal(r.body.code,0);assert.equal(r.body.data.testMode,true);assert.equal(r.body.data.projects.filter(p=>p.projectNo.startsWith('LAB_CHANGE_S')).length,9);});
 await check('T05 bridge refuses other modules and recursive forwarding',async()=>{
  for(const p of ['/system/users','/auth/profile','/changes/lab/changes','/changes/abc'])assert.equal((await call('/changes/lab'+p,'GET',undefined,{...auth,'X-Change-Lab-Token':lab})).status,404);
 });
}finally{fs.writeFileSync('deploy/change-tunnel/api-results.json',JSON.stringify({date:new Date().toISOString(),results},null,2));}
