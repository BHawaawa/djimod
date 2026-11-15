import sys
from PIL import Image

path = sys.argv[1]
im = Image.open(path).convert('RGB')
w, h = im.size
print('size', w, h)

small = im.resize((80, 48))
px = list(small.getdata())
print('mean', tuple(sum(c[i] for c in px) // len(px) for i in range(3)))

# feed area: screen at ~x 246..1353, y 322..908 (window 1598x948)
x0, x1, y0, y1 = 250, 1350, 330, 900
region = im.crop((x0, y0, x1, y1))
rr = region.resize((110, 57))
pixels = list(rr.getdata())
width = 110
dark_rows = []
for row in range(57):
    vals = pixels[row * width:(row + 1) * width]
    mean = tuple(sum(c[i] for c in vals) // len(vals) for i in range(3))
    if sum(mean) < 90:  # dark row
        dark_rows.append((row, mean))
print('dark rows (sum<90):', len(dark_rows))
for r in dark_rows:
    print('  row', r[0], 'y~', y0 + r[0] * (y1 - y0) // 57, r[1])
# also check the horizon band specifically around vertical center of feed
center = im.crop((x0, y0 + (y1 - y0) // 3, x1, y0 + (y1 - y0) * 2 // 3))
cp = center.resize((110, 19))
pixels = list(cp.getdata())
print('center band rows:')
for row in range(19):
    vals = pixels[row * width:(row + 1) * width]
    mean = tuple(sum(c[i] for c in vals) // len(vals) for i in range(3))
    print('  ', mean)
