from html.parser import HTMLParser
import re, sys
class Index(HTMLParser):
    base = False
    scripts = []
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'base': self.base = attrs.get('href') == '/grafioschtrader/'
        if tag == 'script' and attrs.get('src'): self.scripts.append(attrs['src'])
p = Index(); p.feed(open(sys.argv[1]).read())
assets = [s for s in p.scripts if re.fullmatch(r'(?:/grafioschtrader/)?[a-zA-Z0-9_.-]+\.js', s)]
if not p.base or not assets: sys.exit(2)
print(assets[0] if assets[0].startswith('/') else '/grafioschtrader/' + assets[0])
