import assert from 'node:assert/strict'
import {test} from 'node:test'
import {readFileSync} from 'node:fs'
import {transform,build} from '../frontend/node_modules/esbuild/lib/main.js'
const vue=readFileSync('frontend/src/views/initiation/Declaration.vue','utf8')
const fn=vue.slice(vue.indexOf('async function onConfirmSubmit()'),vue.indexOf('async function openMaterials('))
const js=(await transform(fn,{loader:'ts'})).code
function fixture({refreshFails=false,submitFails=false}={}){
 const messages=[],calls=[],submitting={value:false},open={value:true},editing={value:{id:12}},Modal={confirm(o){this.options=o},warning(){}};
 const message=Object.fromEntries(['loading','success','error','warning'].map(k=>[k,v=>messages.push([k,v])]))
 const scope={submitting,open,editing,Modal,message,guard:()=>true,collectSubmitIssues:()=>[],persistDraft:async()=>{calls.push('save');return 12},declarationApi:{submit:async()=>{calls.push('submit');if(submitFails)throw Error('材料不全')}},firstDeclarationAuditNode:()=> '项目负责人',form:{},selectedChannel:{value:{}},load:async()=>{calls.push('load');if(refreshFails)throw Error('刷新故障')},submitErrorText:e=>'提交失败：'+e.message}
 const run=new Function(...Object.keys(scope),js+';return onConfirmSubmit;')(...Object.values(scope))
 return {run,Modal,messages,calls,open,editing,submitting}
}
test('提交成功而刷新失败仍完成确认，不保留可重复提交的编辑对象',async()=>{const f=fixture({refreshFails:true});await f.run();await assert.doesNotReject(f.Modal.options.onOk());assert.deepEqual(f.calls,['save','submit','load']);assert.equal(f.open.value,false);assert.equal(f.editing.value,null);assert.equal(f.submitting.value,false);assert.equal(f.messages.filter(([k])=>k==='error').length,0);assert.equal(f.messages.filter(([k])=>k==='warning').length,1)})
test('提交本身失败保留表单并提示真实失败',async()=>{const f=fixture({submitFails:true});await f.run();await assert.rejects(f.Modal.options.onOk(),/材料不全/);assert.equal(f.open.value,true);assert.equal(f.editing.value.id,12);assert.deepEqual(f.calls,['save','submit']);assert.equal(f.submitting.value,false)})
const bundle=await build({entryPoints:['frontend/src/api/modules.ts'],bundle:true,format:'esm',platform:'node',write:false,plugins:[{name:'mock',setup(b){b.onResolve({filter:/\.\/request$/},()=>({path:'http',namespace:'mock'}));b.onLoad({filter:/.*/,namespace:'mock'},()=>({contents:`export default {get:(path)=>path}`}))}}]})
const {declarationApi}=await import(`data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString('base64')}`)
test('申报待审接口已导出且路由正确',()=>{assert.equal(typeof declarationApi.pending,'function');assert.equal(declarationApi.pending(),'/api/declarations/pending')})
