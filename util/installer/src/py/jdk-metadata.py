import json, re, sys
vendor, feature, token, path = sys.argv[1:5]
with open(path, encoding='utf-8') as source:
    data = json.load(source)
pattern = re.compile(re.escape(feature) + r'(\.\d+){0,3}\+\d+')
found = {}
for item in data if isinstance(data, list) else []:
    if not isinstance(item, dict):
        continue
    if vendor == 'temurin':
        binary = item.get('binary') or {}
        package = binary.get('package') or {}
        version = str(item.get('release_name', '')).removeprefix('jdk-')
        name = f"OpenJDK{feature}U-jdk_{token}_linux_hotspot_{version.replace('+', '_')}.tar.gz"
        link = (f"https://github.com/adoptium/temurin{feature}-binaries/releases/download/"
                f"jdk-{version.replace('+', '%2B')}/{name}")
        checksum = str(package.get('checksum', ''))
        valid = (binary.get('os') == 'linux' and binary.get('architecture') == token
                 and binary.get('image_type') == 'jdk' and binary.get('jvm_impl') == 'hotspot'
                 and package.get('name') == name and package.get('link') == link)
        algorithm, size = 'sha256', 64
    elif vendor == 'liberica':
        version = str(item.get('version', ''))
        name = f"bellsoft-jdk{version}-linux-{token}.tar.gz"
        link = f"https://github.com/bell-sw/Liberica/releases/download/{version}/{name}"
        checksum = str(item.get('sha1', ''))
        valid = (item.get('os') == 'linux' and item.get('bundleType') == 'jdk' and item.get('packageType') == 'tar.gz'
                 and item.get('GA') is True and item.get('FX') is False and str(item.get('featureVersion')) == feature
                 and item.get('filename') == name and item.get('downloadUrl') == link)
        algorithm, size = 'sha1', 40
    else:
        raise SystemExit('Unknown JDK vendor')
    if valid and pattern.fullmatch(version) and re.fullmatch(f'[a-f0-9]{{{size}}}', checksum):
        found[version] = (name, f'{algorithm}:{checksum}')
if not found:
    raise SystemExit('No verified JDK archive')


def order(version):
    release, build = version.split('+')
    parts = [int(part) for part in release.split('.')]
    return parts + [0] * (4 - len(parts)) + [int(build)]


version = max(found, key=order)
print(version, *found[version], sep='\t')
