import hashlib, ipaddress, pathlib, re, subprocess, sys
scratch, lan, names = sys.argv[1:]
selected = {lan, *names.split()}
owned = {'/etc/apache2/sites-enabled/grafioschtrader'+s+'.conf' for s in ('', '-http', '-domain')}
endpoints, binding, current = set(), None, None
stock_marker = pathlib.Path(scratch, 'apache-stock-default')
stock_marker.unlink(missing_ok=True)
def endpoint(bind, name, source, default=False):
    if source in owned: return
    stock = False
    try: implicit_address = bool(ipaddress.ip_address(name))
    except ValueError: implicit_address = False
    if implicit_address and source == '/etc/apache2/sites-enabled/000-default.conf':
        path = pathlib.Path(source).resolve()
        data = path.read_bytes()
        conffiles = subprocess.check_output(['dpkg-query', '-W', '-f=${Conffiles}', 'apache2'], text=True)
        stock = (str(path)+' '+hashlib.md5(data).hexdigest()) in conffiles and not re.search(rb'(?mi)^\s*Server(?:Name|Alias)\b', data)
        foreign = {str(p) for p in pathlib.Path('/etc/apache2/sites-enabled').glob('*.conf')} - owned
        stock = stock and foreign == {source}
        if stock:
            stock_marker.write_text('stock\n')
            return
    if name in selected and not stock: raise ValueError('Selected name already belongs to an Apache vhost')
    if not re.fullmatch(r'[a-zA-Z0-9_.-]+', name): raise ValueError('Unsupported Apache server name')
    host, port = bind.rsplit(':', 1)
    if host not in ('*', '0.0.0.0', '[::]'): raise ValueError('Address-specific Apache vhost requires manual integration')
    if not port.isdigit(): raise ValueError('Unsupported Apache port')
    # The stage supports the conventional HTTP/HTTPS ports on foreign sites.
    if port not in ('80', '443'): raise ValueError('Foreign nonstandard Apache port requires manual integration')
    scheme = 'https' if port == '443' else 'http'
    if not stock: endpoints.add((lan, port, scheme, name))
    if default: endpoints.add((lan, port, scheme, 'gt-install-default.invalid'))
try:
    for line in pathlib.Path(scratch, 'apache-vhosts').read_text().splitlines():
        match = re.match(r'^(\S+:\d+)\s+is a NameVirtualHost', line)
        if match: binding = match[1]; continue
        match = re.match(r'^\s*(?:(\S+:\d+)\s+|(?:port (\d+) namevhost|default server)\s+)(\S+)\s+\((/[^()]+):(\d+)\)', line)
        if match:
            bind = match[1] or binding
            if not bind: raise ValueError('Missing Apache binding')
            name, source = match.group(3), match.group(4); current = (bind, source)
            endpoint(bind, name, source, 'default server' in line or bool(match[1]))
            continue
        match = re.match(r'^\s+alias (\S+)', line)
        if match:
            if not current: raise ValueError('Unassigned Apache alias')
            endpoint(current[0], match[1], current[1]); continue
        if line.strip() and line.strip() != 'VirtualHost configuration:':
            raise ValueError('Unrecognized Apache vhost dump')
    paths = []
    for line in pathlib.Path(scratch, 'apache-includes').read_text().splitlines():
        match = re.match(r'^\s*\([^)]*\)\s+(/.+)$', line)
        if match: paths.append(match[1])
        elif line.strip() != 'Included configuration files:': raise ValueError('Unrecognized Apache include dump')
    if '/etc/apache2/apache2.conf' not in paths: raise ValueError('Distribution Apache configuration required')
    digest = hashlib.sha256()
    for path in sorted(set(paths)):
        digest.update(path.encode()+b'\0'+pathlib.Path(path).read_bytes()+b'\0')
    pathlib.Path(scratch, 'web-endpoints').write_text(''.join('\t'.join(e)+'\n' for e in sorted(endpoints)))
    print(digest.hexdigest())
except (OSError, ValueError) as error:
    print('Apache inventory: '+str(error), file=sys.stderr); sys.exit(2)
