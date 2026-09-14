"""Local QA SQL adapter. Credentials are read from the existing private client config."""
import sys, runpy, subprocess, os
from pathlib import Path
root=Path(__file__).resolve().parents[2]
m=runpy.run_path(str(root/'scripts/local-platform.py'))
database=os.environ.get('CHANGE_QA_DB', 'rpm')
if database not in ('rpm', 'rpm_change_lab'): raise ValueError('Unsupported QA database')
subprocess.run([str(m['SYS']/'bin/mysql'),f"--defaults-file={m['DEPLOY']}/mysql-admin.cnf",'--default-character-set=utf8mb4','--batch','--raw','--skip-column-names',database],input=sys.stdin.read(),text=True,env=m['ENV'],check=True)
