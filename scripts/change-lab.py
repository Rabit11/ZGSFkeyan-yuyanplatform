#!/usr/bin/env python3
"""Project-change rehearsal stack. Only rpm_change_lab is initialized; formal rpm is never copied or reset."""
import argparse
import json
import os
from pathlib import Path
import runpy
import secrets
import signal
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
platform = runpy.run_path(str(ROOT / 'scripts/local-platform.py'))
LAB = ROOT / 'deploy/change-lab'
LAB.mkdir(parents=True, exist_ok=True)
DB = 'rpm_change_lab'


def config():
    file = LAB / 'secrets.json'
    if not file.exists():
        platform['private_write'](file, json.dumps({k: secrets.token_hex(24) for k in ['mysql', 'jwt']}))
    return json.loads(file.read_text())


def init():
    if (LAB / 'initialized').exists():
        return
    cfg = config()
    # Fixed names: no command-line database input and no production database copy.
    platform['mysql']("CREATE DATABASE IF NOT EXISTS rpm_change_lab CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
                      f"CREATE USER IF NOT EXISTS 'rpm_change_lab'@'127.0.0.1' IDENTIFIED BY '{cfg['mysql']}';"
                      "GRANT ALL PRIVILEGES ON rpm_change_lab.* TO 'rpm_change_lab'@'127.0.0.1';")
    db = ROOT / 'backend/src/main/resources/db'
    for name in ['schema.sql', 'migration_backup_v1.sql', 'personnel_roster_v1.sql',
                 'seed_demo_projects.sql', 'keep_two_project_samples.sql', 'seed_declarations_5.sql',
                 'personnel_roster_v2.sql', 'migration_demo_password_employee_no_v1.sql', 'migration_change_v2.sql']:
        platform['mysql']((db / name).read_text(), DB)
    platform['mysql']("UPDATE sys_user SET status=0 WHERE username IN ('pm01','chief01','mgr01','fin01');"
                      "UPDATE proj_info SET name=CONCAT('【演练样例】',name);", DB)
    (LAB / 'initialized').write_text(time.strftime('%Y-%m-%d %H:%M:%S'))


def start():
    platform['infra']()
    init()
    for migration in ['migration_change_v2.sql', 'migration_change_v3.sql']:
        platform['mysql']((ROOT / 'backend/src/main/resources/db' / migration).read_text(), DB)
    cfg = config()
    shared = platform['config']()
    env = {**platform['ENV'], 'SERVER_ADDRESS': '127.0.0.1', 'SERVER_PORT': '8081',
           'SPRING_DATASOURCE_URL': 'jdbc:mysql://127.0.0.1:3306/rpm_change_lab?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true',
           'SPRING_DATASOURCE_USERNAME': DB, 'SPRING_DATASOURCE_PASSWORD': cfg['mysql'],
           'SPRING_DATA_REDIS_HOST': '127.0.0.1', 'SPRING_DATA_REDIS_PORT': '6379', 'SPRING_DATA_REDIS_DATABASE': '1',
           'RPM_JWT_SECRET': cfg['jwt'], 'RPM_JWT_EXPIRE_HOURS': '12',
           'RPM_MINIO_ENDPOINT': 'http://127.0.0.1:9000', 'RPM_MINIO_ACCESS_KEY': 'rpm-local',
           'RPM_MINIO_SECRET_KEY': shared['minio'], 'RPM_MINIO_BUCKET': 'rpm-change-lab',
           'RPM_BACKUP_DIR': str(LAB / 'backups'), 'RPM_CHANGE_TEST_MODE': 'true',
           'RPM_CHANGE_LEGAL_EMPLOYEES': '100009',
           'MYBATIS_PLUS_GLOBAL_CONFIG_DB_CONFIG_LOGIC_DELETE_FIELD': 'deleted',
           'MYBATIS_PLUS_GLOBAL_CONFIG_DB_CONFIG_LOGIC_DELETE_VALUE': '1',
           'MYBATIS_PLUS_GLOBAL_CONFIG_DB_CONFIG_LOGIC_NOT_DELETE_VALUE': '0',
           'SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE': '50MB', 'SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE': '50MB'}
    platform['spawn']('change-lab-backend', 8081, [platform['JDK'] / 'bin/java', '-Xms96m', '-Xmx512m',
                       '-Duser.timezone=Asia/Shanghai', '-jar', platform['runtime_jar']()],
                      env, 'http://127.0.0.1:8081/api/health')
    platform['spawn']('change-lab-frontend', 6026, ['node', ROOT / 'scripts/change-lab-gateway.mjs'],
                      health='http://127.0.0.1:6026/')
    seed()
    print('演练入口：原平台 → 项目变更 → 进入测试模式；无需额外隧道端口', flush=True)


def seed(reset=False):
    env = {**platform['ENV'], 'CHANGE_QA_DB': DB, 'CHANGE_QA_BASE': 'http://127.0.0.1:6026'}
    subprocess.run(['node', str(ROOT / 'qa/change/seed-lab.mjs'), *(['--reset'] if reset else [])],
                   cwd=ROOT, env=env, check=True)


def stop():
    for name, port in [('change-lab-frontend', 6026), ('change-lab-backend', 8081)]:
        pid = platform['running'](name)
        if pid:
            os.kill(pid, signal.SIGTERM)
            for _ in range(120):
                if not platform['port_open'](port):
                    break
                time.sleep(.5)
            if platform['port_open'](port):
                raise RuntimeError(f'{name} did not stop; inspect its log')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['start', 'stop', 'restart', 'status', 'reset-scenarios'])
    command = parser.parse_args().command
    if command == 'status':
        for name, port in [('change-lab-backend', 8081), ('change-lab-frontend', 6026)]:
            print(f"{name}: pid={platform['running'](name)}, listening={platform['port_open'](port)}")
    elif command == 'reset-scenarios':
        if not (LAB / 'initialized').exists():
            raise RuntimeError('Start the lab first')
        seed(reset=True)
    elif command == 'restart':
        stop()
        start()
    else:
        {'start': start, 'stop': stop}[command]()
