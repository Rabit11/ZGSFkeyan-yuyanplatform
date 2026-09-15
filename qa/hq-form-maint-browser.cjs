const fs=require('fs'),http=require('http'),path=require('path'),assert=require('assert/strict');
const {chromium}=require('C:/Users/81172/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root=path.resolve('frontend/dist');
const server=http.createServer((q,s)=>{if(q.url.startsWith('/api/')){const u=http.request({host:'127.0.0.1',port:18083,path:q.url,method:q.method,headers:q.headers},r=>{s.writeHead(r.statusCode,r.headers);r.pipe(s)});u.on('error',()=>s.writeHead(502).end());q.pipe(u);return;}let f=path.join(root,decodeURIComponent(q.url.split('?')[0]));if(!fs.existsSync(f)||fs.statSync(f).isDirectory())f=path.join(root,'index.html');s.setHeader('content-type',f.endsWith('.js')?'text/javascript':f.endsWith('.css')?'text/css':'text/html');fs.createReadStream(f).pipe(s)});
(async()=>{await new Promise(r=>server.listen(18084,'127.0.0.1',r));const browser=await chromium.launch({headless:true});try{
 for(const no of ['100003','100004','100012','100002']) {
 const context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:18084/#/login');await page.getByPlaceholder('请输入工号').fill(no);await page.getByPlaceholder('请输入密码').fill(no);await page.getByRole('button',{name:/^登\s*录$/}).click();await page.waitForURL('**/#/dashboard');
 await page.goto('http://127.0.0.1:18084/#/system/form-maint');
 if(['100003','100004'].includes(no)) {
 await page.locator('button').filter({hasText:'上传总表（合并）'}).waitFor();assert.equal(await page.getByRole('button',{name:'清空全部项目',exact:true}).count(),0);
 const dl=page.waitForEvent('download');await page.getByRole('button',{name:/导出 Excel/}).click();const download=await dl;const out=path.join(process.env.TEMP,'hq-export-'+no+'.xlsx');await download.saveAs(out);
 await page.locator('input[type=file]').first().setInputFiles(out);await page.getByRole('button',{name:'合并入库',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'全量替换入库',exact:true}).count(),0);assert.equal(await page.getByText('无权覆盖总表，请改用合并导入（上传分表）',{exact:true}).count(),0);
 console.log('BROWSER_PASS HQ '+no+' route, table export, real Excel import preview, no replacement');
 } else {await page.waitForURL('**/#/dashboard');console.log('BROWSER_PASS restricted import route '+no)}
 assert.deepEqual(errors,[]);await context.close();
 }
}finally{await browser.close();server.closeAllConnections();server.close()}})().catch(e=>{console.error(e);server.closeAllConnections();server.close();process.exit(1)});


