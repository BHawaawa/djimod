"""Copy the detailed RC2 asset and add independently switchable indicator lenses."""
from pathlib import Path
import json, math
from PIL import Image, ImageDraw
root=Path(__file__).resolve().parents[1]
assets=root/'fabric_mod/src/main/resources/assets/djimod'
p=assets/'geo/rc2.geo.json';data=json.loads((root/'model/RC2/geo/rc2.geo.json').read_text());g=data['minecraft:geometry'][0];g['description']['texture_height']=1056
tex=Image.open(root/'model/RC2/textures/rc2.png').convert('RGBA');out=Image.new('RGBA',(2048,1056));out.paste(tex,(0,0));draw=ImageDraw.Draw(out)
for i,col in enumerate(['#62e6b1','#ff4c4c','#45484a']):draw.rectangle((i*16,1024,i*16+15,1055),fill=col)
out.save(assets/'textures/item/rc2.png')
def cube(x,y,w,h,color):
 return {'origin':[x,y,-2.575],'size':[w,h,0.02],'uv':{f:{'uv':[color*16+2,1026],'uv_size':[8,8]} for f in ['north','south','east','west','up','down']}}
def bone(name,cubes):g['bones'].append({'name':name,'parent':'root','pivot':[0,0,0],'cubes':cubes})
for i,x in enumerate([.44,-.15,-.74,-1.33]):
 for label,col in [('on',0),('off',2)]:
  cubes=[]
  for row in range(8):
   y=(row+.5)/8*.24-.12;half=math.sqrt(max(0,.12*.12-y*y))
   cubes.append(cube(-x-half,6.68+row*.03,half*2,.03,col))
  bone(f'battery_{i}_{label}',cubes)
for label,col in [('connected',0),('disconnected',1)]:bone('link_'+label,[cube(-1.58,6.68,.48,.24,col)])
p.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
