"""Generate GeckoLib 4 steel lattice pillar (triangular truss, 1x1x1 block) -> model/block/pillar/"""
import json,math,os,shutil,numpy as np
ROOT=os.path.join(os.path.dirname(os.path.abspath(__file__)),"..","model","block","pillar")
for d in("geo","textures","animations","preview","reference_code","blockstates","models"):os.makedirs(os.path.join(ROOT,d),exist_ok=True)
S2=math.sqrt(2)
LEGS=[(1,5),(15,5),(8,12)]           # Java block space (x,z), 0..16
# parts: (name, center(x,y,z) java space, length, w, h, yaw, roll)  bar along local X
parts=[]
for i,(x,z) in enumerate(LEGS): parts.append((f"leg{i}",(x,8,z),16,2,2,0,0,"vertical"))
faces=[(LEGS[0],LEGS[1]),(LEGS[0],LEGS[2]),(LEGS[1],LEGS[2])]
for k,(p,q) in enumerate(faces):
    dx,dz=q[0]-p[0],q[1]-p[1];span=math.hypot(dx,dz)
    yaw=math.degrees(math.atan2(-dz,dx))           # R_y(yaw)*(1,0,0)=dir
    mx,mz=(p[0]+q[0])/2,(p[1]+q[1])/2
    for y in(0.5,8,15.5): parts.append((f"ring{k}_{int(y)}",(mx,y,mz),span,1,1,yaw,0,None))
    roll=45 if k!=1 else -45                      # alternate lean for visual twist
    parts.append((f"diag{k}",(mx,8,mz),span*S2-1,1,1,yaw,roll,None))  # true 45deg single brace
TEX=32
bones=[{"name":"pillar","pivot":[0,0,0]}]
def uvf(a,b):return {"uv":[0,0],"uv_size":[min(a,16),min(b,16)]}
for n,(cx,cy,cz),L,w,h,yaw,roll,kind in parts:
    if kind=="vertical": sx,sy,sz=w,L,w
    else: sx,sy,sz=L,h,w
    gx,gy,gz=-(cx-8),cy,cz-8                       # Java block -> geo (x mirrored, centred)
    cube={"origin":[round(gx-sx/2,4),round(gy-sy/2,4),round(gz-sz/2,4)],"size":[round(sx,4),sy,sz],
          "uv":{"north":uvf(sx,sy),"south":uvf(sx,sy),"east":uvf(sz,sy),"west":uvf(sz,sy),"up":uvf(sx,sz),"down":uvf(sx,sz)}}
    if yaw or roll:
        bones.append({"name":n+"_yaw","parent":"pillar","pivot":[gx,gy,gz],"rotation":[0,round(-yaw,4),0]})
        bones.append({"name":n,"parent":n+"_yaw","pivot":[gx,gy,gz],"rotation":[0,0,roll],"cubes":[cube]})
    else: bones.append({"name":n,"parent":"pillar","pivot":[gx,gy,gz],"cubes":[cube]})
geo={"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"geometry.steel_pillar","texture_width":16,"texture_height":16,
     "visible_bounds_width":2,"visible_bounds_height":2,"visible_bounds_offset":[0,0.5,0]},"bones":bones}]}
json.dump(geo,open(f"{ROOT}/geo/steel_pillar.geo.json","w"),indent=1)
json.dump({"format_version":"1.8.0","animations":{"animation.steel_pillar.idle":{"loop":True,"animation_length":1,"bones":{}}}},open(f"{ROOT}/animations/steel_pillar.animation.json","w"),indent=1)
src=os.path.join(ROOT,"..","..","..","textures","block","pillar","steel.png")
shutil.copy(src,f"{ROOT}/textures/steel_pillar.png")
json.dump({"variants":{"":{"model":"djimod:block/steel_pillar"}}},open(f"{ROOT}/blockstates/steel_pillar.json","w"),indent=1)
json.dump({"textures":{"particle":"djimod:block/steel_pillar"}},open(f"{ROOT}/models/steel_pillar_block.json","w"),indent=1)
# preview: rebuild world geometry from parts (same math GeckoLib applies)
import matplotlib;matplotlib.use("Agg");import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
def Ry(t):c,s=math.cos(t),math.sin(t);return np.array([[c,0,s],[0,1,0],[-s,0,c]])
def Rz(t):c,s=math.cos(t),math.sin(t);return np.array([[c,-s,0],[s,c,0],[0,0,1]])
F=[[0,1,3,2],[4,5,7,6],[0,1,5,4],[2,3,7,6],[0,2,6,4],[1,3,7,5]]
for view,az in(("front",-75),("side",15)):
    fig=plt.figure(figsize=(6,10));ax=fig.add_subplot(projection='3d')
    for lvl in range(3):
        for n,c,L,w,h,yaw,roll,kind in parts:
            s=(w,L,w) if kind else (L,h,w)
            v=np.array([[a*s[0]/2,b*s[1]/2,d*s[2]/2] for a in(-1,1) for b in(-1,1) for d in(-1,1)])
            v=(Ry(math.radians(yaw))@Rz(math.radians(roll))@v.T).T+np.array(c)+[0,lvl*16,0]
            ax.add_collection3d(Poly3DCollection([[v[i][[0,2,1]] for i in f] for f in F],facecolor=(.62,.65,.69),edgecolor=(.3,.32,.35),linewidth=.3))
    ax.set_xlim(0,16);ax.set_ylim(0,16);ax.set_zlim(0,48);ax.set_box_aspect((1,1,3));ax.view_init(15,az);ax.axis('off')
    plt.savefig(f"{ROOT}/preview/geo_{view}.png",dpi=120,bbox_inches='tight');plt.close()
print(len(bones),"bones")
