"""Install pinned local build dependencies without changing system Java/PATH."""
import argparse
import hashlib
import json
import pathlib
import urllib.request
import zipfile


def install(item, destination):
    target = destination / item['name']
    if target.is_dir():
        print('Already installed:', item['name'], flush=True)
        return
    archive = destination / (item['name'] + '.zip')
    partial = archive.with_suffix('.zip.part')
    if not archive.is_file():
        print('Downloading:', item['name'], flush=True)
        request = urllib.request.Request(item['url'], headers={'User-Agent': 'DJI-build-tools'})
        with urllib.request.urlopen(request, timeout=45) as response, partial.open('wb') as out:
            while True:
                block = response.read(1024 * 1024)
                if not block:
                    break
                out.write(block)
        partial.replace(archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != item['sha256']:
        raise ValueError('Checksum mismatch: ' + str(archive))
    with zipfile.ZipFile(archive) as package:
        for entry in package.infolist():
            resolved = (destination / entry.filename).resolve()
            if not resolved.is_relative_to(destination.resolve()):
                raise ValueError('Archive entry leaves the toolchain directory')
        package.extractall(destination)
    if not target.is_dir():
        raise ValueError('Expected toolchain directory missing: ' + str(target))
    print('Verified and installed:', item['name'], flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', action='append', help='Install only this named toolchain (repeatable)')
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parents[1]
    destination = root / '.cache/toolchains'
    destination.mkdir(parents=True, exist_ok=True)
    locked = json.loads((root / 'release/toolchains.json').read_text('utf-8'))['windows-x64']
    if args.name and set(args.name) - {item['name'] for item in locked}:
        parser.error('Unknown toolchain name')
    for item in locked:
        if args.name is None or item['name'] in args.name:
            install(item, destination)
