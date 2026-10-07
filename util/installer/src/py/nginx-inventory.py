import glob, hashlib, ipaddress, pathlib, re, shlex, sys
root, lan, scratch, selected_names = sys.argv[1:]
files, servers = {}, []
own = '/etc/nginx/sites-enabled/grafioschtrader'
enabled = False
def parse(path, parents=()):
    global enabled
    if path in parents or len(parents) > 32:
        raise ValueError('recursive nginx include')
    data = pathlib.Path(root + path).read_text()
    files[path] = data
    lexer = shlex.shlex(data, posix=True, punctuation_chars='{};')
    lexer.whitespace_split = True
    tokens = []
    for token in lexer:
        tokens.extend(list(token) if re.fullmatch(r'[{};]+', token) else [token])
    def block(index, nested=False):
        nonlocal tokens
        global enabled
        nodes, words = [], []
        while index < len(tokens):
            token = tokens[index]; index += 1
            if token == '}':
                if words or not nested: raise ValueError('ambiguous nginx block')
                return nodes, index
            if token == '{':
                if not words: raise ValueError('missing block directive')
                children, index = block(index, True)
                nodes.append((words, children, path)); words = []
            elif token == ';':
                if not words: raise ValueError('empty directive')
                if words[0] == 'include':
                    if len(words) != 2 or '$' in words[1]: raise ValueError('dynamic include')
                    pattern = words[1] if words[1].startswith('/') else '/etc/nginx/' + words[1]
                    if pattern == '/etc/nginx/sites-enabled/*': enabled = True
                    for match in sorted(glob.glob(root + pattern)):
                        nodes.extend(parse(match[len(root):] if root else match, parents + (path,)))
                else: nodes.append((words, None, path))
                words = []
            else: words.append(token)
        if words or nested: raise ValueError('unterminated nginx directive')
        return nodes, index
    return block(0)[0]
try:
    tree = parse('/etc/nginx/nginx.conf')
    http = [children for words, children, _ in tree if words == ['http']]
    if len(http) != 1 or not enabled: raise ValueError('standard http sites-enabled include required')
    endpoints, has80, default80 = set(), False, False
    for words, children, source in http[0]:
        if words != ['server']: continue
        if source == own or (selected_names and source in (own+'-http', own+'-domain')): continue
        names, listens = [], []
        for directive, nested, _ in children:
            if directive[0] == 'server_name': names.extend(directive[1:])
            if directive[0] == 'listen': listens.append(directive[1:])
        if lan in names: raise ValueError('LAN address already belongs to another vhost')
        if set(names).intersection(selected_names.split()): raise ValueError('Domain already belongs to another vhost')
        if not names: names = ['gt-install-default.invalid']
        if any(not re.fullmatch(r'[a-zA-Z0-9_.-]+', name) for name in names):
            raise ValueError('wildcard, regex or dynamic server_name needs manual integration')
        for listen in listens or [['80']]:
            address = listen[0]
            if address.isdigit(): address = '0.0.0.0:' + address
            host, port = address.rsplit(':', 1)
            host = host.strip('[]')
            if host == '*': host = '0.0.0.0'
            ip = ipaddress.ip_address(host)
            if not port.isdigit() or not 1 <= int(port) <= 65535: raise ValueError('invalid port')
            if any(x not in ('default_server', 'ssl', 'http2', 'ipv6only=on') for x in listen[1:]):
                raise ValueError('unsupported listen options')
            if ip.version == 4 and port == '80':
                if host != '0.0.0.0': raise ValueError('address-specific port 80 needs manual integration')
                has80 = True
                default80 |= 'default_server' in listen
            connect = (lan if ip.version == 4 else '::1') if ip.is_unspecified else host
            scheme = 'https' if 'ssl' in listen else 'http'
            for name in names: endpoints.add((connect, port, scheme, name))
            if 'default_server' in listen:
                endpoints.add((connect, port, scheme, 'gt-install-default.invalid'))
    if has80 and not default80: raise ValueError('existing port 80 needs an explicit default_server')
    pathlib.Path(scratch, 'web-endpoints').write_text(''.join('\t'.join(e)+'\n' for e in sorted(endpoints)))
    digest = hashlib.sha256()
    for path, data in sorted(files.items()): digest.update((path+'\0'+data+'\0').encode())
    print(digest.hexdigest())
except (ValueError, OSError) as error:
    print('nginx inventory: '+str(error), file=sys.stderr)
    sys.exit(2)
