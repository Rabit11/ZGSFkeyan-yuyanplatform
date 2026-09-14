#!/usr/bin/env python3
"""Restore only repository-provided milestone sample files to local MinIO. Needs boto3."""
from pathlib import Path
import runpy
import subprocess
from urllib.parse import parse_qs, urlparse
import boto3
from botocore.config import Config

m = runpy.run_path(str(Path(__file__).with_name('local-platform.py')))
rows = subprocess.check_output([str(m['SYS'] / 'bin/mysql'),
    f'--defaults-file={m["DEPLOY"]}/mysql-admin.cnf', '-N', '-B', 'rpm', '-e',
    "SELECT file_name,file_url FROM proj_material WHERE biz_type='MILESTONE'"], env=m['ENV'], text=True)
s3 = boto3.client('s3', endpoint_url='http://127.0.0.1:9000', aws_access_key_id='rpm-local',
    aws_secret_access_key=m['config']()['minio'], region_name='us-east-1', config=Config(signature_version='s3v4'))
count = 0
for row in rows.splitlines():
    name, url = row.split('\t')
    if Path(name).name != name:
        continue
    file = m['ROOT'] / 'backend/src/main/resources/db/sample-materials' / name
    key = parse_qs(urlparse(url).query).get('objectKey', [None])[0]
    if key and file.is_file():
        data = file.read_bytes()
        s3.put_object(Bucket='rpm-attachments', Key=key, Body=data, ContentType='text/markdown; charset=utf-8')
        assert s3.get_object(Bucket='rpm-attachments', Key=key)['Body'].read() == data
        count += 1
print(f'Restored and verified {count} repository sample attachments.')
