"""实施阶段整改验证：逐条测试之前可绕过的路径是否被后端拒绝，以及正向流程是否闭合。"""
import json
import sys
import io
import urllib.request
import urllib.error

BASE = 'http://localhost:6006'
results = []


def call(method, path, token=None, body=None, files=None):
    url = BASE + path
    data = None
    headers = {}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if files:
        boundary = '----rpmboundary'
        buf = io.BytesIO()
        for name, (fname, content) in files.items():
            buf.write(('--%s\r\nContent-Disposition: form-data; name="%s"; filename="%s"\r\nContent-Type: text/plain\r\n\r\n' % (boundary, name, fname)).encode())
            buf.write(content)
            buf.write(b'\r\n')
        buf.write(('--%s--\r\n' % boundary).encode())
        data = buf.getvalue()
        headers['Content-Type'] = 'multipart/form-data; boundary=' + boundary
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode('utf-8'))
        except Exception:
            return {'code': e.code, 'msg': str(e)}


def login(no):
    r = call('POST', '/api/auth/login', body={'username': no, 'password': no})
    assert r.get('code') == 0, r
    return r['data']['token']


def check(name, cond, detail=''):
    results.append((name, bool(cond), detail))
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else '  <- ' + str(detail)[:200]))


owner = login('100012')      # 项目负责人 林晚晴
unit = login('100005')       # 单位科研管理部门负责人 方致远
dept = login('100016')       # 项目承担部门负责人 韩承泽
hq = login('100004')         # 总部科研项目主管 何雨桐
admin = login('100001')

# 找项目 2 的里程碑；为逾期流程新建一个无佐证的逾期节点（计划日期在项目周期内但已过）
import datetime
STAMP = datetime.datetime.now().strftime('%H%M%S')
r = call('POST', '/api/milestones', owner, {'projectId': 2, 'name': '逾期验证节点-' + STAMP, 'planDate': '2026-06-01', 'budget': 10, 'year': 2026})
check('新增里程碑（项目负责人）', r.get('code') == 0, r)
ms = call('GET', '/api/projects/2/milestones', owner)['data']
check('里程碑列表带 dateLocked/canDelete', ms and 'dateLocked' in ms[0] and 'canDelete' in ms[0], ms[:1])
overdue = next((m for m in ms if m['id'] == r['data']), None)
blues = sorted([m for m in ms if m['colorStatus'] == 'BLUE' and m['status'] == 'DOING'], key=lambda m: m['planDate'])
blue = blues[0] if blues else None
check('新建节点为红色逾期且日期锁定、可删除', overdue and overdue['colorStatus'] == 'RED' and overdue['dateLocked'] and overdue['canDelete'], overdue)
check('存在正常推进节点', blue is not None, [m['name'] for m in ms])

# 1. PUT 直接改状态/佐证被忽略
r = call('PUT', '/api/milestones/%d' % blue['id'], owner, {'name': blue['name'], 'status': 'DONE', 'evidence': 1, 'actualDate': '2026-01-01', 'budget': blue['budget']})
after = call('GET', '/api/milestones/%d' % blue['id'], owner)['data']
check('P0-1 PUT 传 status=DONE 不生效', after['status'] == 'DOING' and after['evidence'] == blue['evidence'] and not after.get('actualDate'), after)

# 2. 逾期节点改日期被拒
r = call('PUT', '/api/milestones/%d' % overdue['id'], owner, {'name': overdue['name'], 'planDate': '2026-12-01'})
check('P0-1 逾期节点直接改日期返回 400', r.get('code') == 400 and '变更' in r.get('msg', ''), r)

# 3. 伪造佐证被拒
r = call('POST', '/api/milestones/%d/materials' % overdue['id'], owner, {'fileName': 'fake.pdf', 'fileUrl': '/api/files/download?objectKey=policy/not-exist.pdf'})
check('P0-2 伪造 fileUrl 登记材料返回 400', r.get('code') == 400, r)

# 4. 无佐证销项被拒
r = call('POST', '/api/milestones/%d/close' % overdue['id'], owner, {'lagReason': '试验台架故障'})
check('无佐证材料销项被拒', r.get('code') != 0 and '佐证' in r.get('msg', ''), r)

# 5. 真实上传 + 登记
up = call('POST', '/api/files/upload?bizType=milestone', owner, files={'file': ('evidence.txt', b'test evidence')})
check('MinIO 上传成功', up.get('code') == 0 and up['data'].get('objectKey'), up)
r = call('POST', '/api/milestones/%d/materials' % overdue['id'], owner, {'objectKey': up['data']['objectKey'], 'fileName': 'evidence.txt', 'fileUrl': up['data']['fileUrl'], 'fileSize': 13})
check('真实对象登记材料成功', r.get('code') == 0, r)
r = call('DELETE', '/api/milestones/%d' % overdue['id'], owner)
check('已有佐证的节点不能删除', r.get('code') != 0, r)

# 6. 逾期无滞后原因销项被拒；带原因进入初审
r = call('POST', '/api/milestones/%d/close' % overdue['id'], owner, {})
check('逾期节点无滞后原因销项返回 400', r.get('code') == 400 and '滞后原因' in r.get('msg', ''), r)
r = call('POST', '/api/milestones/%d/close' % overdue['id'], owner, {'lagReason': '试验台架故障导致验证推迟', 'lagMeasure': '已修复，补做验证'})
after = call('GET', '/api/milestones/%d' % overdue['id'], owner)['data']
check('带滞后原因销项进入 CLOSE_DEPT_AUDIT（未直接 DONE）', r.get('code') == 0 and after['status'] == 'CLOSE_DEPT_AUDIT' and after.get('lagReason'), after)

# F08：销项审核中佐证材料锁定
r = call('POST', '/api/milestones/%d/materials' % overdue['id'], owner, {'objectKey': up['data']['objectKey'], 'fileName': 'evidence2.txt', 'fileUrl': up['data']['fileUrl'], 'fileSize': 13})
check('F08 销项审核中替换佐证被拒', r.get('code') != 0 and '锁定' in r.get('msg', ''), r)

# 7. 销项审核：非办理人被拒，两级通过后 DONE
r = call('POST', '/api/milestones/%d/close-audit' % overdue['id'], owner, {'pass': True})
check('项目负责人不能审自己的销项', r.get('code') == 403, r)
r = call('POST', '/api/milestones/%d/close-audit' % overdue['id'], dept, {'pass': True, 'remark': '材料完整'})
mid = call('GET', '/api/milestones/%d' % overdue['id'], owner)['data']
check('部门负责人初审通过 → CLOSE_UNIT_AUDIT', r.get('code') == 0 and mid['status'] == 'CLOSE_UNIT_AUDIT', (r, mid.get('status')))
r = call('POST', '/api/milestones/%d/close-audit' % overdue['id'], unit, {'pass': True})
fin = call('GET', '/api/milestones/%d' % overdue['id'], owner)['data']
check('单位负责人终审通过 → DONE/GREEN，记审核人', r.get('code') == 0 and fin['status'] == 'DONE' and fin['colorStatus'] == 'GREEN' and fin.get('auditBy'), (r, fin.get('status'), fin.get('auditBy')))

# 8. 删除：DONE 节点不可删
r = call('DELETE', '/api/milestones/%d' % overdue['id'], owner)
check('已完成节点不能删除', r.get('code') != 0, r)

# F07：基线节点名称/预算不能普通保存
if blue.get('baselinePlanDate'):
    r = call('PUT', '/api/milestones/%d' % blue['id'], owner, {'name': blue['name'] + '-改名', 'budget': blue['budget']})
    check('F07 基线节点改名被拒（走数据变更）', r.get('code') == 400 and '数据变更' in r.get('msg', ''), r)
    r = call('PUT', '/api/milestones/%d' % blue['id'], owner, {'name': blue['name'], 'budget': (blue['budget'] or 0) + 1})
    check('F07 基线节点改预算被拒（走数据变更）', r.get('code') == 400 and '数据变更' in r.get('msg', ''), r)
else:
    check('F07 样本节点已进入基线（前置条件）', False, blue)
r = call('PUT', '/api/projects/2/annual-plan', owner, {'year': 2026, 'annualGoal': '改动已存档年度目标'})
check('F07 已存档年度目标不能直接修改', r.get('code') == 403, r)

# 9. 延期：生成变更单 → 二级初审 → 总部终审 → 回写日期
NEWDATE = (datetime.date.fromisoformat(blue['planDate']) + datetime.timedelta(days=1)).isoformat()
r = call('POST', '/api/milestones/%d/delay' % blue['id'], owner, {'newPlanDate': NEWDATE, 'reason': '供应商交付延后'})
check('延期申请生成变更单', r.get('code') == 0 and r['data'].get('changeId'), r)
cid = r['data']['changeId']
r = call('POST', '/api/changes/%d/audit' % cid, unit, {'pass': True})
check('草稿状态不能直接审批', r.get('code') != 0, r)
r = call('POST', '/api/changes/%d/submit' % cid, owner)
check('变更单提交审批', r.get('code') == 0, r)
r = call('PUT', '/api/changes/%d' % cid, owner, {'afterValue': '2030-01-01'})
check('审批中的变更单不能修改', r.get('code') == 403, r)
r = call('POST', '/api/changes/%d/audit' % cid, hq, {'pass': True})
check('总部不能越过二级初审节点', r.get('code') == 403, r)
r = call('POST', '/api/changes/%d/audit' % cid, unit, {'pass': True, 'opinion': '同意'})
c = call('GET', '/api/changes/%d' % cid, owner)['data']
check('二级初审通过 → 流转总部终审', r.get('code') == 0 and c['status'] == 'APPROVING' and '总部' in (c.get('flowNode') or ''), (r, c.get('flowNode')))
r = call('POST', '/api/changes/%d/audit' % cid, hq, {'pass': True, 'opinion': '同意延期'})
c = call('GET', '/api/changes/%d' % cid, owner)['data']
m2 = call('GET', '/api/milestones/%d' % blue['id'], owner)['data']
check('总部终审通过 → APPROVED 且里程碑日期回写、delayCount=1、基线保留', r.get('code') == 0 and c['status'] == 'APPROVED' and m2['planDate'] == NEWDATE and m2['delayCount'] >= 1 and m2.get('baselinePlanDate'), (c.get('status'), m2.get('planDate'), m2.get('delayCount'), m2.get('baselinePlanDate')))
check('变更审批记录 auditTrail 有 3 条', isinstance(c.get('auditTrail'), list) and len(c['auditTrail']) == 3, c.get('auditTrail'))

# 10. 项目团队不能直接改台账
r = call('PUT', '/api/projects/2', owner, {'name': '被篡改的名称', 'status': 'FINISHED'})
p = call('GET', '/api/projects/2', owner)['data']
check('P1 项目团队 PUT /projects 返回 403，台账未变', r.get('code') == 403 and p['name'] != '被篡改的名称', (r, p.get('name')))
r = call('POST', '/api/projects', owner, {'name': '越权新建', 'orgId': 1})
check('项目团队不能新建台账项目', r.get('code') == 403, r)

# 11. 基本信息草稿 → 四级审批 → 台账更新
d = call('GET', '/api/projects/2/basic-draft', owner)['data']
check('basic-draft 未在审批中且 canEdit', d['status'] != 'APPROVING' and d['canEdit'], d)
payload = {k: p.get(k) for k in ['name', 'goal', 'startDate', 'endDate', 'levelCode', 'filingDept', 'channelId', 'leadOrgName', 'mainWork', 'totalFund', 'major1', 'major2', 'ownerName']}
payload['goal'] = (p.get('goal') or '').split('【审批补充')[0] + '【审批补充' + STAMP + '：新增余度重构半物理验证目标】'
payload['participants'] = [{'orgName': '北京航空航天大学', 'workContent': '余度架构建模'}, {'orgName': '中航工业某所', 'workContent': '试验验证'}]
payload['teamMembers'] = p.get('teamMembers') or []
r = call('PUT', '/api/projects/2/basic-draft', owner, payload)
check('保存基本信息草稿', r.get('code') == 0, r)
p_before = call('GET', '/api/projects/2', owner)['data']
check('草稿保存后台账未变', ('审批补充' + STAMP) not in (p_before.get('goal') or ''), p_before.get('goal'))
r = call('POST', '/api/projects/2/basic-draft/submit', owner)
d = call('GET', '/api/projects/2/basic-draft', owner)['data']
check('提交后 APPROVING，首节点 PROJECT_LEADER', r.get('code') == 0 and d['status'] == 'APPROVING' and d['flowNode'] == 'PROJECT_LEADER', (r, d.get('flowNode')))
r = call('PUT', '/api/projects/2/basic-draft', owner, payload)
check('审批中草稿不能修改', r.get('code') == 403, r)
r = call('POST', '/api/projects/2/basic-draft/audit', unit, {'pass': True})
check('单位负责人不能越过项目负责人节点', r.get('code') == 403, r)
r = call('POST', '/api/projects/2/basic-draft/audit', owner, {'pass': True, 'opinion': '信息属实'})
d = call('GET', '/api/projects/2/basic-draft', owner)['data']
check('项目负责人审核 → UNIT_TECH', r.get('code') == 0 and d['flowNode'] == 'UNIT_TECH', (r, d.get('flowNode')))
pend = call('GET', '/api/projects/basic-drafts/pending', unit)['data']
check('单位负责人待办中出现该草稿', any(x['projectId'] == 2 for x in pend), pend)
r = call('POST', '/api/projects/2/basic-draft/audit', unit, {'pass': True})
d = call('GET', '/api/projects/2/basic-draft', owner)['data']
check('单位审核 → 跳过分管领导 → HQ', r.get('code') == 0 and d['flowNode'] == 'HQ', (r, d.get('flowNode'), [n for n in d['flowNodes'] if n.get('skipped')]))
r = call('POST', '/api/projects/2/basic-draft/audit', hq, {'pass': True, 'opinion': '备案'})
d = call('GET', '/api/projects/2/basic-draft', owner)['data']
p_after = call('GET', '/api/projects/2', owner)['data']
check('总部确认 → APPROVED 且台账已更新（目标+参研单位）', r.get('code') == 0 and d['status'] == 'APPROVED' and ('审批补充' + STAMP) in (p_after.get('goal') or '') and len(p_after.get('participants') or []) == 2, (d.get('status'), p_after.get('goal'), p_after.get('participants')))

# F05：草稿里拟议的岗位不能授予审批权（审核人按台账团队解析）
hack = dict(payload)
hack['teamMembers'] = [{'roleName': '项目负责人', 'userName': '林晚晴', 'employeeNo': '100012', 'groupCode': 'TECH'},
                       {'roleName': '单位科技部长', 'userName': '林晚晴', 'employeeNo': '100012', 'groupCode': 'MGMT'}]
r = call('PUT', '/api/projects/2/basic-draft', owner, hack)
check('F05 保存把自己指为单位科技部长的草稿', r.get('code') == 0, r)
r = call('POST', '/api/projects/2/basic-draft/submit', owner)
r = call('POST', '/api/projects/2/basic-draft/audit', owner, {'pass': True, 'opinion': '自审1'})
d = call('GET', '/api/projects/2/basic-draft', owner)['data']
r = call('POST', '/api/projects/2/basic-draft/audit', owner, {'pass': True, 'opinion': '自审2'})
check('F05 负责人不能自审单位科技管理部节点', r.get('code') == 403 and d.get('flowNode') == 'UNIT_TECH', (r, d.get('flowNode')))
# 由真正的单位负责人退回，恢复状态
r = call('POST', '/api/projects/2/basic-draft/audit', unit, {'pass': False, 'opinion': '拟议岗位不合规，退回'})
check('F05 单位负责人可退回该草稿', r.get('code') == 0, r)

# 12. 数据范围
mine = call('GET', '/api/milestones/mine', owner)['data']
check('/milestones/mine 只含本人可见项目', mine and all(m['projectId'] in (1, 2) for m in mine), len(mine))

# 13. 预警：扫描并按工号投递
r = call('POST', '/api/warnings/scan', admin)
check('管理员触发预警扫描', r.get('code') == 0, r)
w = call('GET', '/api/warnings?page=1&size=20', owner)['data']
check('项目负责人收到按工号投递的预警', w['total'] >= 1 and all('100012' in (x.get('receiverNos') or '') for x in w['records']), (w['total'], [x.get('receiverNos') for x in w['records'][:2]]))
r = call('POST', '/api/warnings/scan', owner)
check('非管理员不能触发扫描', r.get('code') == 403, r)

# 14. 未知异常不回显内部信息（用非法 id 触发 400 路径）
r = call('GET', '/api/milestones/abc', owner)
check('非法参数不泄露堆栈', r.get('code') in (400, 500) and 'Exception' not in json.dumps(r), r)

fails = [x for x in results if not x[1]]
print('\n==== %d passed, %d failed ====' % (len(results) - len(fails), len(fails)))
sys.exit(1 if fails else 0)
