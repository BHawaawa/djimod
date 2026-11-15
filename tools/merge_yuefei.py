"""One-shot, reproducible selective merge from CFR output; original sources stay in the snapshot."""
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1] / 'fabric_mod'
WORK = ROOT / '.cache/yuefei-import'
SRC = ROOT / 'src/main/java/com/example/avatadrone'
TARGET = WORK / 'resolved-src/com/example/avatadrone'
BACKUP = zipfile.ZipFile(WORK / 'project-before-import.zip')

def old(path):
    return BACKUP.read('src/main/java/com/example/avatadrone/' + path).decode('utf-8').replace('\r\n', '\n')

def target(path):
    value = (TARGET / path).read_text(encoding='utf-8')
    # Preserve the Java package, but migrate resource, translation and logger IDs.
    value = value.replace('"avatadrone', '"djimod').replace('.avatadrone.', '.djimod.')
    value = value.replace('com.example.djimod', 'com.example.avatadrone')
    value = re.sub(r'case Direction\.([A-Z_]+)', r'case \1', value)
    value = re.sub(r'\\u([0-9a-fA-F]{4})', lambda m: chr(int(m[1], 16)), value)
    value = re.sub(r'^/\*.*?\*/\s*', '', value, flags=re.S)
    return value

def write(path, value):
    p = SRC / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(value, encoding='utf-8')

def method_span(value, name):
    pattern = r'(?m)^    (?:@Override\s+)?(?:(?:public|private|protected|static|final|synchronized|native|abstract)\s+)+[^\n=;]*?\b' + re.escape(name) + r'\([^;]*?\)\s*\{'
    match = re.search(pattern, value)
    if not match:
        raise ValueError('Missing method ' + name)
    start = match.start()
    pos = match.end() - 1
    depth = 0
    token = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/|[{}]', re.S)
    for m in token.finditer(value, pos):
        if m[0] == '{': depth += 1
        elif m[0] == '}':
            depth -= 1
            if depth == 0: return start, m.end()
    raise ValueError(name)

def method(value, name):
    a, b = method_span(value, name)
    return value[a:b].replace('@Override ', '', 1)

def replace_method(value, name, new):
    a, b = method_span(value, name)
    return value[:a] + new + value[b:]

def restore_methods(value, previous, names):
    for name in names:
        value = replace_method(value, name, method(previous, name))
    return value

def insert_methods(value, additional):
    pos = value.rfind('}')
    return value[:pos] + '\n' + additional + '\n' + value[pos:]

def merge():
    # The composed UI has been substantially extended; keep its implementation,
    # then restore this project's keyboard and Xaero integrations below.
    for name in ['client/Rc2FlightUi.java', 'client/Rc2View.java',
                 'client/DjiGfx.java', 'client/McDjiGfx.java', 'client/SurfaceMap.java',
                 'block/NoDroneSignBlock.java',
                 'mixin/client/AssistEntityMixin.java', 'mixin/client/AssistRenderMixin.java',
                 'mixin/client/ParticleManagerMixin.java', 'mixin/client/BlockEntityRenderDispatcherMixin.java']:
        write(name, target(name))

    p = 'client/Rc2View.java'
    v = target(p)
    prev = old(p)
    v = restore_methods(v, prev, ['registerControls', 'onMouseButton', 'requestEnter',
        'enterFlightView', 'controlHeld', 'backwardKey', 'capture', 'pollGamepad'])
    v = v.replace('nearestDrone(client, 500)', 'nearestDrone(client, RC_RANGE)')
    v = v.replace('                lastModeKey = selectedMode;',
        '                lastModeKey = selectedMode;\n'
        '                tickModeCycleKey(); tickGimbalKeys(); tickAssistAuto();\n'
        '                updateVerticalClearance(); updateObstacleDirections();')
    v = v.replace('                lastModeKey = 0;',
        '                lastModeKey = 0;\n'
        '                modeCycleHeld = gimbalMaxHeld = false;\n'
        '                nextGimbalUpAt = nextGimbalDownAt = 0;')
    # Retain the target's faster arm gesture and its short neutral lock.
    v = v.replace('return DroneKeys.held(DroneKeys.MODE_MOD);',
                  'return DroneKeys.MODE_MOD.isUnbound() || DroneKeys.held(DroneKeys.MODE_MOD);')
    v = v.replace('            Rc2View.creativeOnlyHint(minecraftClient);\n            return;\n', '')
    v = re.sub(r'        if \(minecraftClient.player != null && !minecraftClient.player.isCreative\(\)\) \{\s*\}', '', v)
    # Feed size follows the window, and the assist pass uses its own aspect ratio.
    v = v.replace('            feedTargetId = target.getId();',
                  '            feedTargetId = target.getId();\n'
                  '            feedWidth = feed.textureWidth; feedHeight = feed.textureHeight;')
    v = v.replace('            renderWorld.run();\n            feed.beginWrite(true);',
                  '            renderWorld.run();\n            feed.beginWrite(true);\n'
                  '            renderAssistPass(client, target, renderWorld, tickDelta);')
    # Some variable names differ in the hand-written capture; checked by compile and QA.
    write(p, v)

    p = 'client/McDjiGfx.java'
    v = target(p)
    v = restore_methods(v, old(p), ['mapTiles'])
    write(p, v)

    p = 'client/Rc2FlightUi.java'
    v = target(p)
    # Prefer persistent Xaero tiles; use the new loaded-chunk map when unavailable.
    v = v.replace('boolean bl2 = Rc2FlightUi.drawSurface(f, f2, f3, f4, d2, d3, d);',
                  'boolean bl2 = g.mapTiles(f, f2, f3, f4, d2, d3, d) || Rc2FlightUi.drawSurface(f, f2, f3, f4, d2, d3, d);')
    # Keep the current project's directional coverage rendering.
    prev = old(p)
    map_old = method(prev, 'drawMap').replace('500 * 500', 'Rc2View.RC_RANGE * Rc2View.RC_RANGE', 1)
    map_old = map_old.replace('boolean loaded = g.mapTiles(x, y, w, h, cxw, czw, scale);',
        'boolean loaded = g.mapTiles(x, y, w, h, cxw, czw, scale) || drawSurface(x, y, w, h, cxw, czw, scale);')
    v = replace_method(v, 'drawMap', map_old)
    # Sector helper is present only in the original UI.

    write(p, v)

if __name__ == '__main__':
    merge()
