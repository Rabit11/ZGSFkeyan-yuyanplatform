import os,sys,json,runpy,hashlib,hmac,datetime,urllib.request,urllib.parse
from pathlib import Path
# Minimal SigV4 DELETE, avoiding a new Python package dependency.
root=Path(__file__).resolve().parents[2]
cfg=json.loads((root/'deploy/local-secrets.json').read_text())
bucket = 'rpm-change-lab' if os.environ.get('CHANGE_QA_DB') == 'rpm_change_lab' else 'rpm-attachments'
for key in json.load(sys.stdin):
 if not key.startswith('change/'): raise ValueError('Refusing non-change object')
 now=datetime.datetime.now(datetime.timezone.utc); date=now.strftime('%Y%m%d'); stamp=now.strftime('%Y%m%dT%H%M%SZ')
 path='/'+bucket+'/'+urllib.parse.quote(key,safe='/'); payload=hashlib.sha256(b'').hexdigest()
 headers=f'host:127.0.0.1:9000\nx-amz-content-sha256:{payload}\nx-amz-date:{stamp}\n'
 signed='host;x-amz-content-sha256;x-amz-date'; canonical='DELETE\n'+path+'\n\n'+headers+'\n'+signed+'\n'+payload
 scope=date+'/us-east-1/s3/aws4_request'; text='AWS4-HMAC-SHA256\n'+stamp+'\n'+scope+'\n'+hashlib.sha256(canonical.encode()).hexdigest()
 signing=('AWS4'+cfg['minio']).encode()
 for value in [date,'us-east-1','s3','aws4_request']: signing=hmac.new(signing,value.encode(),hashlib.sha256).digest()
 signature=hmac.new(signing,text.encode(),hashlib.sha256).hexdigest()
 request=urllib.request.Request('http://127.0.0.1:9000'+path,method='DELETE',headers={'x-amz-content-sha256':payload,'x-amz-date':stamp,'Authorization':f'AWS4-HMAC-SHA256 Credential=rpm-local/{scope}, SignedHeaders={signed}, Signature={signature}'})
 with urllib.request.urlopen(request) as response: assert response.status==204
