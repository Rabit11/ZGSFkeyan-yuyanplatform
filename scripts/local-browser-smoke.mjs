// Optional browser check. Set PLAYWRIGHT_MODULE if Playwright is installed elsewhere.
import assert from 'node:assert/strict'
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || '/home/dev/.cache/aeroduct-browser/node_modules/playwright/index.mjs')
const output = fileURLToPath(new URL('../deploy/logs/', import.meta.url))
const base = 'http://127.0.0.1:6006'
const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
const errors = [], failed = [], businessErrors = [], routes = []
page.on('pageerror', e => errors.push(e.message))
page.on('response', async response => {
  if (!response.url().includes('/api/')) return
  if (response.status() >= 400) failed.push({ url: response.url(), status: response.status() })
  else try {
    const body = await response.json()
    if (body.code !== undefined && body.code !== 0) businessErrors.push({ url: response.url(), code: body.code, msg: body.msg })
  } catch { /* binary downloads */ }
})
try {
  await page.goto(base + '/#/login')
  await page.getByRole('button', { name: /^登\s*录$/ }).click()
  await page.waitForURL('**/#/dashboard', { timeout: 30000 })
  await page.waitForLoadState('networkidle')
  await page.screenshot({ path: output + 'browser-dashboard.png', fullPage: true })
  for (const route of ['/overview/ledger', '/overview/board', '/initiation/declaration', '/initiation/filing',
    '/implement/milestone', '/implement/fund', '/acceptance/accept', '/transform', '/post-eval', '/system/user', '/system/role-matrix']) {
    await page.goto(base + '/?qa=' + encodeURIComponent(route) + '#' + route)
    await page.waitForLoadState('networkidle')
    assert.equal(new URL(page.url()).hash, '#' + route)
    const bodyLength = (await page.locator('body').innerText()).length
    assert.ok(bodyLength > 100)
    routes.push({ route, bodyLength })
    console.log('PASS render:', route)
  }
  await page.goto(base + '/?qa=final#/overview/ledger')
  await page.waitForLoadState('networkidle')
  assert.ok((await page.locator('body').innerText()).includes('XM2026S001'))
  await page.screenshot({ path: output + 'browser-ledger.png', fullPage: true })
  assert.deepEqual(errors, [], 'browser runtime errors')
  assert.deepEqual(failed, [], 'HTTP failures')
  assert.deepEqual(businessErrors, [], 'business API failures')
} finally {
  fs.writeFileSync(output + 'browser-smoke.json', JSON.stringify({ errors, failed, businessErrors, routes }, null, 2))
  await browser.close()
}
