#!/usr/bin/env python3
"""Manage the isolated local stack; data and credentials stay in ignored deploy/."""
import argparse
import hashlib
import shutil
import json
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DEPLOY = ROOT / 'deploy'
LIBS = ROOT / '.deploy-libs'
SYS = LIBS / 'rootfs/usr'
for directory in ['logs', 'run', 'data/redis', 'data/minio', 'data/backups']:
    (DEPLOY / directory).mkdir(parents=True, exist_ok=True)
ENV = dict(os.environ)
ENV['LD_LIBRARY_PATH'] = str(SYS / 'lib/x86_64-linux-gnu')
JDK = next(LIBS.glob('jdk-17*'), None)
if JDK:
    ENV['JAVA_HOME'] = str(JDK)
    ENV['PATH'] = str(JDK / 'bin') + ':' + ENV['PATH']


def private_write(file, content):
    fd = os.open(file, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w') as out:
        out.write(content)


def config():
    file = DEPLOY / 'local-secrets.json'
    if not file.exists():
        private_write(file, json.dumps({key: secrets.token_hex(24) for key in ['mysql', 'jwt', 'minio']}, indent=2))
    return json.loads(file.read_text())


def running(name):
    file = DEPLOY / 'run' / (name + '.json')
    if not file.exists():
        return None
    item = json.loads(file.read_text())
    try:
        # Verify Linux start time to avoid signalling a reused PID.
        fields = Path(f'/proc/{item["pid"]}/stat').read_text().split(') ', 1)[1].split()
        if fields[0] != 'Z' and fields[19] == item['start']:
            return item['pid']
    except (FileNotFoundError, ProcessLookupError):
        pass
    return None


def port_open(port):
    with socket.socket() as sock:
        sock.settimeout(0.2)
        return sock.connect_ex(('127.0.0.1', port)) == 0


def spawn(name, port, command, env=None, health=None):
    if running(name):
        print(name + ': already running', flush=True)
        return
    if port_open(port):
        raise RuntimeError(f'Port {port} is occupied by another process; refusing to replace it')
    with (DEPLOY / 'logs' / (name + '.log')).open('ab') as log:
        proc = subprocess.Popen([str(v) for v in command], cwd=ROOT, env=env or ENV,
                                stdin=subprocess.DEVNULL, stdout=log, stderr=log, start_new_session=True)
    fields = Path(f'/proc/{proc.pid}/stat').read_text().split(') ', 1)[1].split()
    (DEPLOY / 'run' / (name + '.json')).write_text(json.dumps({'pid': proc.pid, 'start': fields[19]}))
    for _ in range(120):
        if proc.poll() is not None:
            raise RuntimeError(f'{name} exited; see deploy/logs/{name}.log')
        ready = port_open(port)
        if ready and health:
            try:
                with urllib.request.urlopen(health, timeout=1) as response:
                    ready = response.status == 200
            except Exception:
                ready = False
        if ready:
            print(f'{name}: ready on 127.0.0.1:{port}', flush=True)
            return
        time.sleep(0.5)
    raise RuntimeError(f'{name} startup timed out; see deploy/logs/{name}.log')


def infra():
    cfg = config()
    spawn('mysql', 3306, [SYS / 'sbin/mysqld', '--no-defaults', f'--basedir={SYS}',
          f'--datadir={DEPLOY}/data/mysql', '--bind-address=127.0.0.1', '--port=3306',
          '--mysqlx=OFF', '--secure-file-priv=NULL', f'--socket={DEPLOY}/run/mysql.sock', f'--pid-file={DEPLOY}/run/mysql.pid',
          f'--log-error={DEPLOY}/logs/mysql-error.log', '--innodb-buffer-pool-size=128M'])
    spawn('redis', 6379, [SYS / 'bin/redis-server', '--bind', '127.0.0.1', '--port', '6379',
          '--dir', DEPLOY / 'data/redis', '--appendonly', 'yes', '--save', '60 1'])
    spawn('minio', 9000, [LIBS / 'minio', 'server', DEPLOY / 'data/minio', '--address',
          '127.0.0.1:9000', '--console-address', '127.0.0.1:9001'],
          {**ENV, 'MINIO_ROOT_USER': 'rpm-local', 'MINIO_ROOT_PASSWORD': cfg['minio']},
          'http://127.0.0.1:9000/minio/health/live')


def mysql(sql, database=None):
    subprocess.run([str(SYS / 'bin/mysql'), f'--defaults-file={DEPLOY}/mysql-admin.cnf',
                    '--default-character-set=utf8mb4', *([database] if database else [])],
                   input=sql, text=True, env=ENV, check=True)


def init_db():
    if (DEPLOY / 'database-initialized').exists():
        print('Database already initialized; existing data preserved.')
        return
    cfg = config()
    file = DEPLOY / 'mysql-admin.cnf'
    if not file.exists():
        private_write(file, f'[client]\nuser=root\nsocket={DEPLOY}/run/mysql.sock\n')
    mysql("CREATE DATABASE rpm CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;\n"
          f"CREATE USER 'rpm'@'127.0.0.1' IDENTIFIED BY '{cfg['mysql']}';\n"
          "GRANT ALL PRIVILEGES ON rpm.* TO 'rpm'@'127.0.0.1';")
    db = ROOT / 'backend/src/main/resources/db'
    # schema.sql already includes the older column migrations. Seeds run only on this new DB.
    for name in ['schema.sql', 'migration_backup_v1.sql', 'personnel_roster_v1.sql',
                 'seed_demo_projects.sql', 'keep_two_project_samples.sql', 'seed_declarations_5.sql',
                 'personnel_roster_v2.sql', 'migration_demo_password_employee_no_v1.sql']:
        print('Importing ' + name, flush=True)
        mysql((db / name).read_text(), 'rpm')
    # Retain historical user rows but disable the four obsolete README demo accounts.
    mysql("UPDATE sys_user SET status=0 WHERE username IN ('pm01','chief01','mgr01','fin01');", 'rpm')
    mysql(f"ALTER USER 'root'@'localhost' IDENTIFIED BY '{cfg['mysql']}';")
    private_write(file, f'[client]\nuser=root\npassword={cfg["mysql"]}\nsocket={DEPLOY}/run/mysql.sock\n')
    (DEPLOY / 'database-initialized').write_text(time.strftime('%Y-%m-%d %H:%M:%S'))


def runtime_jar():
    """Run an immutable copy: Maven must not replace a JAR still used by a JVM classloader."""
    built = ROOT / 'backend/target/rpm-backend-1.0.0.jar'
    digest = hashlib.sha256(built.read_bytes()).hexdigest()
    directory = DEPLOY / 'runtime'
    directory.mkdir(parents=True, exist_ok=True)
    image = directory / ('rpm-backend-' + digest + '.jar')
    if not image.exists():
        temporary = directory / ('.' + digest + '-' + str(os.getpid()) + '.tmp')
        shutil.copyfile(built, temporary)
        temporary.replace(image)
    return image


def start():
    infra()
    if not (DEPLOY / 'database-initialized').exists():
        raise RuntimeError('Run init-db once before starting the application')
    for migration in ['migration_change_v2.sql', 'migration_change_v3.sql']:
        mysql((ROOT / 'backend/src/main/resources/db' / migration).read_text(), 'rpm')
    cfg = config()
    app_env = {**ENV, 'SERVER_ADDRESS': '127.0.0.1', 'SERVER_PORT': '8080',
        'SPRING_DATASOURCE_URL': 'jdbc:mysql://127.0.0.1:3306/rpm?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true',
        'SPRING_DATASOURCE_USERNAME': 'rpm', 'SPRING_DATASOURCE_PASSWORD': cfg['mysql'],
        'SPRING_DATA_REDIS_HOST': '127.0.0.1', 'SPRING_DATA_REDIS_PORT': '6379',
        'RPM_JWT_SECRET': cfg['jwt'], 'RPM_JWT_EXPIRE_HOURS': '12',
        'RPM_MINIO_ENDPOINT': 'http://127.0.0.1:9000', 'RPM_MINIO_ACCESS_KEY': 'rpm-local',
        'RPM_MINIO_SECRET_KEY': cfg['minio'], 'RPM_MINIO_BUCKET': 'rpm-attachments',
        'RPM_BACKUP_DIR': str(DEPLOY / 'data/backups'),
        'MYBATIS_PLUS_GLOBAL_CONFIG_DB_CONFIG_LOGIC_DELETE_FIELD': 'deleted',
        'MYBATIS_PLUS_GLOBAL_CONFIG_DB_CONFIG_LOGIC_DELETE_VALUE': '1',
        'MYBATIS_PLUS_GLOBAL_CONFIG_DB_CONFIG_LOGIC_NOT_DELETE_VALUE': '0',
        'SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE': '50MB', 'SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE': '50MB'}
    spawn('backend', 8080, [JDK / 'bin/java', '-Xms128m', '-Xmx640m', '-Duser.timezone=Asia/Shanghai',
          '-jar', runtime_jar()], app_env, 'http://127.0.0.1:8080/api/health')
    spawn('frontend', 6006, ['node', ROOT / 'scripts/local-gateway.mjs'], health='http://127.0.0.1:6006/')


def stop():
    for name in ['frontend', 'backend', 'minio', 'redis', 'mysql']:
        pid = running(name)
        if not pid:
            continue
        os.kill(pid, signal.SIGTERM)
        for _ in range(120):
            if not running(name):
                break
            time.sleep(0.5)
        if running(name):
            raise RuntimeError(f'{name} has not shut down; inspect its log')
        print(name + ': stopped', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['infra', 'init-db', 'start', 'stop', 'restart', 'status'])
    command = parser.parse_args().command
    if command == 'status':
        for name, port in [('mysql', 3306), ('redis', 6379), ('minio', 9000), ('backend', 8080), ('frontend', 6006)]:
            print(f'{name}: pid={running(name)} port={port} listening={port_open(port)}')
    elif command == 'restart':
        stop()
        start()
    else:
        {'infra': infra, 'init-db': init_db, 'start': start, 'stop': stop}[command]()
