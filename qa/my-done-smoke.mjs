#!/usr/bin/env node
/** 验证“我的已办”只返回当前用户成功完成的审批记录。 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

if (process.env.RPM_INSECURE_TLS === '1') process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'
const BASE = String(process.env.RPM_BASE_URL || 'https://u476948-r3ys-6923790a.westd.seetacloud.com:8443').replace(/\/$/, '')
const USERNAME = process.env.RPM_DONE_USER || '100012'
const PASSWORD = process.env.RPM_DONE_PASSWORD || USERNAME
const root = path.dirname(fileURLToPath(import.meta.url))
const outDir = path.join(root, 'output')
fs.mkdirSync(outDir, { recursive: true })
const lines = []
let pass = 0
let fail = 0

function log(level, message) {
  const line = `${new Date().toISOString()} [${level}] ${message}`
  lines.push(line)
  console.log(line)
}

async function call(method, url, token, body) {
  const headers = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch(`${BASE}${url}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  let json = null
  try { json = text ? JSON.parse(text) : null } catch { /* asserted below */ }
  return { status: response.status, json, text }
}

function check(ok, message, detail = '') {
  if (ok) {
    pass++
    log('PASS', `${message}${detail ? ` | ${detail}` : ''}`)
  } else {
    fail++
    log('FAIL', `${message}${detail ? ` | ${detail}` : ''}`)
  }
}

try {
  const login = await call('POST', '/api/auth/login', null, { username: USERNAME, password: PASSWORD })
  const user = login.json?.data?.user || login.json?.data || {}
  const token = login.json?.data?.token || login.json?.data?.accessToken
  check(Boolean(token), '测试账号登录成功', login.json?.msg || '')
  if (token) {
    const result = await call('GET', '/api/audit-logs/mine/done?page=1&size=100', token)
    check(result.status === 200 && Number(result.json?.code) === 0, '我的已办接口可用', `HTTP=${result.status}; code=${result.json?.code}; msg=${result.json?.msg || result.text}`)
    if (Number(result.json?.code) === 0) {
      const rows = result.json?.data?.records || []
      const currentUserId = Number(user.userId ?? user.id)
      check(Number.isFinite(currentUserId) && currentUserId > 0, '登录接口包含有效用户编号')
      check(rows.every((row) => Number(row.userId) === currentUserId), '只返回当前用户的记录', `rows=${rows.length}`)
      check(rows.every((row) => row.action === 'APPROVE'), '只返回审批操作', `actions=${[...new Set(rows.map((row) => row.action))].join(',')}`)
      check(rows.every((row) => !String(row.content || '').startsWith('失败：')), '排除失败的审批尝试')
      check(rows.every((row) => row.bizId && row.module && row.createdAt), '记录包含业务定位信息')
    }
  }
} catch (error) {
  fail++
  log('FAIL', String(error?.stack || error))
}

log('SUMMARY', `PASS=${pass}; FAIL=${fail}`)
const output = path.join(outDir, 'my-done-test-latest.log')
fs.writeFileSync(output, `${lines.join('\n')}\n`, 'utf8')
console.log(`LOG_FILE=${output}`)
process.exitCode = fail ? 1 : 0
