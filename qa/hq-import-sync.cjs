const fs=require('fs'),cp=require('child_process'),assert=require('assert');
const {publishedProject,aggregateDashboard}=require('/tmp/rpm-hq-projection.cjs');
const sql=q=>cp.execFileSync('mysql',['--default-character-set=utf8mb4','-N','rpm_hq_qa_20260915','-e',q]).toString().trim();
function api(path,token,method='GET',data){const a=['-sS','--max-time','30','-X',method];if(token)a.push('-H','Authorization: Bearer '+token);if(data)a.push('-H','Content-Type: application/json','--data-binary',JSON.stringify(data));return JSON.parse(cp.execFileSync('curl',[...a,'http://127.0.0.1:18081/api'+path]).toString())}
const t={};for(const no of ['100003','100004','100005','100012','100002']){const r=api('/auth/login',null,'POST',{username:no,password:no});assert.equal(r.code,0,'login '+no);t[no]=r.data.token}
function ok(path,no='100003',method='GET',data){const r=api(path,t[no],method,data);assert.equal(r.code,0,no+' '+path+' '+r.msg);return r.data}
const owner=sql("SELECT real_name FROM sys_user WHERE employee_no='100012'");
sql("UPDATE sys_user SET org_id=(SELECT o.org_id FROM (SELECT org_id FROM sys_user WHERE employee_no='100012') o) WHERE employee_no='100005'");
const channel=Number(sql("SELECT id FROM proj_channel WHERE channel_code='YYGD' LIMIT 1"));assert(channel>0);
const ids=[];for(const no of ['100003','100004']){const id=ok('/projects',no,'POST',{name:'HQ_IMPORT_TEST_'+no,ownerName:owner+'（100012）',channelId:channel,channelName:'YYGD',levelCode:'COMPANY',status:'IMPLEMENTING',dataSource:'FORM_MAINT',startDate:'2026-01-01',endDate:'2027-12-31',totalFund:100});ids.push(id);assert.equal(sql('SELECT COUNT(*) FROM proj_info WHERE id='+id+' AND deleted=0 AND data_source="FORM_MAINT"'),'1')}
const id=ids[0],base='/supplement/'+id;
for(const no of ['100003','100004','100012','100002'])assert(ok('/projects?page=1&size=500',no).records.some(p=>p.id===id));
assert(ok('/supplement?view=mine','100012').some(p=>p.id===id));
assert.equal(aggregateDashboard({}, {projects:ok('/projects?page=1&size=500','100002').records}).kpis.projectCount,2);
console.log('PASS two HQ identities import into DB, owner ledger/supplement entry and leader statistics');
let s=ok(base,'100012').sections.find(s=>s.key==='basic');const values={...s.values};for(const f of s.fields){if(f.readonly)continue;if(values[f.key]===undefined||values[f.key]===null||values[f.key]==='')values[f.key]=f.type==='date'?(f.key==='endDate'?'2027-12-31':'2026-01-01'):f.type==='number'?10:f.options?f.options[0]:'HQ QA';}values.name='总部导入已审核补录结果';
s=ok(base+'/sections/basic','100012','PUT',{values,rows:[],version:s.version});assert.equal(sql(`SELECT COUNT(*) FROM proj_supplement_section WHERE project_id=${id}`),'1');assert.equal(ok(base+'/publication','100002').sections.length,0);
assert.equal(api(base+'/sections/basic',t['100003'],'PUT',{values,rows:[],version:s.version}).code,403);
s=ok(base+'/sections/basic/submit','100012','POST',{version:s.version});assert.equal(s.status,'UNIT_REVIEW');assert(ok('/projects?page=1&size=500','100002').records.find(p=>p.id===id).supplement.pending>0);
s=ok(base+'/sections/basic/audit','100005','POST',{version:s.version,action:'APPROVE',opinion:'QA单位审核'});s=ok(base+'/sections/basic/audit','100004','POST',{version:s.version,action:'APPROVE',opinion:'QA总部审核'});assert.equal(s.status,'APPROVED');
const records=ok('/projects?page=1&size=500','100002').records;assert.equal(publishedProject(records.find(p=>p.id===id)).name,values.name);assert.equal(ok(base+'/approved','100002').find(s=>s.key==='basic').values.name,values.name);assert.equal(ok('/projects/'+id+'/overview','100002').project.id,id);
const filtered=aggregateDashboard({projectType:values.projectType},{projects:records});assert.equal(filtered.kpis.projectCount,1);
console.log('PASS supplement DB, reviewer read-only, pending, unit/HQ approval, approved ledger/detail/board projection');
let filing=ok(base,'100012').sections.find(s=>s.key==='filing');const fv={...filing.values};for(const f of filing.fields)if(!fv[f.key])fv[f.key]=f.type==='date'?'2026-01-01':f.type==='number'?10:'QA备案';
filing=ok(base+'/sections/filing','100012','PUT',{values:fv,rows:[],version:filing.version});const temp='/tmp/rpm-hq-evidence.txt';fs.writeFileSync(temp,'HQ_IMPORT_EVIDENCE');
for(const m of filing.materials){const r=JSON.parse(cp.execFileSync('curl',['-sS','-H','Authorization: Bearer '+t['100012'],'-F','file=@'+temp,'-F','version='+filing.version,'http://127.0.0.1:18081/api'+base+'/sections/filing/materials/'+m.code]).toString());assert.equal(r.code,0,r.msg);filing=r.data}
const file=filing.materials.reduce((a,m)=>a.concat(m.files),[])[0];assert(file);assert.equal(api(base+'/files/'+file.id,t['100002']).code,403);
filing=ok(base+'/sections/filing/submit','100012','POST',{version:filing.version});for(const no of ['100005','100003'])filing=ok(base+'/sections/filing/audit',no,'POST',{version:filing.version,action:'APPROVE',opinion:'QA附件审核'});
const content=cp.execFileSync('curl',['-fsS','-H','Authorization: Bearer '+t['100002'],'http://127.0.0.1:18081/api'+base+'/files/'+file.id]).toString();assert.equal(content,'HQ_IMPORT_EVIDENCE');assert(ok(base+'/approved','100002').find(s=>s.key==='filing').materials.some(m=>m.files.length));
fs.writeFileSync('/tmp/rpm-hq-qa-result.json',JSON.stringify({status:'PASS',ids,fileId:file.id}));console.log('PASS material upload, approval, leader download; QA database only');
