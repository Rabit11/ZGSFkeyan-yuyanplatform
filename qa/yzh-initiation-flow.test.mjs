import assert from 'node:assert/strict'
import {test} from 'node:test'
import {build} from '../frontend/node_modules/esbuild/lib/main.js'
import path from 'node:path'
const bundle=await build({stdin:{contents:`export * from './frontend/src/utils/declarationWorkflow.ts'`,resolveDir:process.cwd()},alias:{'@':path.resolve('frontend/src')},bundle:true,format:'esm',platform:'node',write:false})
const flow=await import(`data:text/javascript;base64,${Buffer.from(bundle.outputFiles[0].text).toString('base64')}`)
test('yzh 新申报固定八级审批，负责人首审',()=>{
 const nodes=flow.declarationAuditNodes({channelCode:'KJZ',posts:{__workflow:'yzh-v1'}})
 assert.deepEqual(nodes.map(n=>n.title),['项目负责人','项目承担部门负责人','二级总师','单位财务部门负责人','单位科技部门负责人','单位分管领导','一级总师','总部科研项目处'])
 assert.deepEqual(nodes.at(-1).roleKeys,['hqDirector','hqSupervisor'])
})
test('yzh 直接报备仍经过负责人',()=>assert.deepEqual(flow.declarationAuditNodes({posts:{__workflow:'yzh-report-v1'}}).map(n=>n.title),['项目负责人']))
test('历史科技周流程不被覆盖',()=>assert.equal(flow.declarationAuditNodes({channelCode:'KJZ',posts:{__workflow:'week-v1'}})[0].title,'三级专业总师'))
