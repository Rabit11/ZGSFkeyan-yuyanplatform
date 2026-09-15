"""路由爬取 v2：从侧边栏 data-menu-id 收集全部路由后直接 goto，记录耗时/接口失败/控制台错误/空白页；再检查页面内 hash 链接。"""
import asyncio, sys, json, time
from playwright.async_api import async_playwright
OUT = sys.argv[1]
ACCOUNTS = ['100012', '100005', '100001', '100004', '100016']
report = {'pages': [], 'slow_api': [], 'failed_api': [], 'console_errors': [], 'broken_links': []}


async def login(page, u):
    await page.goto('http://localhost:6006/#/login'); await page.wait_for_timeout(800)
    ins = page.locator('input'); await ins.nth(0).fill(u); await ins.nth(1).fill(u)
    t = time.time()
    await page.keyboard.press('Enter')
    try:
        await page.wait_for_url('**/#/dashboard**', timeout=20000)
    except Exception:
        pass
    await page.wait_for_timeout(1200)
    for name in ['稍后处理']:
        try:
            btn = page.get_by_role('button', name=name)
            if await btn.count():
                await btn.first.click(); await page.wait_for_timeout(300)
        except Exception:
            pass
    return round(time.time() - t, 2)


async def visit(page, acc, route, cur, label=''):
    cur['route'] = route
    t0 = time.time()
    await page.goto('http://localhost:6006/#' + route)
    try:
        await page.wait_for_load_state('networkidle', timeout=10000)
    except Exception:
        pass
    # wait until no spinner (max 8s)
    for _ in range(16):
        if await page.locator('.ant-spin-spinning').count() == 0:
            break
        await page.wait_for_timeout(500)
    await page.wait_for_timeout(300)
    sec = round(time.time() - t0, 2)
    cont = page.locator('.page-container, .ant-layout-content')
    body = (await cont.first.inner_text()) if await cont.count() else ''
    spinning = await page.locator('.ant-spin-spinning').count()
    empties = await page.locator('.ant-empty').count()
    rec = {'acc': acc, 'menu': label, 'route': route, 'sec': sec, 'blank': len(body.strip()) < 20,
           'spinning_after_wait': spinning, 'empty_blocks': empties, 'chars': len(body), 'url_after': page.url.split('#')[-1]}
    report['pages'].append(rec)
    return body


async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch()
        for acc in ACCOUNTS:
            ctx = await b.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN')
            page = await ctx.new_page()
            cur = {'route': 'login'}
            page.on('console', lambda m, acc=acc: report['console_errors'].append({'acc': acc, 'route': cur['route'], 'text': m.text[:300]}) if m.type == 'error' else None)
            page.on('pageerror', lambda e, acc=acc: report['console_errors'].append({'acc': acc, 'route': cur['route'], 'text': 'PAGEERROR ' + str(e)[:300]}))

            def on_response(r, acc=acc):
                if '/api/' not in r.url:
                    return
                try:
                    tm = r.request.timing
                    dur = tm.get('responseEnd', 0) - tm.get('requestStart', 0) if tm else 0
                except Exception:
                    dur = 0
                if r.status >= 400:
                    report['failed_api'].append({'acc': acc, 'route': cur['route'], 'status': r.status, 'url': r.url.replace('http://localhost:6006', '')})
                if dur > 1500:
                    report['slow_api'].append({'acc': acc, 'route': cur['route'], 'ms': round(dur), 'url': r.url.replace('http://localhost:6006', '')})
            page.on('response', on_response)
            page.on('requestfailed', lambda r, acc=acc: report['failed_api'].append({'acc': acc, 'route': cur['route'], 'status': 'NETFAIL', 'url': r.url[:200]}) if '/api/' in r.url else None)
            lt = await login(page, acc)
            report['pages'].append({'acc': acc, 'route': 'login->dashboard', 'sec': lt})
            # expand all submenus and collect data-menu-id routes
            for _ in range(3):
                titles = page.locator('.ant-menu-submenu-title')
                for i in range(await titles.count()):
                    try:
                        t = titles.nth(i)
                        cls = await t.locator('xpath=..').get_attribute('class') or ''
                        if 'ant-menu-submenu-open' not in cls:
                            await t.click(); await page.wait_for_timeout(150)
                    except Exception:
                        pass
            items = await page.eval_on_selector_all('.ant-menu-item', 'els => els.map(e => ({id: e.getAttribute("data-menu-id") || "", text: e.innerText.trim()}))')
            routes = []
            seen = set()
            for it in items:
                rid = it['id']
                # data-menu-id 形如 "/implement/milestone" 或 "rc-menu-uuid-xxx-/implement/milestone"
                if '/' in rid:
                    rid = rid[rid.index('/'):]
                if rid and rid not in seen:
                    seen.add(rid); routes.append((rid, it['text']))
            hrefs = set()
            for route, label in routes:
                try:
                    await visit(page, acc, route, cur, label)
                    links = await page.eval_on_selector_all('a[href^="#/"]', 'els => [...new Set(els.map(e=>e.getAttribute("href")))]')
                    for h in links[:30]:
                        hrefs.add(h[1:])
                except Exception as e:
                    report['pages'].append({'acc': acc, 'menu': label, 'route': route, 'error': str(e)[:200]})
            for h in sorted(hrefs - seen):
                try:
                    body = await visit(page, acc, h, cur, '(link)')
                    if len(body.strip()) < 20 or '404' in body[:200]:
                        report['broken_links'].append({'acc': acc, 'href': h, 'chars': len(body)})
                except Exception as e:
                    report['broken_links'].append({'acc': acc, 'href': h, 'error': str(e)[:120]})
            await ctx.close()
        await b.close()
    json.dump(report, open(f'{OUT}/crawl2-report.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print('pages', len(report['pages']), 'slow', len(report['slow_api']), 'failed', len(report['failed_api']), 'console', len(report['console_errors']), 'broken', len(report['broken_links']))

asyncio.run(main())
