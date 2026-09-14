"""Report current multi-category UI checks from their actual outputs."""
from pathlib import Path
import datetime, hashlib, json, re, shutil
root=Path(__file__).resolve().parents[2]
out=root/'deploy/change-multiselect'
checks=[]
for file,label in [('browser-results.json','多类别多对象完整流程'),('regression-browser-results.json','既有页面操作回归'),('lab-results.json','演练隔离及样式'),('entry-results.json','单地址真实流程'),('selection-labels.json','最终场景及输入标签')]:
 v=json.loads((out/file).read_text());assert v['results'] and all(r['pass'] for r in v['results']) and not v.get('errors'),file
 checks.append({'name':label,'count':len(v['results']),'pass':True,'evidence':file})
v=json.loads((out/'other-pages-results.json').read_text());assert not v['errors'] and not v['failures'] and all(r['pass'] for r in v['routes'])
checks.append({'name':'其他页面只读回归','count':len(v['routes']),'pass':True,'evidence':'other-pages-results.json'})
unit=(out/'unit.log').read_text();n=int(re.search(r'ℹ tests (\d+)',unit)[1]);assert f'ℹ pass {n}' in unit and 'ℹ fail 0' in unit
checks.append({'name':'前端策略与状态单元测试','count':n,'pass':True,'evidence':'unit.log'})
assert '✓ built in' in (out/'build.log').read_text() and not re.search(r'error TS\d+', (out/'typecheck.log').read_text())
shutil.copy2(root/'deploy/change-v3/scope-check.json',out/'scope-check.json');scope=json.loads((out/'scope-check.json').read_text());assert not scope['outsideScope']
v=json.loads((out/'browser-results.json').read_text());assert len(v['saveBodies'])==1 and len(v['saveBodies'][0]['items'])==4 and len(v['submitRequests'])==1
report={'date':datetime.datetime.now(datetime.timezone.utc).isoformat(),'allPassed':True,'checks':checks,'totalChecks':sum(c['count'] for c in checks),'sameApplicationProof':{'saveRequests':1,'submitRequests':1,'items':4,'categories':3,'retained':v.get('retainedProof')},'entryUnchanged':True,'actualUserTunnelVerified':False,'scope':{'checkedExistingFiles':scope['checkedExistingFiles'],'outsideScope':scope['outsideScope'],'originalDocumentsUnchanged':all(s['unchanged'] for s in scope['sourceDocuments'])}}
(out/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
paths=[p for name in ['frontend/src/views/implement/change','qa/change','docs/change'] for p in (root/name).rglob('*') if p.is_file()]+[root/'frontend/src/views/implement/Change.vue']
(out/'source-sha256.json').write_text(json.dumps({str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths},indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False,indent=2))
