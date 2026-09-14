"""浏览器端全流程走查：真实点击页面完成 新增节点→清单审核→上传佐证→销项两级审核→延期变更三级审批→基本信息四级审批。
每步记录耗时；失败截图并继续。"""
import asyncio, sys, json, time, datetime, os, re
from playwright.async_api import async_playwright
OUT = sys.argv[1]
BASE = 'http://localhost:6006'
STAMP = datetime.datetime.now().strftime('%H%M%S')
NODE = 'UI走查节点-' + STAMP
steps = []
messages = []


def log(name, ok, sec, detail=''):
    steps.append({'step': name, 'ok': ok, 'sec': round(sec, 2), 'detail': str(detail)[:300]})
    print(('PASS' if ok else 'FAIL'), f'{sec:5.2f}s', name, '' if ok else ('<- ' + str(detail)[:200]))


class Flow:
    def __init__(self, browser):
        self.browser = browser
        self.ctx = None
        self.page = None

    async def login(self, u):
        if self.ctx:
            await self.ctx.close()
        self.ctx = await self.browser.new_context(viewport={'width': 1440, 'height': 900}, locale='zh-CN')
        self.page = await self.ctx.new_page()
        self.page.on('console', lambda m: messages.append({'type': 'console', 'text': m.text[:200]}) if m.type == 'error' else None)
        p = self.page
        t = time.time()
        await p.goto(BASE + '/#/login'); await p.wait_for_timeout(800)
        ins = p.locator('input'); await ins.nth(0).fill(u); await ins.nth(1).fill(u)
        await p.keyboard.press('Enter')
        await p.wait_for_url('**/#/dashboard**', timeout=20000)
        await p.wait_for_timeout(1200)
        for name in ['稍后处理']:
            btn = p.get_by_role('button', name=name)
            if await btn.count():
                await btn.first.click(); await p.wait_for_timeout(300)
        log(f'登录 {u}', True, time.time() - t)

    async def goto(self, route, wait=1500):
        t = time.time()
        await self.page.goto(BASE + '/' + route)
        try:
            await self.page.wait_for_load_state('networkidle', timeout=8000)
        except Exception:
            pass
        for _ in range(20):
            if await self.page.locator('.ant-spin-spinning').count() == 0:
                break
            await self.page.wait_for_timeout(500)
        await self.page.wait_for_timeout(wait)
        return time.time() - t

    async def toast(self, timeout=6000):
        """返回最近出现的 antd message 文本"""
        try:
            m = self.page.locator('.ant-message-notice-content')
            await m.last.wait_for(timeout=timeout)
            txt = await m.last.inner_text()
            messages.append({'type': 'toast', 'text': txt})
            return txt
        except Exception:
            return ''

    async def shot(self, name):
        await self.page.screenshot(path=f'{OUT}/e2e-{name}.png')

    async def step(self, name, fn):
        t = time.time()
        try:
            detail = await fn()
            log(name, True, time.time() - t, detail or '')
            return detail
        except Exception as e:
            await self.shot('fail-' + name.replace('/', '_')[:40])
            log(name, False, time.time() - t, e)
            return None


import json as _json, urllib.request as _ur


def _api(method, path, token=None, body=None):
    data = _json.dumps(body).encode() if body is not None else None
    req = _ur.Request(BASE + path, data=data, method=method, headers={'Content-Type': 'application/json', **({'Authorization': 'Bearer ' + token} if token else {})})
    try:
        with _ur.urlopen(req, timeout=20) as r:
            return _json.loads(r.read().decode())
    except Exception as e:
        return {'code': -1, 'msg': str(e)}


def prep_state():
    t = _api('POST', '/api/auth/login', body={'username': '100005', 'password': '100005'}).get('data', {}).get('token')
    r = _api('POST', '/api/milestones/annual-plan/audit?projectId=2&year=2026', t, {'pass': True, 'remark': '走查前清理'})
    print('prep: annual audit ->', r.get('code'), r.get('msg'))


async def main():
    async with async_playwright() as pw:
        browser = await pw.chromium.launch()
        f = Flow(browser)
        p = lambda: f.page
        prep_state()

        # ---------- A. 项目负责人：新增节点、提交清单审核
        await f.login('100012')
        sec = await f.goto('#/implement/milestone')
        log('打开里程碑填报', True, sec)

        async def add_node():
            board = p().locator('.ant-card').filter(has=p().locator('.proj-title', has_text='XM2026S002')).first
            await board.get_by_role('button', name='新增节点').click()
            dlg = p().locator('.ant-modal').filter(has_text='新增节点')
            await dlg.wait_for(timeout=5000)
            await dlg.locator('input').nth(0).fill(NODE)
            date = dlg.locator('input[placeholder], .ant-picker input').last
            await date.click(); await date.fill('2026-10-30'); await p().keyboard.press('Enter')
            budget = dlg.locator('.ant-input-number input').last
            await budget.fill('88')
            await dlg.get_by_role('button', name='确 定').click()
            txt = await f.toast()
            await p().wait_for_timeout(1500)
            assert NODE in await p().content(), '新节点未出现在列表'
            return txt
        await f.step('新增里程碑节点（弹窗）', add_node)
        await f.shot('a1-node-added')

        async def submit_annual():
            board = p().locator('.ant-card').filter(has=p().locator('.proj-title', has_text='XM2026S002')).first
            btn = board.get_by_role('button', name='提交清单审核')
            assert await btn.count(), '未找到"提交清单审核"按钮'
            await btn.click()
            ok = p().locator('.ant-modal-confirm').get_by_role('button', name='确 定')
            if await ok.count():
                await ok.click()
            txt = await f.toast()
            await p().wait_for_timeout(1500)
            return txt
        await f.step('提交年度清单审核', submit_annual)
        await f.shot('a2-annual-submitted')

        # ---------- B. 单位科研管理部门负责人：清单审核
        await f.login('100005')
        sec = await f.goto('#/implement/review'); log('打开待我审核', True, sec)
        await f.shot('b1-review-list')

        async def audit_annual():
            row = p().locator('.ant-table-row').filter(has_text='XM2026S002').filter(has_text='清单').first
            await row.wait_for(timeout=15000)
            await row.get_by_role('button', name=re.compile(r'^审\s*核$')).click()
            await p().wait_for_timeout(2500)
            btn = p().get_by_role('button', name='审核通过并归档')
            assert await btn.count(), '编制页没有"审核通过并归档"按钮'
            await btn.click()
            ok = p().locator('.ant-modal-confirm').get_by_role('button', name='确 定')
            if await ok.count():
                await ok.click()
            txt = await f.toast()
            await p().wait_for_timeout(1500)
            return txt
        await f.step('清单审核通过并归档', audit_annual)
        await f.shot('b2-annual-audited')

        # ---------- E. 延期申请 → 变更三级（用本轮新建节点，基线已固化）
        await f.login('100012')
        await f.goto('#/implement/milestone')

        async def delay():
            row = p().locator('.ant-table-row').filter(has_text=NODE).first
            await row.wait_for(timeout=15000)
            await row.get_by_role('button', name=re.compile(r'^更\s*多$')).click(); await p().wait_for_timeout(600)
            await p().locator('.ant-dropdown:visible .ant-dropdown-menu-item').filter(has_text='延期申请').first.click()
            dlg = p().locator('.ant-modal').filter(has_text='延期申请')
            await dlg.wait_for(timeout=5000)
            date = dlg.locator('.ant-picker input').first
            await date.click(); await date.fill('2026-10-31'); await p().keyboard.press('Enter')
            await dlg.locator('textarea').first.fill('供应商交付延后，需顺延两天（UI 走查）')
            await dlg.get_by_role('button', name='生成延期变更单').click()
            txt = await f.toast()
            await p().wait_for_timeout(2500)
            assert 'implement/change' in p().url, '未跳转变更页 ' + p().url
            return txt + ' | ' + p().url
        await f.step('延期申请生成变更单并跳转', delay)
        await f.shot('e1-change-page')

        async def submit_change():
            row = p().locator('.ant-table-row.row-highlight, .ant-table-row').filter(has_text=NODE).first
            btn = row.get_by_role('button', name=re.compile(r'^提\s*交$')).first
            assert not await btn.is_disabled(), '提交按钮禁用'
            await btn.click()
            ok = p().locator('.ant-modal-confirm').get_by_role('button', name='确 定')
            if await ok.count():
                await ok.click()
            txt = await f.toast()
            await p().wait_for_timeout(1200)
            return txt
        await f.step('提交变更审批', submit_change)

        for acc, label in [('100005', '二级初审'), ('100004', '总部终审')]:
            await f.login(acc)
            await f.goto('#/implement/change?projectId=2')

            async def audit_change():
                await p().locator('.ant-table-row').first.wait_for(timeout=15000)
                rows = p().locator('.ant-table-row').filter(has_text=NODE).filter(has_text='审批中')
                row = rows.first if await rows.count() else p().locator('.ant-table-row').filter(has_text=NODE).first
                btn = row.get_by_role('button', name=re.compile(r'^审\s*批$')).first
                assert await btn.count() and not await btn.is_disabled(), '审批按钮不可用'
                await btn.click()
                dlg = p().locator('.ant-modal').filter(has_text='变更审批')
                await dlg.wait_for(timeout=5000)
                ta = dlg.locator('textarea')
                if await ta.count():
                    await ta.first.fill('同意')
                await dlg.get_by_role('button', name='确 定').click()
                txt = await f.toast()
                await p().wait_for_timeout(1200)
                return txt
            await f.step(f'变更审批（{label}）', audit_change)
            await f.shot(f'e-{acc}-change-audit')

        # ---------- C. 项目负责人：上传佐证、闭环销项
        await f.login('100012')
        await f.goto('#/implement/milestone')

        async def open_upload():
            row = p().locator('.ant-table-row').filter(has_text=NODE).first
            await row.wait_for(timeout=15000)
            await row.get_by_role('button', name=re.compile(r'^更\s*多$')).click()
            await p().wait_for_timeout(600)
            await p().locator('.ant-dropdown:visible .ant-dropdown-menu-item').filter(has_text='上传材料').first.click()
            await p().wait_for_timeout(2500)
            assert 'milestone-close' in p().url, '未跳转到销项页 ' + p().url
            return p().url
        await f.step('更多→上传材料 跳转销项页', open_upload)
        await f.shot('c1-close-page')

        async def upload_and_close():
            card = p().locator('.ant-card, .ms-card, .close-card').filter(has_text=NODE).first
            if not await card.count():
                card = p().locator('body')
            evidence = card.locator('input[placeholder="节点完成佐证名称"]')
            if await evidence.count():
                await evidence.fill('试验报告-' + STAMP)
            fpath = os.path.join(OUT, 'e2e-evidence.txt')
            open(fpath, 'w').write('evidence ' + STAMP)
            fi = card.locator('input[type=file]')
            assert await fi.count(), '销项页没有文件输入'
            await fi.first.set_input_files(fpath)
            txt1 = await f.toast()
            await p().wait_for_timeout(1500)
            btn = card.get_by_role('button').filter(has_text='销项').first
            assert await btn.count(), '没有销项按钮'
            assert not await btn.is_disabled(), '销项按钮仍禁用（材料未登记？）'
            await btn.click()
            ok = p().locator('.ant-modal-confirm').get_by_role('button', name='确 定')
            if await ok.count():
                await ok.click()
            txt2 = await f.toast()
            await p().wait_for_timeout(1500)
            return f'{txt1} | {txt2}'
        await f.step('上传佐证并提交销项', upload_and_close)
        await f.shot('c2-closed')

        # ---------- D. 两级销项审核
        for acc, label in [('100016', '部门负责人'), ('100005', '单位负责人')]:
            await f.login(acc)
            await f.goto('#/implement/review')

            async def audit_close(acc=acc):
                row = p().locator('.ant-table-row').filter(has_text=NODE).first
                await row.wait_for(timeout=15000)
                await row.get_by_role('button', name=re.compile(r'^审\s*核$')).click()
                await p().wait_for_timeout(2500)
                card = p().locator('.ant-card, .ms-card, .close-card').filter(has_text=NODE).first
                btn = card.get_by_role('button', name='审核通过')
                assert await btn.count(), '销项页没有"审核通过"按钮'
                await btn.click()
                ok = p().locator('.ant-modal-confirm').get_by_role('button', name='确 定')
                if await ok.count():
                    await ok.click()
                txt = await f.toast()
                await p().wait_for_timeout(1200)
                return txt
            await f.step(f'销项审核通过（{label}）', audit_close)
            await f.shot(f'd-{acc}-close-audit')

        # ---------- F. 基本信息四级审批
        await f.login('100012')
        await f.goto('#/implement/basic?projectId=2')

        async def draft_submit():
            goal = p().locator('textarea').first
            await goal.wait_for(timeout=15000)
            if await goal.is_disabled():
                raise AssertionError('基本信息表单只读（草稿可能仍在审批中）')
            cur = await goal.input_value()
            await goal.fill(cur.split('【UI走查')[0] + '【UI走查' + STAMP + '】')
            await p().get_by_role('button', name='保存草稿').click()
            t1 = await f.toast()
            await p().wait_for_timeout(1000)
            btn = p().get_by_role('button', name='提交审批')
            await btn.wait_for(timeout=15000)
            await btn.click()
            ok = p().locator('.ant-modal-confirm').get_by_role('button', name='确 定')
            if await ok.count():
                await ok.click()
            t2 = await f.toast()
            await p().wait_for_timeout(1500)
            return f'{t1} | {t2}'
        await f.step('基本信息保存草稿并提交审批', draft_submit)
        await f.shot('f1-draft-submitted')

        # 负责人本人提交，首节点自动通过，后续两级审批
        for acc, label in [('100005', '单位科技管理部'), ('100004', '总部科研项目处')]:
            await f.login(acc)
            await f.goto('#/implement/basic?projectId=2')

            async def audit_basic():
                btn = p().get_by_role('button', name='审核通过')
                await btn.wait_for(timeout=15000)
                await btn.click()
                dlg = p().locator('.ant-modal').filter(has_text='审核通过')
                await dlg.wait_for(timeout=5000)
                ta = dlg.locator('textarea')
                if await ta.count():
                    await ta.first.fill('同意')
                await dlg.get_by_role('button', name='确 定').click()
                txt = await f.toast()
                await p().wait_for_timeout(1500)
                return txt
            await f.step(f'基本信息审批（{label}）', audit_basic)
            await f.shot(f'f-{acc}-basic-audit')

        await f.goto('#/overview/detail/2')
        await f.shot('g-detail-after')
        await browser.close()
    json.dump({'steps': steps, 'messages': messages}, open(f'{OUT}/e2e-report.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    fails = [s for s in steps if not s['ok']]
    print(f'==== {len(steps) - len(fails)} passed, {len(fails)} failed ====')

asyncio.run(main())
