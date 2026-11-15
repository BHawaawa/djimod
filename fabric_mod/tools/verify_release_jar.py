"""Fail closed when a platform build produces a mislabeled release artifact."""
import argparse
import json
import pathlib
import zipfile


def verify(path, loader, minecraft, release):
    with zipfile.ZipFile(path) as jar:
        names = set(jar.namelist())
        if any(name.startswith('com/example/avatadrone/qa/') for name in names):
            raise ValueError('QA classes in release JAR')
        if loader != 'Fabric' and 'fabric.mod.json' in names:
            raise ValueError('Fabric metadata in a Forge/NeoForge release')
        if loader == 'Fabric':
            metadata = json.loads(jar.read('fabric.mod.json'))
            if metadata['id'] != 'djimod' or metadata['version'] != release:
                raise ValueError('Mod ID or version does not match the release matrix')
            if metadata['depends']['minecraft'] != minecraft:
                raise ValueError('Minecraft version does not match the release matrix')
            entrypoints = metadata.get('entrypoints', {})
            if not entrypoints.get('main') or not entrypoints.get('client'):
                raise ValueError('Missing main/client entrypoints')
            for entry in entrypoints['main'] + entrypoints['client']:
                if isinstance(entry, dict):
                    entry = entry['value']
                if entry.replace('.', '/') + '.class' not in names:
                    raise ValueError('Missing entrypoint class: ' + entry)
        else:
            # Parse TOML so a comment cannot satisfy a dependency/version check.
            import tomllib
            location = 'META-INF/neoforge.mods.toml' if loader == 'NeoForge' else 'META-INF/mods.toml'
            metadata = tomllib.loads(jar.read(location).decode('utf-8'))
            mod = next((mod for mod in metadata['mods'] if mod['modId'] == 'djimod'), None)
            if mod is None or mod['version'] != release:
                raise ValueError('Missing djimod or mismatched version')
            dependencies = metadata.get('dependencies', {}).get('djimod', [])
            game = next((d for d in dependencies if d['modId'] == 'minecraft'), None)
            if game is None or game['versionRange'] != '[' + minecraft + ']':
                raise ValueError('Minecraft version does not match the release matrix')
            loader_id = 'neoforge' if loader == 'NeoForge' else 'forge'
            if not any(d['modId'] == loader_id for d in dependencies):
                raise ValueError('Missing loader dependency')
            entrypoint = ('NeoForgeEntrypoint' if loader == 'NeoForge' else 'ForgeEntrypoint')
            if 'com/example/avatadrone/' + entrypoint + '.class' not in names:
                raise ValueError('Missing native loader entrypoint: ' + entrypoint)
        classes = [name for name in names if name.startswith('com/example/avatadrone/') and name.endswith('.class')]
        if not classes:
            raise ValueError('No mod classes in JAR')
        java = {'1.16.5': 8, '1.17.1': 16, '1.18.2': 17, '1.19.2': 17,
                '1.20.1': 17, '1.21.1': 21, '26.3': 25}.get(minecraft)
        if java is None:
            raise ValueError('Unknown Minecraft Java requirement; update the verifier first')
        for name in classes:
            header = jar.read(name)[:8]
            if len(header) < 8 or header[:4] != b'\xca\xfe\xba\xbe':
                raise ValueError('Invalid class file: ' + name)
            if int.from_bytes(header[6:8], 'big') > java + 44:
                raise ValueError(f'{name} requires a newer Java than {java}')
    return True


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=pathlib.Path)
    parser.add_argument('--loader', required=True, choices=['Fabric', 'Forge', 'NeoForge'])
    parser.add_argument('--minecraft', required=True)
    parser.add_argument('--release', required=True)
    args = parser.parse_args()
    verify(args.jar, args.loader, args.minecraft, args.release)
    print(f'Verified {args.loader} {args.minecraft} / djimod {args.release}: {args.jar.name}')
