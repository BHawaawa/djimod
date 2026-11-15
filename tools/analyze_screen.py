import sys
from PIL import Image

path = sys.argv[1]
im = Image.open(path).convert('RGB')
w, h = im.size
print('size', w, h)
# whole-frame mean
small = im.resize((80, 48))
px = list(small.getdata())
mean = tuple(sum(c[i] for c in px) // len(px) for i in range(3))
print('mean', mean)
# center horizontal strip mean (menu button area)
strip = im.crop((0, int(h * 0.30), w, int(h * 0.40)))
sp = strip.resize((80, 8))
px = list(sp.getdata())
mean2 = tuple(sum(c[i] for c in px) // len(px) for i in range(3))
print('center strip mean', mean2)
# top-left area (title screen world list vs game)
tl = im.crop((0, 0, int(w * 0.2), int(h * 0.15)))
tp = tl.resize((40, 12))
px = list(tp.getdata())
print('top-left mean', tuple(sum(c[i] for c in px) // len(px) for i in range(3)))
