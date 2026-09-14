from pathlib import Path
import hashlib,json,runpy,subprocess
root=Path(__file__).resolve().parents[2]
before=json.loads((root/'deploy/change-v3/before-files.json').read_text())
allowed_prefixes=('frontend/src/views/implement/change/','backend/src/main/java/com/comac/rpm/modules/change/', 'qa/change/', 'docs/change/')
allowed_files={'frontend/src/views/implement/Change.vue','frontend/src/api/change.ts','frontend/vite.config.ts','scripts/local-gateway.mjs','scripts/change-lab-gateway.mjs','scripts/local-platform.py'}
changes=[]
for name,digest in before.items():
    p=root/name
    current=hashlib.sha256(p.read_bytes()).hexdigest() if p.exists() else None
    if current!=digest:changes.append({'path':name,'allowed':name in allowed_files or name.startswith(allowed_prefixes),'before':digest,'after':current})
manifest=json.loads((root/'deploy/change/source/manifest.json').read_text())
source=[{'name':v['name'],'unchanged':hashlib.sha256((Path('/home/dev/research_platform')/v['name']).read_bytes()).hexdigest()==v['sha256']} for v in manifest]
assert all(v['unchanged'] for v in source)
result={'sourceDocuments':source,'checkedExistingFiles':len(before),'changes':changes,'outsideScope':[v for v in changes if not v['allowed']]}
(root/'deploy/change-v3/scope-check.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
assert not result['outsideScope'], result['outsideScope']
print(f"Existing files checked: {len(before)}; changed: {len(changes)}; outside scope: 0")
