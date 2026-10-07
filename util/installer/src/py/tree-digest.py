import hashlib, os, stat, sys
from pathlib import Path
root = Path(sys.argv[1]); digest = hashlib.sha256()
info = root.lstat()
if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.geteuid() or stat.S_IMODE(info.st_mode) != 0o755:
    raise SystemExit('Build tool root must be installer-owned with mode 755')
for p in sorted(root.rglob('*')):
    info = p.lstat()
    if info.st_uid != os.geteuid() or (not stat.S_ISLNK(info.st_mode) and info.st_mode & 0o022):
        raise SystemExit('Build tool files must be installer-owned and not writable by other users')
    digest.update((str(p.relative_to(root)) + ':' + str(stat.S_IMODE(info.st_mode)) + ':').encode())
    if p.is_symlink(): digest.update(os.readlink(p).encode())
    elif p.is_file(): digest.update(hashlib.sha256(p.read_bytes()).digest())
    elif not p.is_dir(): raise SystemExit('Unsupported build tool file')
print(digest.hexdigest())
