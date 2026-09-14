"""实施阶段角色权限矩阵验证（按需求 V19.1 二（二）角色定义 + 四（三）实施阶段审批流）。
对 16 个花名册账号逐一调用接口，核对"能看什么、能改什么、能审什么"是否与需求一致。
用法：PYTHONIOENCODING=utf-8 python qa/impl-stage-role-matrix.py [BASE_URL]
"""
import json
import sys
import urllib.request
import urllib.error

BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:6006'
PROJECT = 2  # XM2026S002，上飞院（org 10），项目团队为花名册 100012~100015

# 花名册：工号 → (姓名, 任职身份码, 需求角色)
ROSTER = {
    '100001': ('系统管理员', 'admin', '超级管理员'),
    '100002': ('周明远', 'leader', '公司领导（只读）'),
    '100003': ('王建国', 'hqHead', '管理团队·总部处长'),
    '100004': ('何雨桐', 'hqStaff', '管理团队·总部主管'),
    '100005': ('方致远', 'unitHead', '管理团队·单位科研管理部门负责人'),
    '100006': ('田念慈', 'unitStaff', '管理团队·单位项目主管'),
    '100007': ('陈铁军', 'chief1', '责任总师·一级'),
    '100008': ('蔡文渊', 'chief2', '责任总师·二级'),
    '100009': ('赵美玲', 'finHq', '财务团队·总部'),
    '100010': ('毕仲文', 'finHead', '财务团队·单位财务部长'),
    '100011': ('龚雪君', 'finStaff', '财务团队·单位财务主管'),
    '100012': ('林晚晴', 'owner', '项目团队·项目负责人'),
    '100013': ('顾思远', 'contactLogin', '项目团队·项目联系人'),
    '100014': ('沈知行', 'techLead', '项目团队·技术负责人'),
    '100015': ('陆嘉言', 'projectPm', '项目团队·项目主管'),
    '100016': ('韩承泽', 'deptHead', '项目承担部门负责人'),
}
TEAM = {'owner', 'contactLogin', 'techLead', 'projectPm'}
MGMT = {'hqHead', 'hqStaff', 'unitHead', 'unitStaff'}
LEDGER_EDIT = MGMT | {'admin'}

results = []


def call(method, path, token=None, body=None):
    data = json.dumps(body, ensure_ascii=False).encode('utf-8') if body is not None else None
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode('utf-8'))
        except Exception:
            return {'code': e.code, 'msg': str(e)}
    except Exception as e:
        return {'code': -1, 'msg': str(e)}


def ok(r):
    return isinstance(r, dict) and r.get('code') == 0


def check(who, name, cond, detail=''):
    results.append((who, name, bool(cond), str(detail)[:160]))


def main():
    tokens = {}
    for no in ROSTER:
        r = call('POST', '/api/auth/login', body={'username': no, 'password': no})
        if ok(r):
            tokens[no] = r['data']['token']
        else:
            check(no, '登录', False, r)
    owner_t = tokens['100012']
    # 清理上一轮残留：若基本信息草稿仍在审批中，按节点顺序补审通过，保证矩阵可重复执行
    for _ in range(4):
        d = call('GET', f'/api/projects/{PROJECT}/basic-draft', owner_t).get('data') or {}
        if d.get('status') != 'APPROVING':
            break
        node = d.get('flowNode')
        actor = {'PROJECT_LEADER': '100012', 'UNIT_TECH': '100005', 'UNIT_LEADER': '100005', 'HQ': '100004'}.get(node)
        # 退回而不是通过：避免把测试草稿写进台账
        r = call('POST', f'/api/projects/{PROJECT}/basic-draft/audit', tokens.get(actor), {'pass': False, 'opinion': '矩阵前清理：退回测试草稿'})
        print('prep: audit', node, 'by', actor, '->', r.get('code'), r.get('msg', ''))
    # 准备一个可用于销项/删除测试的节点（技术负责人新增），并保证清单不在审核中
    prep = call('POST', '/api/milestones', tokens['100014'], {'projectId': PROJECT, 'name': '角色矩阵测试节点', 'planDate': '2026-11-30', 'budget': 1, 'year': 2026})
    test_ms = prep.get('data') if ok(prep) else None

    for no, (name, ident, role) in ROSTER.items():
        t = tokens.get(no)
        if not t:
            continue
        who = f'{no} {name}（{role}）'
        is_team = ident in TEAM

        # ---- 查看权限（本单位 / 总部 / 项目团队自有项目：均应可看 XM2026S002）
        r = call('GET', f'/api/projects/{PROJECT}', t)
        check(who, '查看项目详情', ok(r), r.get('msg'))
        r = call('GET', f'/api/projects/{PROJECT}/milestones', t)
        check(who, '查看里程碑（含预算）', ok(r), r.get('msg'))
        r = call('GET', '/api/milestones/board?year=2026', t)
        check(who, '打开里程碑看板', ok(r), r.get('msg'))
        r = call('GET', f'/api/projects/{PROJECT}/basic-draft', t)
        check(who, '查看基本信息审批状态', ok(r), r.get('msg'))
        if ok(r):
            d = r['data']
            check(who, '基本信息可填写标记 = 项目团队', bool(d.get('canEdit')) == is_team, f"canEdit={d.get('canEdit')}")

        # ---- 填报权限：基本信息草稿仅项目团队；管理员/管理团队/总师/财务/领导均 403
        # 占位草稿沿用台账真实值，避免后续补审时把演示数据改掉
        pj = call('GET', f'/api/projects/{PROJECT}', t).get('data') or {}
        payload = {k: pj.get(k) for k in ('name', 'goal', 'startDate', 'endDate', 'levelCode', 'filingDept', 'channelId', 'leadOrgName', 'mainWork', 'totalFund', 'major1', 'major2', 'ownerName')}
        payload['participants'] = pj.get('participants') or []
        payload['teamMembers'] = pj.get('teamMembers') or [{'roleName': '项目负责人', 'userName': '林晚晴', 'employeeNo': '100012'}]
        r = call('PUT', f'/api/projects/{PROJECT}/basic-draft', t, payload)
        check(who, '保存基本信息草稿' + ('（应允许）' if is_team else '（应拒绝）'), ok(r) == is_team, r.get('msg'))

        # ---- 里程碑新增：项目团队；其余 403
        r = call('POST', '/api/milestones', t, {'projectId': PROJECT, 'name': f'矩阵-{no}', 'planDate': '2026-12-01', 'budget': 1, 'year': 2026})
        check(who, '新增里程碑节点' + ('（应允许）' if is_team else '（应拒绝）'), ok(r) == is_team, r.get('msg'))
        if ok(r):
            call('DELETE', f"/api/milestones/{r['data']}", t)

        # ---- 销项提交：仅项目负责人
        if test_ms:
            r = call('POST', f'/api/milestones/{test_ms}/close', t, {})
            expect = ident == 'owner'
            # 负责人会因无佐证被拒（业务校验），但不是 403；其他人应为 403 权限拒绝
            if expect:
                check(who, '提交销项（负责人：进入业务校验而非权限拒绝）', r.get('code') != 403, r.get('msg'))
            else:
                check(who, '提交销项（应拒绝 403）', r.get('code') == 403, r.get('msg'))

        # ---- 变更发起：项目团队
        r = call('POST', '/api/changes', t, {'projectId': PROJECT, 'changeType': 'DATA', 'category': 'BASIC', 'title': f'矩阵-{no}', 'reason': '测试'})
        check(who, '发起变更' + ('（应允许）' if is_team else '（应拒绝）'), ok(r) == is_team, r.get('msg'))
        if ok(r):
            call('DELETE', f"/api/changes/{r['data']}", t)

        # ---- 清单审核 / 销项审核 / 基本信息审核：错误角色应 403（当前无待审对象时应为“不在审核环节”而非权限放行）
        r = call('POST', f'/api/milestones/annual-plan/audit?projectId={PROJECT}&year=2026', t, {'pass': True})
        if ident == 'unitHead':
            check(who, '清单审核（应进入业务校验）', r.get('code') != 403, r.get('msg'))
        else:
            check(who, '清单审核（应拒绝 403）', r.get('code') == 403, r.get('msg'))

        # ---- 台账直接编辑：管理团队/管理员；项目团队、总师、财务、领导 403
        # 已立项（平台流程产生）项目：台账由流程归集，任何人都不能直接编辑；表单维护导入项目才允许管理团队维护
        r = call('PUT', f'/api/projects/{PROJECT}', t, {'name': '民机飞控余度架构可靠性与重构技术研究'})
        check(who, '台账直接编辑已立项项目（应拒绝）', not ok(r), r.get('msg'))

        # ---- 预警：能取到列表（内容按工号过滤）
        r = call('GET', '/api/warnings?page=1&size=5', t)
        check(who, '预警列表', ok(r), r.get('msg'))

    # 清理
    if test_ms:
        call('DELETE', f'/api/milestones/{test_ms}', tokens['100014'])
    # 清掉矩阵占位草稿（团队成员保存的草稿）：由负责人重新保存为真实值即可，这里删除草稿记录不暴露接口，保留 DRAFT 无副作用

    fails = [x for x in results if not x[2]]
    cur = None
    for who, name, passed, detail in results:
        if who != cur:
            print('\n== ' + who)
            cur = who
        print(('  PASS ' if passed else '  FAIL ') + name + ('' if passed else '  <- ' + detail))
    print(f'\n==== {len(results) - len(fails)} passed, {len(fails)} failed ====')
    sys.exit(1 if fails else 0)


if __name__ == '__main__':
    main()
