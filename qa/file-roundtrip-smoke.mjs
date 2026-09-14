/** Upload a unique QA attachment, compare downloaded bytes, then remove only that object. */
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
if (process.env.RPM_INSECURE_TLS === '1') process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'
const base = (process.env.RPM_BASE_URL || 'https://u476948-r3ys-6923790a.westd.seetacloud.com:8443').replace(/\/$/, '')
const timeout = () => AbortSignal.timeout(Number(process.env.RPM_TIMEOUT_MS || 30000))
async function login(username) {
  const r = await fetch(base + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: process.env.RPM_TEST_PASSWORD || username }), signal: timeout() })
  const body = await r.json()
  assert.equal(body.code, 0, 'test account login')
  assert.ok(body.data.token)
  return body.data.token
}
const admin = await login(process.env.RPM_ADMIN_USER || '100001')
const finance = await login(process.env.RPM_FINANCE_USER || '100010')
const filename = 'QA_上传下载_' + randomUUID() + '.txt'
const content = 'UTF-8 中文附件往返测试\n' + randomUUID()
let key
try {
  const form = new FormData()
  form.append('file', new Blob([content], { type: 'text/plain;charset=utf-8' }), filename)
  const r = await fetch(base + '/api/files/upload?bizType=qa-regression', { method: 'POST',
    headers: { Authorization: 'Bearer ' + finance }, body: form, signal: timeout() })
  const uploaded = await r.json()
  key = uploaded.data?.objectKey
  assert.equal(uploaded.code, 0, 'finance upload accepted')
  assert.ok(key?.startsWith('qa-regression/'))
  assert.ok(key.endsWith(filename))
  assert.equal(uploaded.data.fileName, filename, 'Chinese filename preserved')
  const downloaded = await fetch(base + '/api/files/download?objectKey=' + encodeURIComponent(key), {
    headers: { Authorization: 'Bearer ' + finance }, signal: timeout() })
  assert.equal(downloaded.status, 200)
  assert.equal(await downloaded.text(), content, 'downloaded bytes match')
  console.log('PASS: 二级单位财务上传、中文文件名、下载内容一致')
} finally {
  if (key) {
    // Never delete a key not created by this execution.
    assert.ok(key.startsWith('qa-regression/') && key.endsWith(filename))
    const r = await fetch(base + '/api/files?objectKey=' + encodeURIComponent(key), {
      method: 'DELETE', headers: { Authorization: 'Bearer ' + admin }, signal: timeout() })
    assert.equal((await r.json()).code, 0, 'QA attachment cleanup')
    console.log('PASS: 本次 QA 附件已清理')
  }
}
