import sys
sys.path.insert(0, r'tools')
import render
import numpy as np
from PIL import Image

quads, tex = render.load_geo(r'model/RC2/geo/rc2.geo.json', r'model/RC2/textures/rc2.png')
# Keep only stick cubes for visual test: render full scene but crop around knobs
for name, view in [('stick_top', render.view(180, 30)), ('stick_side', render.VIEWS['front'])]:
    img = render.render(quads, tex, view, size=1200, scale=45.0, center=(0, 0.1, 0), bg=(238, 238, 238))
    img.crop((250, 180, 450, 350)).resize((400, 340)).save(fr'preview\rc2_{name}.png')
print('ok')
