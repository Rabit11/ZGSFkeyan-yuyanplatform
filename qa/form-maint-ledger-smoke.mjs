import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

// Compile the actual mock API into memory; this test never touches server data.
const root = resolve(process.argv[2] || fileURLToPath(new URL('../frontend', import.meta.url)))
const require = createRequire(pathToFileURL(resolve(root, 'package.json')))
const { build } = require('esbuild')
const result = await build({
  entryPoints: [resolve(root, 'src/mock/index.ts')],
  bundle: true, platform: 'node', format: 'esm', write: false,
  alias: { '@': resolve(root, 'src') },
})
let uid = '1'
globalThis.localStorage = { getItem: (key) => key === 'rpm_uid' ? uid : null }
const { mockRequest } = await import(`data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString('base64')}`)
const call = (method, path = '', data = {}) => mockRequest({ method, url: `/api/projects${path}`, data })
const create = async (name, dataSource) => {
  const response = await call('POST', '', { name, dataSource })
  assert.equal(response.code, 0)
  return response.data
}
const imported = await create('QA 表单导入同步', 'FORM_MAINT')
const platform = await create('QA 平台项目', 'PLATFORM')
let list = await call('GET', '', { keyword: 'QA 表单导入同步' })
assert.equal(list.data.records[0].id, imported)
assert.equal(list.data.records[0].canDelete, false)
assert.notEqual((await call('DELETE', `/${imported}`)).code, 0)
assert.equal((await call('GET', `/${imported}`)).data.id, imported)
uid = '12'
assert.notEqual((await call('DELETE', `/form-maint/${imported}`)).code, 0)
assert.equal((await call('GET', `/${imported}`)).data.id, imported)
uid = '1'
assert.equal((await call('DELETE', `/form-maint/${imported}`)).code, 0)
list = await call('GET', '', { keyword: 'QA 表单导入同步' })
assert.equal(list.data.records.length, 0)
assert.equal((await call('GET', `/${platform}`)).data.id, platform)
assert.equal((await call('DELETE', `/${platform}`)).code, 0)
assert.notEqual((await call('DELETE', `/form-maint/${imported}`)).code, 0)
console.log('PASS: imported projects visible, ledger deletion blocked, form deletion synchronized, admin enforced, platform deletion preserved')
