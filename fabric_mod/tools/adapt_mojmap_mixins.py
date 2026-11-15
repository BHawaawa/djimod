"""Adapt Mixin string selectors and shadow names after Mercury's Java source mapping."""
import argparse
from collections import defaultdict
from pathlib import Path
import re


def load_maps(yarn_path, mojang_path):
    classes, members = {}, []
    owner = None
    for line in yarn_path.read_text(encoding='utf-8').splitlines():
        parts = line.split('\t')
        if parts[0] == 'c':
            owner = parts[1]
            classes[owner] = parts[3]
        elif len(parts) >= 6 and parts[0] == '' and parts[1] in ('f', 'm'):
            members.append((owner, parts[1], parts[2], parts[3], parts[5]))
    official, raw_members = {}, []
    owner = None
    for line in mojang_path.read_text(encoding='utf-8').splitlines():
        if line.startswith('#') or not line:
            continue
        if not line.startswith(' '):
            name, owner = line.rstrip(':').split(' -> ')
            official[owner] = name.replace('.', '/')
        else:
            signature, obfuscated = line.strip().split(' -> ')
            signature = re.sub(r'^\d+:\d+:', '', signature)
            signature = re.sub(r':\d+(?::\d+)?$', '', signature)
            raw_members.append((owner, signature, obfuscated))
    reverse_official = {name: obf for obf, name in official.items()}

    def descriptor(typename):
        dimensions = 0
        while typename.endswith('[]'):
            dimensions += 1
            typename = typename[:-2]
        primitive = {'void': 'V', 'boolean': 'Z', 'byte': 'B', 'char': 'C', 'short': 'S',
                     'int': 'I', 'long': 'J', 'float': 'F', 'double': 'D'}
        typename = typename.replace('.', '/')
        return '[' * dimensions + (primitive[typename] if typename in primitive else
                                   'L' + reverse_official.get(typename, typename) + ';')

    methods, fields = {}, {}
    for owner, signature, obfuscated in raw_members:
        if '(' in signature:
            match = re.fullmatch(r'(\S+) (\S+)\((.*?)\)', signature)
            if not match:
                raise ValueError(f'Unrecognized mapping: {signature}')
            result, name, args = match.groups()
            desc = '(' + ''.join(descriptor(t) for t in args.split(',') if t) + ')' + descriptor(result)
            methods[owner, obfuscated, desc] = name
        else:
            _, name = signature.split(' ', 1)
            fields[owner, obfuscated] = name
    method_names, field_names, selectors = defaultdict(set), {}, {}
    yarn_to_official = {name: official[obf] for obf, name in classes.items() if obf in official}
    for owner, kind, desc, obfuscated, name in members:
        mapped = methods.get((owner, obfuscated, desc)) if kind == 'm' else fields.get((owner, obfuscated))
        if mapped is None:
            continue
        if kind == 'm':
            method_names[official[owner], name].add(mapped)
            yarn_desc = re.sub(r'L([^;]+);', lambda m: 'L' + classes.get(m[1], m[1]) + ';', desc)
            selectors[classes[owner], name + yarn_desc] = mapped
        else:
            field_names[official[owner], name] = mapped
    return yarn_to_official, method_names, field_names, selectors


def adapt(source, maps):
    classes, methods, fields, selectors = maps
    imports = {name.rsplit('.', 1)[-1]: name.replace('.', '/')
               for name in re.findall(r'^import ([\w.$]+);', source, flags=re.M)}
    mixin = re.search(r'@Mixin\((.*?)\)', source, flags=re.S)
    owner = None
    if mixin:
        literal = re.search(r'([\w.$]+)\.class', mixin[1])
        target = re.search(r'targets\s*=\s*"([\w.$]+)"', mixin[1])
        if literal:
            owner = imports.get(literal[1], literal[1].replace('.', '/'))
        elif target:
            owner = classes.get(target[1].replace('.', '/'), target[1].replace('.', '/'))

    def map_classes(text):
        return re.sub(r'L([^;]+);', lambda m: 'L' + classes.get(m[1], m[1]) + ';', text)

    def method_name(name):
        if '(' in name:
            method, desc = name.split('(', 1)
            # Exact selector lookup is in Yarn names, while the mixin owner is Mojang named.
            yarn_owner = next((key for key, value in classes.items() if value == owner), owner)
            mapped = selectors.get((yarn_owner, name))
            if not mapped:
                # Yarn can declare an inherited selector only on the parent class.
                inherited = {value for (target, selector), value in selectors.items() if selector == name}
                if len(inherited) == 1:
                    mapped = next(iter(inherited))
            if mapped:
                return mapped + map_classes('(' + desc)
            candidates = methods.get((owner, method), set())
            return (next(iter(candidates)) if len(candidates) == 1 else method) + map_classes('(' + desc)
        candidates = methods.get((owner, name), set())
        return next(iter(candidates)) if len(candidates) == 1 else name

    def map_string(match):
        value = match[1]
        if value in classes:
            value = classes[value]
        elif value.replace('.', '/') in classes:
            value = classes[value.replace('.', '/')].replace('/', '.')
        elif value.startswith('L') and ';' in value:
            class_name, member = value[1:].split(';', 1)
            mapped_owner = classes.get(class_name, class_name)
            if '(' in member:
                name, desc = member.split('(', 1)
                mapped = selectors.get((class_name, member))
                candidates = methods.get((mapped_owner, name), set())
                name = mapped or (next(iter(candidates)) if len(candidates) == 1 else name)
                member = name + map_classes('(' + desc)
            elif ':' in member:
                name, desc = member.split(':', 1)
                member = fields.get((mapped_owner, name), name) + ':' + map_classes(desc)
            value = 'L' + mapped_owner + ';' + member
        return '"' + value + '"'

    source = re.sub(r'"([^"\n]+)"', map_string, source)
    if owner:
        source = re.sub(r'(method\s*=\s*)(\{[^}]+\}|"[^"\n]+")',
                        lambda m: m[1] + re.sub(r'"([^"\n]+)"', lambda s: '"' + method_name(s[1]) + '"', m[2]), source)
        source = re.sub(r'@(Accessor|Invoker)\("([^"\n]+)"\)',
                        lambda m: '@' + m[1] + '("' + (fields.get((owner, m[2]), m[2]) if m[1] == 'Accessor'
                                                        else method_name(m[2])) + '")', source)
        # Shadow declarations are not Java overrides, so Mercury cannot resolve them.
        for name in re.findall(r'@Shadow[^;]*?\b(\w+)\s*;', source):
            mapped = fields.get((owner, name))
            if mapped and mapped != name:
                source = re.sub(r'(?<![\w./$])' + re.escape(name) + r'\b', mapped, source)
                source = re.sub(r'(?<=this\.)' + re.escape(name) + r'\b', mapped, source)
                source = re.sub(r'(?<=\)\.)' + re.escape(name) + r'\b', mapped, source)
        for name in re.findall(r'@(?:org\.spongepowered\.asm\.mixin\.)?Shadow\s+(?:protected|public|private)\s+abstract\s+\S+\s+(\w+)\(', source):
            mapped = method_name(name)
            if mapped != name:
                source = re.sub(r'\b' + re.escape(name) + r'\b', mapped, source)
    return source


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources', type=Path, required=True)
    parser.add_argument('--yarn', type=Path, required=True)
    parser.add_argument('--mojang', type=Path, required=True)
    args = parser.parse_args()
    maps = load_maps(args.yarn, args.mojang)
    changed = 0
    for path in args.sources.rglob('*.java'):
        source = path.read_text(encoding='utf-8')
        if '@Mixin' not in source:
            continue
        mapped = adapt(source, maps)
        if mapped != source:
            path.write_text(mapped, encoding='utf-8')
            changed += 1
    print(f'Adapted {changed} Mixin source files to Mojang selectors and shadows')


if __name__ == '__main__':
    main()
