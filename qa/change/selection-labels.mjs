import {selectOneObject} from './select-objects.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {fixture,clean,root} from './helpers.mjs';
if(process.env.CHANGE_QA_DB!=='rpm_change_lab')throw Error('Lab only');
const {chromium}=await import('/home/dev/.cache/aeroduct-browser/node_modules/playwright/index.mjs');
const results=[],errors=[];const f=fixture('04ZXJX','【选择标签】');let browser;
try {
 browser=await chromium.launch({headless:true,args:['--no-sandbox']});const page=await browser.newPage({viewport:{width:1440,height:1000}});page.on('pageerror',e=>errors.push(e.message));
 await page.addLocatorHandler(page.getByRole('button',{name:'稍后处理'}),async b=>b.click());
 await page.goto('http://127.0.0.1:6006/#/login');await page.getByPlaceholder('请输入工号').fill('100012');await page.getByPlaceholder('请输入密码').fill('100012');await page.getByRole('button',{name:/^登\s*录$/}).click();await page.waitForURL('**#/dashboard');
 await page.goto('http://127.0.0.1:6006/#/implement/change');await page.getByRole('button',{name:'进入测试模式',exact:true}).click();await page.getByTestId('change-lab').waitFor();
 await page.getByRole('button',{name:'使用场景与操作脚本',exact:true}).click();await page.getByRole('navigation',{name:'演练场景'}).getByRole('button').filter({hasText:'S09'}).click();await page.getByRole('button',{name:'开始此场景',exact:true}).click();
 await page.waitForFunction(()=>document.querySelector('[aria-label="变更标题"]')?.value==='S09 多项变更 · 多轮修订与周转');await page.getByRole('button',{name:/版本与履历/}).waitFor();
 assert.equal(await page.getByLabel('第1项调整后内容',{exact:true}).inputValue(),'第一版联合调整指标');assert.equal(await page.locator('.selected-items .selected-item').count(),2);assert.equal(await page.locator('.selection-summary .ant-tag').count(),2);results.push({name:'U01 S09操作脚本打开真实申请并提供版本导航',pass:true});
 await page.locator('.ant-drawer-content').getByRole('button',{name:/^关\s*闭$/}).click();await page.getByRole('button',{name:'放弃修改',exact:true}).click();await page.locator('.ant-drawer-content').waitFor({state:'hidden'});
 await page.getByRole('button',{name:/发起变更$/}).click();
 async function select(label,text){if(label==='变更对象')return selectOneObject(page,text);const c=page.getByLabel(label,{exact:true});await c.click();await c.locator('input').fill(text);await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({hasText:text}).first().click();}
 await select('关联项目',f.key);await page.getByText('数据变更 · 纠正登记',{exact:true}).click();await select('变更对象','项目层级 / 渠道特殊调整');
 await page.locator('.ant-select[aria-label="第1项调整后渠道"]').click();const option=page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({hasText:'上海'}).first();const label=await option.innerText();await option.click();
 assert.equal(await page.locator('.ant-select[aria-label="第1项调整后渠道"]').locator('.ant-select-selection-item').innerText(),label.trim());await page.locator('.selected-items').scrollIntoViewIfNeeded();await page.screenshot({path:root+'deploy/change-batch/05-channel-selection.png',fullPage:true});
 assert.deepEqual(errors,[]);results.push({name:'U02 渠道已选清单在保存前显示名称而非内部编号',pass:true});
} catch(e){results.push({name:'failure',pass:false,error:e.stack});throw e;} finally {if(browser)await browser.close();clean([f]);fs.writeFileSync(root+'deploy/change-batch/selection-labels.json',JSON.stringify({date:new Date().toISOString(),results,errors},null,2));}
console.log('PASS',results.length,'selection label checks');
