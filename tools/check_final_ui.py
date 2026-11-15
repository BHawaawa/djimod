import sys
from PIL import Image

im = Image.open(sys.argv[1]).convert('RGB')
w, h = im.size
print('size', w, h)
# screen rect for 1598x948
sx, sy = 245.8, 322.0
ss = 0.865
def p(dx, dy):
    return im.getpixel((int(sx + dx * ss), int(sy + dy * ss)))
checks = {
    'topbar-left': (300, 35),
    'topbar-right': (1100, 35),
    'bottom-left': (100, 620),
    'bottom-right': (1100, 620),
    'left-takeoff': (96, 300),
    'right-record': (1192, 218),
    'warning-area': (100, 90),
}
for k, (dx, dy) in checks.items():
    print(k, p(dx, dy))
# whole frame mean
small = im.resize((64, 38))
px = list(small.getdata())
print('mean', tuple(sum(c[i] for c in px)//len(px) for i in range(3)))
