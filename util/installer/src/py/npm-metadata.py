import json, re, sys
with open(sys.argv[1], encoding='utf-8') as source:
    data = json.load(source)
versions = [v for v in data['versions'] if re.fullmatch(r'\d+\.\d+\.\d+', v)
            and (sys.argv[2] == 'any' or v.split('.')[0] == sys.argv[2])]
v = max(versions, key=lambda v: tuple(map(int, v.split('.'))))
print(v, data['versions'][v]['dist']['integrity'], sep='\t')
