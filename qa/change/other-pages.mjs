import assert from 'node:assert/strict'
import fs from 'node:fs'
import {base,root} from './helpers.mjs'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE||'/home/dev/.cache/aeroduct-browser/node_modules/playwright/index.mjs')
const browser=await chromium.launch({headless:true,args:['--no-sandbox']})
const page=await browser.newPage({viewport:{width:1440,height:1000}}), errors=[],failures=[],routes=[]
page.on('pageerror',e=>errors.push(e.message))
page.on('response',async response=>{if(!response.url().includes('/api/'))return;try{const b=await response.json();if(response.status()>=400||Number(b.code||0)!==0)failures.push({url:response.url(),status:response.status(),code:b.code,msg:b.msg})}catch{}})
await page.addLocatorHandler(page.getByRole('button',{name:'稍后处理'}),async button=>{await button.click()})
try {
 await page.goto(base+'/#/login');await page.getByPlaceholder('请输入工号').fill('100001');await page.getByPlaceholder('请输入密码').fill('100001');await page.getByRole('button',{name:/^登\s*录$/}).click();await page.waitForURL('**/#/dashboard');await page.waitForLoadState('networkidle')
 for(const route of ['/overview/ledger','/overview/board','/initiation/declaration','/initiation/filing','/implement/milestone','/implement/fund','/acceptance/accept','/transform','/post-eval','/system/user','/system/role-matrix']) {
  await page.goto(base+'/?change-regression='+encodeURIComponent(route)+'#'+route);await page.waitForLoadState('networkidle');
  assert.equal(new URL(page.url()).hash.split('?')[0],'#'+route);assert.ok((await page.locator('body').innerText()).length>100)
  routes.push({route,actualHash:new URL(page.url()).hash,pass:true});console.log('PASS',route)
 }
 assert.deepEqual(errors,[]);assert.deepEqual(failures,[])
}finally{fs.writeFileSync(root+'deploy/change/other-pages-results.json',JSON.stringify({date:new Date().toISOString(),routes,errors,failures},null,2));await browser.close()}
