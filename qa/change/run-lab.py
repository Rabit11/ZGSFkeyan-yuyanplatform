#!/usr/bin/env python3
"""Run repeatable project-change verification against the isolated rehearsal stack."""
from pathlib import Path
import os
import runpy
import shutil
import subprocess
import json

root = Path(__file__).resolve().parents[2]
os.chdir(root)
m = runpy.run_path(str(root / 'scripts/local-platform.py'))
env = {**m['ENV'], 'CHANGE_QA_DB': 'rpm_change_lab', 'CHANGE_QA_BASE': 'http://127.0.0.1:6026'}
out = root / 'deploy/change-v3'
out.mkdir(parents=True, exist_ok=True)
checks=[]

def run(name, cmd, copy=()):
    print('Running '+name, flush=True)
    with (out / (name+'.log')).open('w') as log:
        result=subprocess.run(cmd,cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT)
    checks.append({'name':name,'pass':result.returncode==0})
    (out/'run-results.json').write_text(json.dumps(checks,indent=2))
    if result.returncode:
        raise SystemExit(f'{name} failed; see {out/name}.log')
    for file in copy:
        shutil.copy2(root/'deploy/change'/file,out/file)

run('lab-start',['python3','scripts/change-lab.py','start'])
run('typecheck',['npm','run','typecheck','--prefix','frontend'])
run('frontend-tests',['node','--test','qa/change/policy.test.mjs','qa/change/workbench.test.mjs'])
run('backend-tests',[str(root/'.deploy-libs/apache-maven-3.9.9/bin/mvn'),'-B','-f','backend/pom.xml','test'])
run('api',['node','qa/change/api.mjs'],['api-results.json'])
run('batch-api',['node','qa/change/batch.mjs'])
run('selection-labels',['node','qa/change/selection-labels.mjs'])
run('multi-object-browser',['node','qa/change/multiselect-browser.mjs'])
run('browser',['node','qa/change/browser.mjs'],['browser-results.json'])
run('lab-test',['node','qa/change/lab.mjs'])
run('entry',['node','qa/change/entry.mjs'])
run('other-pages',['node','qa/change/other-pages.mjs'],['other-pages-results.json'])
# Scope/report snapshots belong to this local delivery and are optional on a fresh checkout.
if os.environ.get('CHANGE_QA_DELIVERY_REPORT') == '1' and all((out / name).exists() for name in ['before-files.json','reset-results.json','backend-build.log','frontend-build.log']):
    run('scope',['python3','qa/change/check-scope.py'])
    run('report',['python3','qa/change/report-v3.py'])
print('All isolated module checks passed; reports: deploy/change-v3/',flush=True)
