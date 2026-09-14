"""各角色视角页面检查（Playwright）：16 个账号分别打开实施阶段各页与台账，记录页面是否正常、可见的写操作按钮是否与需求一致。
用法：PYTHONIOENCODING=utf-8 python qa/impl-stage-role-ui-check.py <输出目录>
"""
import asyncio
import json
import sys
from playwright.async_api import async_playwright

OUT = sys.argv[1] if len(sys.argv) > 1 else '.'
BASE = 'http://localhost:6006'
ROSTER = [('100001', 'admin'), ('100002', 'leader'), ('100003', 'hqHead'), ('100004', 'hqStaff'), ('100005', 'unitHead'),
          ('100006', 'unitStaff'), ('100007', 'chief1'), ('100008', 'chief2'), ('100009', 'finHq'), ('100010', 'finHead'),
          ('100011', 'finStaff'), ('100012', 'owner'), ('100013', 'contactLogin'), ('100014', 'techLead'), ('100015', 'projectPm'), ('100016', 'deptHead')]
TEAM = {'owner', 'contactLogin', 'techLead', 'projectPm'}
PAGES = [('basic', '#/implement/basic?projectId=2'), ('milestone', '#/implement/milestone'), ('review', '#/implement/review'),
         ('change', '#/implement/change?projectId=2'), ('ledger', '#/overview/ledger'), ('detail', '#/overview/detail/2')]
WRITE_BUTTONS = ['保存草稿', '提交审批', '保存至台账', '新增节点', '编制里程碑节点', '提交清单审核', '发起变更', '添加项目']
report = []


async def main():
    async with async_playwright() as pw:
        b = await pw.chromium.launch()
        for no, ident in ROSTER:
            ctx = await b.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN')
            pg = await ctx.new_page()
            errs = []
            pg.on('pageerror', lambda e: errs.append(str(e)[:120]))
            pg.on('console', lambda m: errs.append(m.text[:120]) if m.type == 'error' else None)
            await pg.goto(BASE + '/#/login'); await pg.wait_for_timeout(700)
            ins = pg.locator('input'); await ins.nth(0).fill(no); await ins.nth(1).fill(no); await pg.keyboard.press('Enter')
            try:
                await pg.wait_for_url('**/#/dashboard**', timeout=15000)
            except Exception:
                report.append({'acc': no, 'ident': ident, 'page': 'login', 'ok': False, 'detail': 'login did not reach dashboard'})
                await ctx.close(); continue
            await pg.wait_for_timeout(800)
            btn = pg.get_by_role('button', name='稍后处理')
            if await btn.count():
                await btn.first.click()
            for key, route in PAGES:
                await pg.goto(BASE + '/' + route)
                try:
                    await pg.wait_for_load_state('networkidle', timeout=8000)
                except Exception:
                    pass
                for _ in range(16):
                    if await pg.locator('.ant-spin-spinning').count() == 0:
                        break
                    await pg.wait_for_timeout(500)
                await pg.wait_for_timeout(600)
                cont = pg.locator('.page-container, .ant-layout-content')
                text = (await cont.first.inner_text()) if await cont.count() else ''
                visible = {}
                for label in WRITE_BUTTONS:
                    loc = pg.get_by_role('button', name=label)
                    n = await loc.count()
                    if n:
                        enabled = any([not await loc.nth(i).is_disabled() for i in range(n)])
                        visible[label] = 'enabled' if enabled else 'disabled'
                rec = {'acc': no, 'ident': ident, 'page': key, 'ok': len(text.strip()) > 20 and not errs,
                       'chars': len(text), 'buttons': visible, 'errors': errs[:2], 'url': pg.url.split('#')[-1]}
                # 期望：非项目团队不应看到可用的填报按钮（审批/查看除外）
                fill_enabled = [k for k, v in visible.items() if v == 'enabled' and k in ('保存草稿', '提交审批', '保存至台账', '新增节点', '提交清单审核', '发起变更')]
                if ident not in TEAM and fill_enabled:
                    rec['ok'] = False
                    rec['detail'] = '非项目团队看到可用填报按钮: ' + ','.join(fill_enabled)
                report.append(rec)
                errs.clear()
                await pg.screenshot(path=f'{OUT}/role-{no}-{ident}-{key}.png')
            await ctx.close()
        await b.close()
    json.dump(report, open(f'{OUT}/role-ui-report.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    bad = [r for r in report if not r['ok']]
    for r in report:
        flag = 'OK  ' if r['ok'] else 'FAIL'
        print(f"{flag} {r['acc']} {r['ident']:<12} {r['page']:<9} btns={r.get('buttons')} {r.get('detail', '')} {r.get('errors') or ''}")
    print(f'\n==== {len(report) - len(bad)} ok, {len(bad)} problems ====')


asyncio.run(main())
