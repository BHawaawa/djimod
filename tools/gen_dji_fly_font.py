"""Builds the DJI Fly style UI fonts used by the RC 2 flight interface.

Source: Noto Sans CJK SC (SIL Open Font License 1.1) Regular / Bold.
The fonts are subset to every character that appears in the mod's Java string literals plus
ASCII and common punctuation, converted from CFF to TrueType outlines (stb_truetype in
Minecraft reads glyf outlines most reliably), and given em-square line metrics so a
`"size": N` provider renders an N pixel em box.

Minecraft rasterises TTF glyphs with nearest filtering, so one provider per pixel size is
generated (assets/djimod/font/dji_r_<px>.json / dji_b_<px>.json). Rc2FlightUi picks the
provider whose size is closest to the on-screen pixel size, keeping text crisp at any GUI scale.

Usage:  python3 tools/gen_dji_fly_font.py [path/to/NotoSansCJK-Regular.ttc] [path/to/NotoSansCJK-Bold.ttc]
"""
import json
import os
import re
import sys

from fontTools.ttLib import TTCollection, TTFont, newTable
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools import subset

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'fabric_mod', 'src', 'main', 'java')
OUT = os.path.join(ROOT, 'fabric_mod', 'src', 'main', 'resources', 'assets', 'djimod', 'font')
SIZES = [8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 20, 22, 24, 26, 28, 30, 32, 35, 38, 41, 44, 48, 52, 56, 60, 65, 70]

REG = sys.argv[1] if len(sys.argv) > 1 else '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
BOLD = sys.argv[2] if len(sys.argv) > 2 else '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'


def collect_chars():
    chars = set(chr(c) for c in range(0x20, 0x7F))
    chars |= set('·×°℃％：，。、！？；（）【】《》“”‘’…—–←→↑↓↕↔′″±≤≥▲▼◀▶●○■□★☆✓✕')
    literal = re.compile(r'"((?:[^"\\]|\\.)*)"')
    for base, _, files in os.walk(SRC):
        for name in files:
            if not name.endswith('.java'):
                continue
            with open(os.path.join(base, name), encoding='utf-8') as fh:
                for m in literal.finditer(fh.read()):
                    chars |= set(m.group(1))
    return ''.join(sorted(c for c in chars if c >= ' '))


def pick_sc(path, weight):
    # Windows also supplies Noto Sans SC as a TrueType variable font.
    if path.lower().endswith('.ttf'):
        font = TTFont(path)
        if 'fvar' in font:
            from fontTools.varLib.instancer import instantiateVariableFont
            instantiateVariableFont(font, {'wght': weight}, inplace=True)
        return font
    coll = TTCollection(path)
    for font in coll.fonts:
        name = font['name'].getDebugName(1) or ''
        if 'CJK SC' in name and 'Mono' not in name:
            return font
    raise SystemExit('Noto Sans CJK SC not found in ' + path)


def cff_to_glyf(font):
    glyph_order = font.getGlyphOrder()
    glyph_set = font.getGlyphSet()
    glyphs = {}
    for name in glyph_order:
        pen = TTGlyphPen(glyph_set)
        glyph_set[name].draw(Cu2QuPen(pen, max_err=1.0, reverse_direction=True))
        glyphs[name] = pen.glyph()
    del font['CFF ']
    if 'VORG' in font:
        del font['VORG']
    glyf = newTable('glyf')
    glyf.glyphOrder = glyph_order
    glyf.glyphs = glyphs
    font['glyf'] = glyf
    font['loca'] = newTable('loca')
    maxp = font['maxp']
    maxp.tableVersion = 0x00010000
    for attr in ('maxZones', 'maxTwilightPoints', 'maxStorage', 'maxFunctionDefs', 'maxInstructionDefs',
                 'maxStackElements', 'maxSizeOfInstructions', 'maxComponentElements'):
        setattr(maxp, attr, 0 if attr != 'maxZones' else 1)
    maxp.maxComponentDepth = 0
    font['head'].indexToLocFormat = 0
    font['head'].glyphDataFormat = 0
    font.sfntVersion = '\x00\x01\x00\x00'
    glyf.compile(font)


def build(src_path, out_name, text, weight):
    font = pick_sc(src_path, weight)
    opts = subset.Options()
    opts.layout_features = []
    opts.name_IDs = ['*']
    opts.notdef_outline = True
    opts.hinting = False
    opts.desubroutinize = True
    sub = subset.Subsetter(opts)
    sub.populate(text=text)
    sub.subset(font)
    if 'CFF ' in font:
        cff_to_glyf(font)
    upm = font['head'].unitsPerEm
    # Em-square metrics: stbtt_ScaleForPixelHeight(size) then maps 1 em to `size` pixels.
    font['hhea'].ascent = int(upm * 0.88)
    font['hhea'].descent = -int(upm * 0.12)
    font['hhea'].lineGap = 0
    font['OS/2'].sTypoAscender = int(upm * 0.88)
    font['OS/2'].sTypoDescender = -int(upm * 0.12)
    font['OS/2'].usWinAscent = int(upm * 0.88)
    font['OS/2'].usWinDescent = int(upm * 0.12)
    path = os.path.join(OUT, out_name)
    font.save(path)
    print(out_name, os.path.getsize(path) // 1024, 'KB')


def main():
    os.makedirs(OUT, exist_ok=True)
    text = collect_chars()
    print('characters:', len(text))
    build(REG, 'dji_sans_regular.ttf', text, 400)
    build(BOLD, 'dji_sans_bold.ttf', text, 700)
    for weight, file in (('r', 'dji_sans_regular.ttf'), ('b', 'dji_sans_bold.ttf')):
        for px in SIZES:
            data = {'providers': [
                {'type': 'ttf', 'file': 'djimod:' + file, 'size': px, 'oversample': 1.0,
                 'shift': [0, 0], 'skip': []},
                {'type': 'reference', 'id': 'minecraft:default'}
            ]}
            with open(os.path.join(OUT, f'dji_{weight}_{px}.json'), 'w', encoding='utf-8') as fh:
                json.dump(data, fh, indent=2)


if __name__ == '__main__':
    main()
