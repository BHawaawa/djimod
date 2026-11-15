import sys
sys.path.insert(0, r'tools')
import render
from PIL import Image
import numpy as np

quads, tex = render.load_geo(r'model/RC2/geo/rc2.geo.json', r'model/RC2/textures/rc2.png')
print('quads', len(quads))
# front view; custom transparent render
img = render.render(quads, tex, render.VIEWS['front'], size=1200, scale=45.0, center=(0, 0.1, 0), bg=(238, 238, 238))
img.save(r'preview\rc2_front_test.png')
print('saved')
