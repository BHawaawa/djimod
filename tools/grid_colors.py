import sys
from PIL import Image

im = Image.open(sys.argv[1]).convert('RGB')
w, h = im.size
cols, rows = 16, 9
cw, ch = w // cols, h // rows
for r in range(rows):
    line = []
    for c in range(cols):
        box = im.crop((c*cw, r*ch, (c+1)*cw, (r+1)*ch)).resize((1,1))
        px = box.getpixel((0,0))
        line.append('%02x%02x%02x' % px)
    print(' '.join(line))
