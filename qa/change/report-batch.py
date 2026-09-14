"""Collect actual results for the multi-item change delivery; fail if a check is missing or failed."""
from pathlib import Path
import json, re, hashlib, shutil, datetime, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2]
out=root/'deploy/change-batch'
checks=[]
for filename,label in [('api-results.json','新增真实接口场景'),('browser-results.json','新增浏览器流程'),('selection-labels.json','最终选择与场景入口'),('regression-api-results.json','既有接口回归'),('regression-browser-results.json','既有页面操作回归'),('lab-results.json','演练隔离与样式'),('tunnel-api-results.json','同源转发接口'),('entry-results.json','单地址完整流程')]:
 data=json.loads((out/filename).read_text()); rows=data['results']; assert rows and all(r['pass'] for r in rows), filename
 assert not data.get('errors'), filename
 if filename=='entry-results.json': assert data['formalBusinessUnchanged']
 checks.append({'name':label,'count':len(rows),'pass':True,'evidence':filename})
data=json.loads((out/'other-pages-results.json').read_text());assert not data['errors'] and not data['failures'] and all(r['pass'] for r in data['routes'])
checks.append({'name':'其他业务页面只读回归','count':len(data['routes']),'pass':True,'evidence':'other-pages-results.json'})
unit=(out/'frontend-unit.log').read_text();count=int(re.search(r'ℹ tests (\d+)',unit)[1]);assert f'ℹ pass {count}' in unit and 'ℹ fail 0' in unit
checks.append({'name':'前端策略与状态测试','count':count,'pass':True,'evidence':'frontend-unit.log'})
suites=[ET.parse(p).getroot() for p in (root/'backend/target/surefire-reports').glob('TEST-*Change*.xml')];assert suites and all(int(s.get('failures'))==0 and int(s.get('errors'))==0 for s in suites)
checks.append({'name':'后端单元测试','count':sum(int(s.get('tests')) for s in suites),'pass':True,'evidence':'backend-build.log'})
assert 'BUILD SUCCESS' in (out/'backend-build.log').read_text()
assert '✓ built in' in (out/'frontend-build.log').read_text()
assert not re.search(r'error TS\d+', (out/'typecheck.log').read_text())
shutil.copy2(root/'deploy/change-v3/scope-check.json',out/'scope-check.json')
scope=json.loads((out/'scope-check.json').read_text());assert not scope['outsideScope'] and all(s['unchanged'] for s in scope['sourceDocuments'])
report={'date':datetime.datetime.now(datetime.timezone.utc).isoformat(),'checks':checks,'totalChecks':sum(c['count'] for c in checks),'allPassed':True,'existingFilesScopeChecked':scope['checkedExistingFiles'],'outsideScope':0,'originalDocumentsUnchanged':True,'formalBusinessUnchangedInEntryVerification':True,'retainedProof':json.loads((out/'retained-proof.json').read_text()),'entry':'原平台地址 → 项目变更 → 进入测试模式','actualUserTunnelVerified':False}
(out/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
files=[p for parent in ['frontend/src/views/implement/change','backend/src/main/java/com/comac/rpm/modules/change','qa/change','docs/change'] for p in (root/parent).rglob('*') if p.is_file()]+[root/p for p in ['frontend/src/views/implement/Change.vue','backend/src/main/resources/db/migration_change_v3.sql','scripts/local-platform.py','scripts/change-lab.py']]
(out/'source-sha256.json').write_text(json.dumps({str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(files)},indent=2)+'\n')
print(json.dumps({'allPassed':True,'totalChecks':report['totalChecks'],'checks':checks},ensure_ascii=False,indent=2))
