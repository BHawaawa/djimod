from PIL import Image

im = Image.open('preview/batt_after.png').convert('RGB')
w, h = im.size
reds = []
for y in range(0, h, 2):
    for x in range(0, w, 2):
        r, g, b = im.getpixel((x, y))
        if r > 190 and g < 110 and b < 110:
            reds.append((x, y))
print('red count', len(reds))
if reds:
    xs = [p[0] for p in reds]; ys = [p[1] for p in reds]
    print('red bbox', min(xs), min(ys), max(xs), max(ys))
