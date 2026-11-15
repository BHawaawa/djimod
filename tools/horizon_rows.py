import sys
from PIL import Image

im = Image.open(sys.argv[1]).convert('RGB')
# examine the horizon band region at full resolution
for y in range(590, 660, 4):
    row = im.crop((250, y, 1350, y + 1))
    small = row.resize((100, 1))
    px = list(small.getdata())
    mean = tuple(sum(c[i] for c in px) // len(px) for i in range(3))
    dark = sum(1 for c in px if sum(c) < 60)
    print(y, mean, 'nearblack%', round(dark * 100 / len(px)))
