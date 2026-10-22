"""Build redistributable notices from the resolved runtime artifacts and their cached POMs.
Run Gradle writeRuntimeInventory first. Never includes test/build-only dependencies.
"""
from pathlib import Path
import io
import json
import zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
cache = root / '.toolchain/gradle-home/caches/modules-2/files-2.1'
items = []
notices = []
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
def pom_licenses(group, name, version, visited=None):
    visited = set() if visited is None else visited
    coordinate = f'{group}:{name}:{version}'
    if coordinate in visited:
        raise RuntimeError(f'POM parent cycle: {coordinate}')
    visited.add(coordinate)
    poms = list((cache / group / name / version).glob('*/*.pom'))
    if not poms:
        raise RuntimeError(f'Missing POM: {coordinate}')
    pom = ET.parse(poms[0]).getroot()
    licenses = [{'name': x.findtext('m:name', '', ns), 'url': x.findtext('m:url', '', ns)}
                for x in pom.findall('m:licenses/m:license', ns)]
    parent = pom.find('m:parent', ns)
    if not licenses and parent is not None:
        return pom_licenses(*(parent.findtext('m:'+x, '', ns) for x in ('groupId','artifactId','version')), visited)
    return licenses

for line in (root / 'app/build/runtime-inventory.tsv').read_text(encoding='utf-8').splitlines():
    coordinate, artifact = line.split('\t', 1)
    group, name, version = coordinate.split(':')
    licenses = pom_licenses(group, name, version)
    if not licenses:
        raise RuntimeError(f'License needs manual review: {coordinate}')
    items.append({'coordinate': coordinate, 'licenses': licenses})
    with zipfile.ZipFile(artifact) as z:
        archives = [(coordinate, z)]
        if 'classes.jar' in z.namelist():
            archives.append((coordinate + '/classes.jar', zipfile.ZipFile(io.BytesIO(z.read('classes.jar')))))
        for label, archive in archives:
            for filename in archive.namelist():
                base = filename.rsplit('/', 1)[-1].upper()
                if not filename.endswith('/') and base.startswith(('LICENSE', 'NOTICE', 'COPYING')):
                    notices.append(f'--- {label}: {filename} ---\n' + archive.read(filename).decode('utf-8', errors='replace'))

intro = '''Koenami for Android / Koenami
Independent Android client by Isomeria. Not affiliated with or endorsed by Koenami.
Website, reference audio and user recordings retain their own rights. No official audio is bundled.
Launcher artwork was generated using OpenAI imagegen without third-party image inputs; editable themed vector is included.

Resolved runtime dependencies (test/build tooling excluded):
'''
listing = '\n'.join(x['coordinate'] + ' — ' + '; '.join(y['name'] + ' ' + y['url'] for y in x['licenses']) for x in items)
texts = [intro, listing, '\nCLIENT MIT LICENSE\n', (root/'LICENSE').read_text(encoding='utf-8'),
         '\nUPSTREAM KOENAMI MIT LICENSE\n', (root/'LICENSES/MIT-Koenami.txt').read_text(encoding='utf-8'),
         '\nAPACHE LICENSE 2.0\n', (root/'LICENSES/Apache-2.0.txt').read_text(encoding='utf-8'),
         '\nARTIFACT LICENSES AND NOTICES\n', '\n\n'.join(dict.fromkeys(notices))]
text = '\n'.join(texts).rstrip() + '\n'
(root/'THIRD_PARTY_NOTICES.txt').write_text(text, encoding='utf-8')
(root/'app/src/main/assets/THIRD_PARTY_NOTICES.txt').write_text(text, encoding='utf-8')
(root/'docs/runtime-dependencies.json').write_text(json.dumps(items, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print(f'{len(items)} runtime artifacts; {len(notices)} embedded notice documents; {len(text)} characters')
