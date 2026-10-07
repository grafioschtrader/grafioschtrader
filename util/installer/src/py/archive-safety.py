import posixpath, sys, tarfile
with tarfile.open(sys.argv[1], 'r:xz') as archive:
    members = archive.getmembers(); links = {m.name.rstrip('/') for m in members if m.issym()}
    root = sys.argv[2]
    for m in members:
        name = m.name.rstrip('/')
        if name != root and not name.startswith(root + '/'): raise SystemExit('Unexpected archive root')
        if '..' in name.split('/') or name.startswith('/') or not (m.isfile() or m.isdir() or m.issym()):
            raise SystemExit('Unsafe archive member')
        parent = posixpath.dirname(name)
        while parent:
            if parent in links: raise SystemExit('Archive writes through a symlink')
            parent = posixpath.dirname(parent)
        if m.issym():
            target = posixpath.normpath(posixpath.join(posixpath.dirname(name), m.linkname))
            if m.linkname.startswith('/') or not target.startswith(root + '/'):
                raise SystemExit('Archive link escapes destination')
