import glob, pathlib, re, sys
# Finds the one HTTPS server block that serves the domain, where the snippet include may be inserted.
# Prints: file, line after which to insert, certificate, key, state (ready or included).
kind, root, domain, snippet, dump = sys.argv[1:6]
ROUTES = ('/api', '/m2m', '/socket', '/ws', '/grafioschtrader')


def fail(message):
    print('vhost include: ' + message, file=sys.stderr)
    sys.exit(2)


def read(path):
    try:
        return pathlib.Path(root + path).read_text()
    except (OSError, UnicodeDecodeError):
        fail('cannot read ' + path)


def tokens(text):
    """nginx tokens with line numbers; quotes and comments follow nginx syntax."""
    result, index, line = [], 0, 1
    while index < len(text):
        char = text[index]
        if char == '\n':
            line += 1; index += 1
        elif char.isspace():
            index += 1
        elif char == '#':
            while index < len(text) and text[index] != '\n': index += 1
        elif char in '{};':
            result.append((char, line)); index += 1
        elif char in '"\'':
            end = index + 1
            while end < len(text) and text[end] != char:
                end += 2 if text[end] == '\\' else 1
            if end >= len(text): fail('unterminated quote')
            result.append((text[index + 1:end], line)); line += text[index:end].count('\n'); index = end + 1
        else:
            end = index
            while end < len(text) and not text[end].isspace() and text[end] not in '{};#"\'': end += 1
            result.append((text[index:end], line)); index = end
    return result


def blocks(items, start=0, nested=False):
    nodes, words = [], []
    index = start
    while index < len(items):
        token, line = items[index]; index += 1
        if token == '}':
            if words or not nested: fail('ambiguous nginx block')
            return nodes, index
        if token == '{':
            if not words: fail('block without directive')
            children, end = blocks(items, index, True)
            nodes.append({'words': [w for w, _ in words], 'line': words[0][1], 'open': line, 'children': children})
            index = end; words = []
        elif token == ';':
            if not words: fail('empty directive')
            nodes.append({'words': [w for w, _ in words], 'line': words[0][1], 'children': None}); words = []
        else:
            words.append((token, line))
    if words or nested: fail('unterminated nginx directive')
    return nodes, index


def nginx_includes(node, seen):
    """Directives of a block, with plain includes resolved; dynamic includes are not evaluable."""
    result = []
    for child in node:
        if child['children'] is None and child['words'][0] == 'include':
            if len(child['words']) != 2 or '$' in child['words'][1]: fail('dynamic include in the server block')
            pattern = child['words'][1]
            pattern = pattern if pattern.startswith('/') else '/etc/nginx/' + pattern
            result.append(child)
            if pattern == snippet: continue
            for match in sorted(glob.glob(root + pattern)):
                path = match[len(root):] if root else match
                if path in seen: fail('recursive include')
                result.extend(nginx_includes(blocks(tokens(read(path)))[0], seen | {path}))
        else:
            result.append(child)
    return result


def conflicting(path):
    return any(path == route or path.startswith(route) for route in ROUTES)


def nginx():
    files = sorted(glob.glob(root + '/etc/nginx/sites-enabled/*') + glob.glob(root + '/etc/nginx/conf.d/*.conf'))
    https, ssl_names = [], set()
    for match in files:
        path = str(pathlib.Path(match).resolve())
        path = path[len(root):] if root and path.startswith(root) else path
        text = read(path)
        items = tokens(text)
        for node in blocks(items)[0]:
            if node['words'] != ['server'] or node['children'] is None: continue
            directives = nginx_includes(node['children'], {path})
            names = [w for d in directives if d['words'][0] == 'server_name' for w in d['words'][1:]]
            if domain not in names: continue
            listens = [d['words'][1:] for d in directives if d['words'][0] == 'listen']
            secure = any('ssl' in listen or listen[:1] == ['443'] for listen in listens)
            if not secure: continue
            https.append((path, node, directives, items))
    if len(https) != 1: fail(f'expected exactly one HTTPS server block for {domain}, found {len(https)}')
    path, node, directives, items = https[0]
    following = [token for token, line in items if line == node['open']]
    if following[-1:] != ['{'] or following.count('{') != 1: fail('the server block must open with "{" ending its line')
    included = sum(1 for d in node['children'] if d['words'] == ['include', snippet])
    if included > 1: fail('the snippet is included more than once')
    for directive in directives:
        if directive['words'][0] != 'location' or directive['children'] is None: continue
        arguments = directive['words'][1:]
        if arguments[0] in ('~', '~*'): continue
        if conflicting(arguments[-1]):
            fail('existing location ' + ' '.join(arguments) + ' overlaps a Grafioschtrader route')
    cert = next((d['words'][1] for d in directives if d['words'][0] == 'ssl_certificate'), '')
    key = next((d['words'][1] for d in directives if d['words'][0] == 'ssl_certificate_key'), '')
    print(path, node['open'], cert, key, 'included' if included else 'ready', sep='\t')


def apache():
    entries, binding, current = set(), None, None
    for line in pathlib.Path(dump).read_text().splitlines():
        match = re.match(r'^(\S+:\d+)\s+is a NameVirtualHost', line)
        if match: binding = match[1]; continue
        match = re.match(r'^\s*(?:(\S+:\d+)\s+|(?:port (\d+) namevhost|default server)\s+)'
                         r'(\S+)\s+\((/[^()]+):(\d+)\)', line)
        if match:
            bind = match[1] or binding or ''
            current = (bind, match[4], int(match[5]))
            if match[3] == domain and bind.endswith(':443'): entries.add(current[1:])
            continue
        match = re.match(r'^\s+alias (\S+)', line)
        if match and current and match[1] == domain and current[0].endswith(':443'): entries.add(current[1:])
    if len(entries) != 1: fail(f'expected exactly one HTTPS virtual host for {domain}, found {len(entries)}')
    path, start = entries.pop()
    # The dump names the sites-enabled link; the include goes into the file it points to.
    path = str(pathlib.Path(root + path).resolve())
    path = path[len(root):] if root and path.startswith(root) else path
    lines = read(path).splitlines()
    if not lines[start - 1].lstrip().lower().startswith('<virtualhost'): fail('virtual host start not found')
    end = next((i for i in range(start, len(lines)) if lines[i].strip().lower() == '</virtualhost>'), None)
    if end is None: fail('virtual host end not found')
    body = list(lines[start:end])
    included, cert, key, index = 0, '', '', 0
    while index < len(body):
        words = body[index].split(); index += 1
        if not words or words[0].startswith('#'): continue
        directive = words[0].lower()
        if directive in ('include', 'includeoptional'):
            if len(words) != 2 or '$' in words[1]: fail('dynamic include in the virtual host')
            if words[1] == snippet: included += 1; continue
            for match in sorted(glob.glob(root + words[1])):
                body.extend(pathlib.Path(match).read_text().splitlines())
            continue
        if directive == 'rewriterule': fail('RewriteRule in the virtual host needs manual integration')
        if directive in ('proxypass', 'alias', 'scriptalias', 'redirect', '<location') and len(words) > 1:
            if conflicting(words[1].rstrip('>').strip('"')):
                fail('existing ' + words[0] + ' ' + words[1] + ' overlaps a Grafioschtrader route')
        if directive == 'sslcertificatefile' and len(words) > 1: cert = words[1].strip('"')
        if directive == 'sslcertificatekeyfile' and len(words) > 1: key = words[1].strip('"')
    if included > 1: fail('the snippet is included more than once')
    print(path, start, cert, key, 'included' if included else 'ready', sep='\t')


nginx() if kind == 'nginx' else apache()
