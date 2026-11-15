import sys
from PIL import Image

im = Image.open(sys.argv[1]).convert('RGB')
w, h = im.size
reds = []
whites = []
for y in range(0, h, 2):
    for x in range(0, w, 2):
        r, g, b = im.getpixel((x, y))
        if r > 190 and g < 110 and b < 110:
            reds.append((x, y))
        if r > 235 and g > 235 and b > 235:
            whites.append((x, y))
print('reds', len(reds))
if reds:
    xs = [p[0] for p in reds]; ys = [p[1] for p in reds]
    print(' red bbox', min(xs), min(ys), max(xs), max(ys))
print('whites', len(whites))
if whites:
    xs = [p[0] for p in whites]; ys = [p[1] for p in whites]
    print(' white bbox', min(xs), min(ys), max(xs), max(ys))
    # cluster white points roughly by 40px grid
    from collections import Counter
    c = Counter(((x//80)*80, (y//80)*80) for x, y in whites)
    for k, v in c.most_common(12):
        print(' white cluster', k, v)
