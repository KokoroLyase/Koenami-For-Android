"""Scan current tracked files and every reachable historical Git blob without printing secrets."""
import json
import pathlib
import re
import subprocess

root = pathlib.Path(__file__).resolve().parents[1]
def git(*args):
    return subprocess.check_output(['git', *args], cwd=root)

patterns = {
    'github-token': re.compile(rb'gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{30,}'),
    'private-key': re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----'),
    'device-serial': re.compile(rb'adb-[a-zA-Z0-9]+-[a-zA-Z0-9]+\._adb-tls'),
    'user-local-path': re.compile(rb'[A-Za-z]:[\\/]Users[\\/][A-Za-z0-9_.-]+[\\/]'),
}
private_paths = re.compile(r'(^|/)(\.local|\.toolchain|\.env)(/|$)|\.(jks|keystore|p12|pfx|dpapi|apk|aab|wav|mp3|flac|tar|zip)$', re.I)
objects = {}
for line in git('rev-list','--objects','--all').decode('utf-8').splitlines():
    sha, _, name = line.partition(' ')
    if name:
        objects[sha] = name
findings = []
def inspect(name, data, where):
    if private_paths.search(name):
        findings.append({'path': name, 'location': where, 'rule': 'private-file'})
    if b'\0' not in data:
        for label, pattern in patterns.items():
            if pattern.search(data):
                findings.append({'path': name, 'location': where, 'rule': label})
for sha, name in objects.items():
    if git('cat-file','-t',sha).strip() == b'blob':
        inspect(name, git('cat-file','blob',sha), 'history')
for name in git('ls-files').decode('utf-8').splitlines():
    path = root / name
    if path.is_file():
        inspect(name, path.read_bytes(), 'working-tree')
print(json.dumps({'historicalObjects':len(objects),'findings':findings},ensure_ascii=False,indent=2))
raise SystemExit(1 if findings else 0)
