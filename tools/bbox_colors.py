import sys
from PIL import Image

im = Image.open(sys.argv[1]).convert('RGB')
w, h = im.size
minx, miny, maxx, maxy = w, h, 0, 0
count = 0
for y in range(0, h, 2):
    for x in range(0, w, 2):
        r, g, b = im.getpixel((x, y))
        if b > 180 and b > r + 25 and g > 120:
            minx = min(minx, x); maxx = max(maxx, x)
            miny = min(miny, y); maxy = max(maxy, y)
            count += 1
print('bright blue bbox', minx, miny, maxx, maxy, 'count', count)
# green bbox
minx, miny, maxx, maxy = w, h, 0, 0
count = 0
for y in range(0, h, 2):
    for x in range(0, w, 2):
        r, g, b = im.getpixel((x, y))
        if g > 80 and g > b + 15 and g > r + 10:
            minx = min(minx, x); maxx = max(maxx, x)
            miny = min(miny, y); maxy = max(maxy, y)
            count += 1
print('green bbox', minx, miny, maxx, maxy, 'count', count)
