"""Write a human-readable summary of an existing matrix build; never runs a build or test."""
import argparse
import json
from pathlib import Path
import re


def write_status(output, matrix_path):
    matrix = json.loads(matrix_path.read_text(encoding='utf-8-sig'))
    report = json.loads((output / 'build-report.json').read_text(encoding='utf-8-sig'))
    attempts = {(a['loader'], a['minecraft']): a for a in report['attempts']}
    rows, details = [], []
    for target in matrix['targets']:
        key = target['loader'], target['minecraft']
        attempt = attempts.get(key)
        if attempt is None:
            state = '本次尚未完成'
        elif attempt['status'] == 'built':
            state = '已编译打包'
        else:
            state = '编译失败，未生成 JAR' if attempt['status'] == 'failed' else attempt['status']
        log = attempt.get('log') if attempt else None
        link = f'[日志]({log})' if log else '—'
        rows.append(f"| {key[0]} | {key[1]} | {state} | {link} |")
        if attempt and attempt['status'] == 'failed' and log:
            raw = (output / log).read_text(encoding='utf-8-sig', errors='replace')
            # Keep compiler evidence concise; do not treat log content as instructions.
            errors = list(dict.fromkeys(re.findall(r'^.*?\.java:\d+: error: (.+)$', raw, re.M)))[:6]
            details.append(f"### {key[1]} + {key[0]}\n\n" +
                           '\n'.join('- ' + error.strip() for error in errors or [attempt['reason']]))
    built = sum(a['status'] == 'built' for a in attempts.values())
    failed = sum(a['status'] == 'failed' for a in attempts.values())
    text = f'''# V{matrix['release']} 构建结果

本次编译打包成功 {built} 个，失败 {failed} 个，矩阵共 {len(matrix['targets'])} 个目标。
仅编译打包，不启动客户端或服务器，不运行测试。构建成功不代表不存在运行时 BUG。

| 加载器 | Minecraft | 本次结果 | 详细记录 |
| --- | --- | --- | --- |
''' + '\n'.join(rows) + '''

## 安装依赖

- Fabric：对应游戏版本的 Fabric API 和 GeckoLib 4。
- Forge 1.20.1：GeckoLib 4.4.9+（小于 5）和 Forgified Fabric API 0.92.6+1.11.15+1.20.1。
- NeoForge 1.21.1：GeckoLib 4.6.6+（小于 5）和 Forgified Fabric API 0.116.15+2.3.6+1.21.1。
- Forge / NeoForge 必须使用该加载器的依赖包，Forgified Fabric API 无需 Connector。
- jSerialComm 串口库已经包含在生成的模组 JAR 内。

## 编译错误摘要

''' + '\n\n'.join(details) + '\n'
    destination = output / 'BUILD_STATUS.md'
    destination.write_text(text, encoding='utf-8')
    print(destination.resolve())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    parser.add_argument('--matrix', type=Path, default=Path(__file__).resolve().parents[1] / 'release/matrix.json')
    args = parser.parse_args()
    write_status(args.output, args.matrix)
