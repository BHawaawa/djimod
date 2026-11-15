from PIL import Image

im = Image.open('preview/batt_after.png').convert('RGB')
# warning bar region: design x=40..700, y=72..106 -> window approx 280..850, 384..414
box = (270, 375, 900, 425)
r = im.crop(box)
px = list(r.getdata())
red = sum(1 for p in px if p[0] > 150 and p[1] < 90 and p[2] < 90)
bright = sum(1 for p in px if min(p) > 150)
print('warning-region red', red, 'bright', bright, 'total', len(px))
print('mean', r.resize((1,1)).getpixel((0,0)))
