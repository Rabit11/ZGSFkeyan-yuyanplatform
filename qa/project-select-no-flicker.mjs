#!/usr/bin/env node
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import ts from '../frontend/node_modules/typescript/lib/typescript.js'

const repoRoot = path.resolve(import.meta.dirname, '..')
const policyFile = path.join(repoRoot, 'frontend/src/components/projectSelectPolicy.ts')
const source = fs.readFileSync(policyFile, 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText

const module = { exports: {} }
vm.runInNewContext(compiled, { module, exports: module.exports }, { filename: policyFile })
const { shouldNotifyProjectChange } = module.exports

assert.equal(
  shouldNotifyProjectChange({ source: 'sync', modelValue: 160, optionId: 160 }),
  false,
  '父组件同步已有项目时不得再次通知 change，否则经费页会重复进入骨架屏',
)
assert.equal(
  shouldNotifyProjectChange({ source: 'default', modelValue: undefined, optionId: 160 }),
  true,
  '没有已选项目时，组件仍需通知父页面加载默认项目',
)
assert.equal(
  shouldNotifyProjectChange({ source: 'user', modelValue: 160, optionId: 161 }),
  true,
  '用户主动切换项目时必须通知父页面重新加载',
)

console.log('PASS: 项目选择器不会因同步已有项目而重复触发页面加载')
