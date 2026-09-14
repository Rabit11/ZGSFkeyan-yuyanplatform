import assert from 'node:assert/strict'
import { test } from 'node:test'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import { webcrypto } from 'node:crypto'
import { mountSource, vue, flush, deferred, root } from './vue-harness.mjs'
const require = createRequire(path.join(root,'package.json')), ts = require('typescript')
function loader(stubs={}) {
  const cache=new Map()
  function load(file) {
    file=path.resolve(root,'src',file)
    if(!path.extname(file))file+='.ts'
    if(cache.has(file)) return cache.get(file)
    const mod={exports:{}};cache.set(file,mod.exports)
    const code=ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,esModuleInterop:true}}).outputText
    const localRequire=name=>{
      if(name in stubs)return stubs[name]
      if(name.startsWith('@/'))return load(name.slice(2))
      if(name.startsWith('.'))return load(path.resolve(path.dirname(file),name))
      return require(name)
    }
    vm.runInNewContext(code,{module:mod,exports:mod.exports,require:localRequire,console,Date,URL,FormData,File,crypto:webcrypto},{filename:file})
    return mod.exports
  }
  return load
}
const helpers=loader()('utils/transformPackage.ts')
function fixture() {
  const db={projects:[{id:101,name:'隔离项目',projectNo:'XM101'}],transforms:[],deliverables:[{id:1,projectId:101,status:'DELIVERED',name:'成果1'},{id:2,projectId:101,status:'DELIVERED',name:'成果2'},{id:3,projectId:101,status:'PENDING'},{id:4,projectId:102,status:'DELIVERED'}],dicts:[
    ...['MODEL','MARKET'].map(dictCode=>({dictType:'TRANSFORM_WAY',dictCode})),
    ...Object.entries(helpers.FORM_PARENT).map(([dictCode,parentCode])=>({dictType:'TRANSFORM_FORM',dictCode,parentCode})),
    ...Object.keys(helpers.STATUS_TEXT).map(dictCode=>({dictType:'TRANSFORM_STATUS',dictCode})),
  ]}
  let user={identityCode:'owner',realName:'测试负责人',employeeNo:'100012',roles:[]}
  const load=loader({'./data':db,'./dashboard':{currentMockUser:()=>user,visibleProjects:()=>user.identityCode==='outsider'?[]:db.projects}})
  const api=load('mock/transform.ts').mockTransform
  const call=(method,path,body={})=>api(method,path,{},body)
  const payload=()=>({name:'验证成果包',projectId:101,transformWay:'MODEL',transformForm:'INSTALLED',planDate:'2030-01-01',dutyOrg:'验证单位',introDetail:'2026年计划，型号应用，验证单位，2026年完成',status:'NOT_STARTED',deliverableIds:[1],evidenceJson:'[]'})
  const create=()=>{const r=call('POST','/transforms',payload());assert.equal(r.code,0,r.msg);return r.data}
  const detail=id=>call('GET','/transforms/'+id).data
  const act=(id,action,note='')=>call('POST',`/transforms/${id}/workflow`,{action,note,revision:detail(id).revision})
  return {db,call,payload,create,detail,act,user:code=>{user={...user,identityCode:code}}}
}
test('成果包创建与交付物回写同一编号；编号由服务生成',()=>{
 const f=fixture(),r=f.call('POST','/transforms',{...f.payload(),id:999,achievementNo:'伪造',workflowStatus:'RECORDED',itemCount:999})
 assert.equal(r.code,0);const d=f.detail(r.data);assert.notEqual(d.achievementNo,'伪造');assert.equal(d.workflowStatus,'DRAFT');assert.equal(d.itemCount,1);assert.equal(f.db.deliverables[0].achievementNo,d.achievementNo)
})
test('未交付、跨项目、重复绑定校验失败不创建成果包',()=>{
 for(const ids of [[1,3],[4],[1,1],[]]){const f=fixture(),r=f.call('POST','/transforms',{...f.payload(),deliverableIds:ids});assert.notEqual(r.code,0);assert.equal(f.db.transforms.length,0);assert.equal(f.db.deliverables[0].achievementNo,undefined)}
})
test('其他成果包占用不能抢占，失败后旧绑定完整保留',()=>{
 const f=fixture(),id=f.create();const second=f.call('POST','/transforms',{...f.payload(),deliverableIds:[2]}).data
 const r=f.call('POST',`/transforms/${id}/bind`,{revision:0,deliverableIds:[2]});assert.notEqual(r.code,0)
 assert.equal(f.detail(id).deliverableIds.join(','),'1');assert.equal(f.detail(second).deliverableIds.join(','),'2')
})
test('替换绑定清除旧交付物反向关联',()=>{
 const f=fixture(),id=f.create();assert.equal(f.call('POST',`/transforms/${id}/bind`,{revision:0,deliverableIds:[2]}).code,0)
 assert.equal(f.db.deliverables[0].achievementNo,undefined);assert.equal(f.detail(id).deliverableIds.join(','),'2')
})
test('陈旧版本不能覆盖新内容',()=>{
 const f=fixture(),id=f.create();assert.equal(f.call('PUT',`/transforms/${id}`,{...f.payload(),revision:0,name:'新内容'}).code,0)
 assert.equal(f.call('PUT',`/transforms/${id}`,{...f.payload(),revision:0,name:'旧内容'}).code,409);assert.equal(f.detail(id).name,'新内容')
})
test('负责人提交、二级退回重提、审核通过、总部备案、更新进展完整闭环',()=>{
 const f=fixture(),id=f.create();assert.equal(f.act(id,'SUBMIT').code,0)
 assert.notEqual(f.act(id,'APPROVE').code,0)
 f.user('unitHead');assert.notEqual(f.act(id,'REJECT').code,0);assert.equal(f.act(id,'REJECT','补充转化说明').code,0)
 f.user('owner');assert.equal(f.act(id,'SUBMIT').code,0)
 f.user('hqHead');assert.notEqual(f.act(id,'RECORD').code,0)
 f.user('unitHead');assert.equal(f.act(id,'APPROVE').code,0)
 f.user('hqHead');assert.equal(f.act(id,'RECORD').code,0);assert.equal(f.detail(id).workflowStatus,'RECORDED')
 f.user('owner');assert.equal(f.act(id,'REOPEN').code,0);assert.equal(f.detail(id).workflowStatus,'DRAFT');assert.equal(JSON.parse(f.detail(id).historyJson).length,7)
})
test('审核中禁止编辑和绑定，业务进度不会越过审核',()=>{
 const f=fixture(),id=f.create();f.act(id,'SUBMIT')
 assert.notEqual(f.call('PUT',`/transforms/${id}`,{...f.payload(),revision:1}).code,0)
 assert.notEqual(f.call('POST',`/transforms/${id}/bind`,{deliverableIds:[2],revision:1}).code,0)
 assert.equal(f.detail(id).workflowStatus,'UNIT_REVIEW')
})
test('完成转化需要实际日期与已上传佐证，允许上传后完整保存',()=>{
 const f=fixture(),id=f.create();assert.notEqual(f.call('PUT',`/transforms/${id}`,{...f.payload(),revision:0,status:'DONE'}).code,0)
 const fd=new FormData();fd.append('projectId','101');fd.append('file',new File(['隔离验收佐证'],'proof.txt'))
 const file=f.call('POST','/files/transform/upload',fd).data
 const r=f.call('PUT',`/transforms/${id}`,{...f.payload(),revision:0,status:'DONE',actualDate:'2026-01-01',evidenceJson:JSON.stringify([file])})
 assert.equal(r.code,0,r.msg);assert.notEqual(f.detail(id).colorStatus,'GREEN');assert.equal(f.db.transforms[0].status,'NOT_STARTED');assert.equal(f.db.transforms[0].actualDate,undefined);f.act(id,'SUBMIT');f.user('unitHead');f.act(id,'APPROVE');f.user('hqHead');f.act(id,'RECORD');assert.equal(f.db.transforms[0].status,'DONE');assert.equal(f.db.transforms[0].actualDate,'2026-01-01');assert.equal(f.detail(id).colorStatus,'GREEN')
})
test('只读或不相关人员不能访问或更新成果',()=>{
 const f=fixture(),id=f.create();f.user('leader');assert.notEqual(f.call('PUT',`/transforms/${id}`,{...f.payload(),revision:0}).code,0)
 f.user('outsider');assert.equal(f.call('GET',`/transforms/${id}`).code,403);assert.equal(f.call('GET','/transforms').data.total,0)
})
test('非法方式形式组合与超长简介被拒绝',()=>{
 for(const patch of [{transformWay:'MARKET',transformForm:'INSTALLED'},{intro:'字'.repeat(101)}]){const f=fixture();assert.notEqual(f.call('POST','/transforms',{...f.payload(),...patch}).code,0)}
})
function pageFixture() {
 const writes=[],notices=[]
 const dicts={TRANSFORM_FORM:Object.entries(helpers.FORM_PARENT).map(([dictCode,parentCode])=>({dictCode,parentCode,dictName:dictCode}))}
 const api={projectApi:{page:async()=>({data:{records:[{id:101,name:'隔离项目'}],total:1}})},transformApi:{page:async()=>({data:{records:[],total:0}}),create:async data=>{writes.push(data);return {data:1}},detail:async()=>({data:{id:1,projectId:101,allowedActions:['fill']}})},deliverableApi:{list:async()=>({data:[]})},fileApi:{}}
 const workflow={permissions:async()=>({data:{allowedActions:['fill']}})}
 const deps={'@/api/modules':api,'@/api/transform':{transformWorkflowApi:workflow},'vue-router':{useRoute:()=>({query:{}}),useRouter:()=>({push:()=>{}})},'ant-design-vue':{message:Object.fromEntries(['success','error','warning'].map(k=>[k,msg=>notices.push(msg)]))},'@ant-design/icons-vue':{},'@/stores/dict':{useDictStore:()=>({dicts,load:async()=>{},options:()=>[],label:(_,v)=>v})},'@/composables/useWorkDuty':{useWorkDuty:()=>({can:vue.ref({fill:true})})},'@/utils/color':loader()('utils/color.ts'),'@/utils/authFile':{downloadAuthenticatedFile:async()=>{}},'@/utils/transformPackage':helpers}
 return {...mountSource('views/transform/Transform.vue',deps),api,workflow,writes,notices}
}
test('台账加载超过200条且筛选重置到第一页',async()=>{
 const f=pageFixture();try{await flush();f.api.transformApi.page=async({page,size})=>({data:{total:201,records:Array.from({length:page===1?200:1},(_,i)=>({id:(page-1)*size+i+1,name:'成果',status:'NOT_STARTED'}))}})
 await f.state.load();assert.equal(f.state.rows.length,201);f.state.query.page=21;f.state.query.keyword='不存在';await flush();assert.equal(f.state.query.page,1);assert.equal(f.state.filtered.length,0)}finally{f.unmount()}
})
test('未选项目时不借用其他项目权限，必填校验阻止空保存',async()=>{
 const f=pageFixture();try{await flush();f.state.newPackage();assert.equal(f.state.selectedProject,null);await f.state.save();assert.equal(f.writes.length,0)}finally{f.unmount()}
})
test('保存失败保留填写内容并释放提交锁',async()=>{
 const f=pageFixture();try{await flush();f.state.newPackage();Object.assign(f.state.form,{allowedActions:['fill'],name:'未丢失',projectId:101,deliverableIds:[1],planDate:'2030-01-01',dutyOrg:'单位',introDetail:'转化任务'})
 f.api.transformApi.create=async()=>{throw Error('模拟失败')};await f.state.save();assert.equal(f.state.form.name,'未丢失');assert.equal(f.state.mode,'edit');assert.equal(f.state.drawerOpen,true);assert.equal(f.state.saving,false)}finally{f.unmount()}
})
test('快速切换详情时旧请求不覆盖最新成果',async()=>{
 const f=pageFixture();try{await flush();const held=deferred();f.api.transformApi.detail=id=>id===1?held.promise:Promise.resolve({data:{id:2,name:'成果二',projectId:101}})
 const first=f.state.openDetail({id:1,projectId:101});await f.state.openDetail({id:2,projectId:101});held.resolve({data:{id:1,name:'成果一',projectId:101}});await first;assert.equal(f.state.form.id,2)}finally{f.unmount()}
})

test('备案成果全部完成即完成转化阶段，无项目归档前置',()=>{
 const build=loader()('utils/transformFlow.ts').buildTransformFlowOptsFromOverview
 const flow=build({project:{status:'IMPLEMENTING'},transforms:[{status:'DONE',workflowStatus:'RECORDED'}]})
 assert.equal(flow.panelStatus,'DONE');assert.equal(flow.nodes.some(n=>n.nodeCode==='TF_ARCHIVE'),false)
 assert.doesNotMatch(JSON.stringify(flow),/归档/)
})
test('未选项目或服务端拒绝填报时不能创建或上传',async()=>{
 const f=pageFixture();try{await flush();f.state.newPackage();assert.equal(f.state.writable,false)
 Object.assign(f.state.form,{projectId:101,allowedActions:[]});assert.equal(f.state.writable,false)
 }finally{f.unmount()}
})
test('重新更新的备案完成结果仍计入台账，未备案声明不计完成',async()=>{
 const f=pageFixture();try{await flush();f.state.rows=[{id:1,status:'NEGOTIATING',confirmedStatus:'DONE',confirmedActualDate:'2025-01-01',workflowStatus:'DRAFT'},{id:2,status:'DONE',confirmedStatus:'SIGNED',workflowStatus:'UNIT_REVIEW'}]
 assert.equal(f.state.stats.done,1);assert.equal(f.state.color(f.state.rows[0]),'GREEN');assert.equal(f.state.deadline(f.state.rows[0]),'实际 2025-01-01')
 }finally{f.unmount()}
})
test('管理员与联系人不能填报成果包',()=>{
 for(const code of ['admin','contactLogin']){const f=fixture();f.user(code);assert.notEqual(f.call('POST','/transforms',f.payload()).code,0)}
})

test('Mock 已备案再次更新不清空正式完成日期',()=>{
 const f=fixture();f.db.transforms.push({id:9,projectId:101,achievementNo:'CG9',name:'已备案',workflowStatus:'RECORDED',status:'DONE',actualDate:'2025-01-01',revision:0,historyJson:'[]'})
 assert.equal(f.act(9,'REOPEN').code,0);assert.equal(f.db.transforms[0].status,'DONE');assert.equal(f.db.transforms[0].actualDate,'2025-01-01');assert.equal(f.detail(9).confirmedStatus,'DONE')
})
test('上传仅使用成果私有入口并携带项目，保留后端下载地址',async()=>{
 const f=pageFixture();try{await flush();f.state.newPackage();Object.assign(f.state.form,{projectId:101,allowedActions:['fill']});let calls=0
 f.workflow.upload=async data=>{calls++;assert.equal(data.get('projectId'),'101');return {data:{fileName:'proof.txt',fileUrl:'/api/files/transform/download?fileId=abc',objectKey:'private/abc'}}}
 await f.state.upload({file:new File(['proof'],'proof.txt')});assert.equal(calls,1);assert.equal(f.state.evidence[0].fileUrl,'/api/files/transform/download?fileId=abc')
 f.state.form.allowedActions=[];await f.state.upload({file:new File(['proof'],'proof.txt')});assert.equal(calls,1)
 }finally{f.unmount()}
})
test('创建权限取所选项目服务端结果，旧请求不能覆盖新项目',async()=>{
 const f=pageFixture();try{await flush();f.state.newPackage();const held=deferred();f.workflow.permissions=id=>id===101?held.promise:Promise.resolve({data:{allowedActions:[]}})
 f.state.form.projectId=101;const first=f.state.changeProject();f.state.form.projectId=102;await f.state.changeProject();held.resolve({data:{allowedActions:['fill']}});await first;assert.equal(f.state.writable,false)
 }finally{f.unmount()}
})

test('新完成声明未备案时流程完成度为零',()=>{
 const build=loader()('utils/transformFlow.ts').buildTransformFlowOptsFromOverview
 const flow=build({transforms:[{status:'DONE',confirmedStatus:'SIGNED',workflowStatus:'UNIT_REVIEW'}]})
 assert.equal(flow.doneCount,0);assert.equal(flow.packages[0].status,'SIGNED')
})
test('已备案完成结果重新填报时流程完成度保留',()=>{
 const build=loader()('utils/transformFlow.ts').buildTransformFlowOptsFromOverview
 const flow=build({transforms:[{status:'NEGOTIATING',confirmedStatus:'DONE',workflowStatus:'DRAFT'}]})
 assert.equal(flow.doneCount,1);assert.equal(flow.packages[0].status,'DONE')
})

test('孤立历史成果可只读打开，不请求缺失项目交付物或允许保存上传',async()=>{
 const f=pageFixture();try{await flush();let uploadCalls=0
 f.api.transformApi.detail=async()=>({data:{id:99,name:'历史孤立成果',projectId:999,orphanedProject:true,allowedActions:[],deliverables:[],workflowStatus:'DRAFT',status:'DONE'}})
 f.api.deliverableApi.list=async()=>{throw Error('关联项目不存在')};f.workflow.upload=async()=>{uploadCalls++}
 await f.state.openDetail({id:99,projectId:999});assert.equal(f.state.detailError,'');assert.equal(f.state.form.name,'历史孤立成果');assert.equal(f.state.writable,false)
 await f.state.save();await f.state.upload({file:new File(['x'],'x.txt')});assert.equal(f.writes.length,0);assert.equal(uploadCalls,0)
 }finally{f.unmount()}
})
