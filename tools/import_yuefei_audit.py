"""Read-only inventory and reversible source snapshot for the V1.0.32 import."""
import hashlib
import json
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
PROJECT = ROOT / 'fabric_mod'
WORK = PROJECT / '.cache' / 'yuefei-import'
TARGET = pathlib.Path(r'C:\Users\17082\Downloads\YueFei-MC-DJI Mod 1.20.1Fabric-V1.0.32内测版.jar')
WORK.mkdir(parents=True, exist_ok=True)
snapshot = WORK / 'project-before-import.zip'
if not snapshot.exists():
    with zipfile.ZipFile(snapshot, 'w', zipfile.ZIP_DEFLATED) as z:
        for p in (PROJECT / 'src').rglob('*'):
            if p.is_file():
                z.write(p, p.relative_to(PROJECT))
        for name in ['build.gradle', 'gradle.properties', 'README.md', 'RENDER_QA.md']:
            z.write(PROJECT / name, name)
with zipfile.ZipFile(TARGET) as z:
    z.extractall(WORK / 'original')
    for name in ['fabric.mod.json', 'avatadrone.client.mixins.json', 'avatadrone.mixins.json']:
        print(name, z.read(name).decode())
print('SHA256', hashlib.sha256(TARGET.read_bytes()).hexdigest())
