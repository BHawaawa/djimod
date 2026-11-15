import sys
from PIL import Image

a = Image.open('preview/batt_before.png').convert('RGB')
b = Image.open('preview/batt_after.png').convert('RGB')
print('sizes', a.size, b.size)


def mean(im, box):
    r = im.crop(box).resize((1, 1)).getpixel((0, 0))
    return r


# center region where the battery panel should appear
box = (500, 400, 1100, 830)
print('center before', mean(a, box))
print('center after ', mean(b, box))
# count differing pixels
diff = 0
for y in range(300, 900, 4):
    for x in range(300, 1400, 4):
        if a.getpixel((x, y)) != b.getpixel((x, y)):
            diff += 1
print('differing samples', diff)
