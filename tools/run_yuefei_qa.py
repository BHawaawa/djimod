"""Launch the remapped release and isolated import QA mod (never the decompiled JAR)."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess

project = Path(__file__).resolve().parents[1] / 'fabric_mod'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('profile', choices=['vanilla', 'sodium', 'iris', 'shaders'])
parser.add_argument('--no-xaero', action='store_true')
args = parser.parse_args()
run = project / ('run-v201-qa-' + args.profile + ('-no-xaero' if args.no_xaero else ''))
mods = run / 'mods'
mods.mkdir(parents=True, exist_ok=True)
world = run / 'saves' / 'Flight Map QA'
if not world.exists():
    shutil.copytree(project / 'run-flight-map-qa/saves/Flight Map QA', world)
options = (project / 'run-flight-map-qa/options.txt').read_text(encoding='utf-8')
options = options.replace('enableVsync:true', 'enableVsync:false').replace('mipmapLevels:4', 'mipmapLevels:0')
(run / 'options.txt').write_text(options, encoding='utf-8')
release = next(line.split('=', 1)[1] for line in (project / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
for src, dst in [(f'dji-avata-drone-{release}.jar', 'djimod.jar'), (f'dji-avata-drone-{release}-qa.jar', 'import-qa.jar')]:
    shutil.copy2(project / 'build/libs' / src, mods / dst)
cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
patterns = ['net.fabricmc.fabric-api/fabric-api/0.92.2+1.20.1/*/*.jar',
            'software.bernie.geckolib/geckolib-fabric-1.20.1/4.4.9/*/*.jar']
if not args.no_xaero:
    patterns.append('maven.modrinth/xaeros-world-map/fabric-1.20.1-1.47.0/*/*.jar')
for pattern in patterns:
    src = next(p for p in cache.glob(pattern) if '-sources' not in p.name)
    shutil.copy2(src, mods / src.name)
if args.profile != 'vanilla':
    for pattern in ['sodium-*.jar', 'lithium-*.jar']:
        src = next((project / '.cache/compat-mods').glob(pattern))
        shutil.copy2(src, mods / src.name)
if args.profile in ['iris', 'shaders']:
    src = next((project / '.cache/compat-mods').glob('iris-*.jar'))
    shutil.copy2(src, mods / src.name)
    (run / 'config').mkdir(exist_ok=True)
    if args.profile == 'shaders':
        pack = next((project / '.cache/compat-mods').glob('MakeUp*.zip'))
        (run / 'shaderpacks').mkdir(exist_ok=True)
        shutil.copy2(pack, run / 'shaderpacks' / pack.name)
        iris_config = 'enableShaders=true\nshaderPack=' + pack.name + '\n'
    else:
        iris_config = 'enableShaders=false\n'
    (run / 'config/iris.properties').write_text(iris_config, encoding='utf-8')
classpath = [p for p in (project / 'build/compat-classpath.txt').read_text().splitlines()
             if 'modules-2' in p and 'dev-launch-injector' not in p and 'org.lwjgl' not in p
             and 'maven.modrinth' not in p]
for p in cache.glob('org.lwjgl/*/3.3.1/*/*.jar'):
    if 'sources' not in p.name and 'javadoc' not in p.name and ('natives' not in p.name or p.name.endswith('natives-windows.jar')):
        classpath.append(str(p))
game = next((Path.home() / '.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-intermediary').glob('1.20.1-*/*.jar'))
classpath.insert(0, str(game))
java = shutil.which('java')
# Prefer the game's tested Java 17 runtime when available on this Windows host.
jdk17 = Path(r'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe')
if jdk17.exists(): java = str(jdk17)
command = [java, '-Xmx1800M', '-Dfabric.development=false', '-Davatadrone.flightMapQa=true',
           '-Dfile.encoding=UTF-8', '-cp', os.pathsep.join(classpath),
           'net.fabricmc.loader.impl.launch.knot.KnotClient', '--username', 'ImportQA', '--accessToken', '0',
           '--userType', 'legacy', '--version', 'ImportQA', '--assetsDir',
           str(Path.home() / '.gradle/caches/fabric-loom/assets'), '--assetIndex', '1.20.1-5',
           '--width', '1100', '--height', '700', '--quickPlaySingleplayer', 'Flight Map QA']
with (run / 'launcher.log').open('w', encoding='utf-8') as log:
    process = subprocess.Popen(command, cwd=run, stdout=log, stderr=subprocess.STDOUT,
                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
print('Started', args.profile, 'PID', process.pid, 'log:', run / 'launcher.log')
