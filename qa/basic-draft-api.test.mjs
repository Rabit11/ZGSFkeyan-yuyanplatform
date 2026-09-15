import assert from 'node:assert/strict'
import {test} from 'node:test'
import {build} from '../frontend/node_modules/esbuild/lib/main.js'
const bundle=await build({entryPoints:['frontend/src/api/modules.ts'],bundle:true,format:'esm',platform:'node',write:false,plugins:[{name:'http',setup(b){b.onResolve({filter:/\.\/request$/},()=>({path:'http',namespace:'mock'}));b.onLoad({filter:/.*/,namespace:'mock'},()=>({contents:`export default Object.fromEntries(['get','post','put','del'].map(method=>[method,(...args)=>({method,args})]))`}))}}]})
const {projectApi}=await import(`data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString('base64')}`)
test('基本信息草稿完整接口可调用，使用项目独立的草稿审批接口',()=>{
 const payload={teamMembers:[{employeeNo:'123',roleCode:'PROJECT_CONTACT'}]}
 for(const [name,method,suffix,data] of [['basicDraft','get','',undefined],['saveBasicDraft','put','',payload],['submitBasicDraft','post','/submit',undefined],['auditBasicDraft','post','/audit',{pass:true}]]) {
  assert.equal(typeof projectApi[name],'function',name)
  const result=projectApi[name](41,...(data?[data]:[]))
  assert.equal(result.method,method);assert.equal(result.args[0],`/api/projects/41/basic-draft${suffix}`)
  if(data)assert.deepEqual(result.args[1],data)
 }
})

test('审核人员可读取基本信息待审核任务',()=>assert.deepEqual(projectApi.pendingBasicDrafts(),{method:'get',args:['/api/projects/basic-drafts/pending']}))
