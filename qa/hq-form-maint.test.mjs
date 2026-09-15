import assert from 'node:assert/strict'
import {test} from 'node:test'
import {build} from '../frontend/node_modules/esbuild/lib/main.js'
const b=await build({entryPoints:['frontend/src/constants/permission.ts'],bundle:true,format:'esm',platform:'node',write:false})
const p=await import(`data:text/javascript;base64,${Buffer.from(b.outputFiles[0].text).toString('base64')}`)
test('表单维护仅对管理员和两类总部管理人员开放',()=>{
 assert.equal(typeof p.canMaintainImportedProjects,'function')
 for(const identityCode of ['admin','hqHead','hqStaff'])assert.equal(p.canMaintainImportedProjects({identityCode}),true)
 for(const identityCode of ['leader','unitHead','unitStaff','finHq','owner','contactLogin',''])assert.equal(p.canMaintainImportedProjects({identityCode,roles:['MANAGEMENT']}),false)
 assert.equal(p.canMaintainImportedProjects({roles:['ADMIN']}),true)
})
