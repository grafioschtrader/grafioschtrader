import pathlib, sys
directory = pathlib.Path(sys.argv[1])
def read(path): return dict(line.split('\t', 1) for line in pathlib.Path(path).read_text().splitlines())
before, after = read(sys.argv[2]), read(sys.argv[3])
current = {p.name: str(p.readlink()) for p in directory.iterdir() if p.is_symlink()}
if current != after: sys.exit(2)
for name in after.keys() - before.keys():
    if '/' in name or name in ('.', '..'): sys.exit(2)
    (directory / name).unlink()
