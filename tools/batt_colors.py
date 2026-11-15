from PIL import Image

im = Image.open('preview/batt_after.png').convert('RGB')
box = (520, 480, 820, 760)
r = im.crop(box)
px = list(r.getdata())
green = sum(1 for p in px if p[1] > 120 and p[1] > p[0] + 25 and p[1] > p[2] + 15)
white = sum(1 for p in px if min(p) > 200)
yellow = sum(1 for p in px if p[0] > 170 and p[1] > 140 and p[2] < 110)
red = sum(1 for p in px if p[0] > 170 and p[1] < 110 and p[2] < 110)
print('green', green, 'white', white, 'yellow', yellow, 'red', red, 'total', len(px))
