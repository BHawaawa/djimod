"""Run remapped Fabric candidates in the production namespace, outside Gradle's dev launcher."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess


def single(paths, description):
    paths = [p for p in paths if not any(s in p.name for s in ('-sources', '-javadoc'))]
    if len(paths) != 1:
        raise ValueError(f'Expected one {description}, found: {paths}')
    return paths[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--minecraft', required=True)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--scenario', choices=['render', 'charging', 'rc2', 'survival', 'server'], default='render')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    matrix = json.loads((root / 'release/matrix.json').read_text(encoding='utf-8'))
    target = next(t for t in matrix['targets'] if t['loader'] == 'Fabric' and t['minecraft'] == args.minecraft)
    project = root / target['project']
    cache = Path.home() / '.gradle/caches'
    modules = cache / 'modules-2/files-2.1'
    candidate = project / 'build/qa-candidate'
    stem = f'DronesJoyrideInterface-{args.minecraft}+Fabric-V{matrix["release"]}'
    mod = candidate / f'{stem}.jar'
    qa = candidate / f'{stem}-qa.jar'
    if not mod.is_file() or not qa.is_file():
        raise FileNotFoundError(f'Build candidate first: {mod}')
    java = args.java / 'bin/java.exe' if args.java.is_dir() else args.java
    if not java.is_file():
        raise FileNotFoundError(java)
    server = args.scenario == 'server'
    run = project / ('run-port-server-qa' if server else f'run-production-{args.scenario}-qa')
    run.mkdir(exist_ok=True)
    if server and 'eula=true' not in (run / 'eula.txt').read_text(encoding='utf-8'):
        raise ValueError('Dedicated test server requires user-authorized EULA acceptance')
    mods = run / 'mods'
    mods.mkdir(exist_ok=True)
    artifacts = [mod, qa,
        single(modules.glob(f'net.fabricmc.fabric-api/fabric-api/{target["fabricApi"]}/*/*.jar'), 'Fabric API'),
        single(modules.glob(f'maven.modrinth/geckolib/{target["geckolibId"]}/*/*.jar'), 'GeckoLib')]
    allowed_names = {p.name for p in artifacts}
    extra = [p for p in mods.glob('*.jar') if p.name not in allowed_names]
    if extra:
        raise ValueError(f'Unexpected mods in isolated QA directory: {extra}')
    for artifact in artifacts:
        shutil.copy2(artifact, mods / artifact.name)
    if not server:
        world = run / 'saves/Port QA'
        if not world.exists():
            shutil.copytree(project / 'run-port-qa/saves/Port QA', world)
        shutil.copy2(project / 'run-port-qa/options.txt', run / 'options.txt')
    classpath = []
    for line in (project / 'build/port-classpath.txt').read_text(encoding='utf-8').splitlines():
        if 'modules-2' in line and 'dev-launch-injector' not in line and 'org.lwjgl' not in line:
            classpath.append(line)
    info = json.loads((cache / f'fabric-loom/{args.minecraft}/minecraft-info.json').read_text(encoding='utf-8'))
    for library in info['libraries']:
        if not library['name'].startswith('org.lwjgl:'):
            continue
        parts = library['name'].split(':')
        if len(parts) > 3 and parts[3] != 'natives-windows':
            continue
        group, artifact, version = parts[:3]
        name = f'{artifact}-{version}' + (f'-{parts[3]}' if len(parts) > 3 else '') + '.jar'
        classpath.append(str(single(modules.glob(f'{group}/{artifact}/{version}/*/{name}'), name)))
    game = single((cache / 'fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-intermediary').glob(
        f'{args.minecraft}-*/*.jar'), 'intermediary game')
    classpath.insert(0, str(game))
    flag = {'render': 'avatadrone.compatQa', 'charging': 'avatadrone.chargingQa', 'rc2': 'avatadrone.rc2Qa',
            'survival': 'djimod.survivalQa', 'server': 'djimod.portServerQa'}[args.scenario]
    command = [str(java), '-Xmx2G', '-XX:ActiveProcessorCount=4', '-Dfabric.development=false',
               f'-D{flag}=true', '-Dfile.encoding=UTF-8', '-cp', os.pathsep.join(dict.fromkeys(classpath)),
               'net.fabricmc.loader.impl.launch.knot.KnotServer' if server else 'net.fabricmc.loader.impl.launch.knot.KnotClient']
    if server:
        command += ['--nogui']
    else:
        command += ['--username', 'PortQA', '--accessToken', '0', '--userType', 'legacy', '--version', 'PortQA',
                    '--assetsDir', str(cache / 'fabric-loom/assets'), '--assetIndex', f'{args.minecraft}-{info["assetIndex"]["id"]}',
                    '--width', '960', '--height', '640', '--quickPlaySingleplayer', 'Port QA']
    log = run / 'launcher.log'
    with log.open('w', encoding='utf-8') as output:
        print(f'Running {args.minecraft} {args.scenario} production QA; log: {log}', flush=True)
        result = subprocess.run(command, cwd=run, stdout=output, stderr=subprocess.STDOUT,
                                timeout=360,
                                creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    expected = {'render': 'COMPAT_QA PASS:', 'charging': 'CHARGING_QA PASS:', 'rc2': 'RC2_QA PASS ',
                'survival': 'SURVIVAL_QA PASS:', 'server': 'PORT_SERVER_QA PASS:'}[args.scenario]
    if result.returncode or expected not in log.read_text(encoding='utf-8', errors='replace'):
        raise SystemExit(f'QA failed (exit {result.returncode} or missing completion marker). See {log}')
    print(f'PASS: {args.minecraft} {args.scenario} production QA')


if __name__ == '__main__':
    main()
