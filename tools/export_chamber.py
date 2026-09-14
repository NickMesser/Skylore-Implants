import json,pathlib,shutil
ROOT=pathlib.Path(__file__).resolve().parent.parent
R=ROOT/'common/src/main/resources'
SOURCE=ROOT/'art/modification_chamber'
def put(path,data):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
b=json.loads((SOURCE/'modification_chamber.bbmodel').read_text())
groups={g['uuid']:g['name'] for g in b['groups']}
slides={}
anim=next(a for a in b['animations'] if a['name'].endswith('.opened'))
for key,track in anim['animators'].items():
    frames=[f for f in track.get('keyframes',[]) if f['channel']=='position']
    if frames: slides[key]=[float(frames[-1]['data_points'][0][axis])/16 for axis in 'xyz']
element_slides={}
def walk(node,slide=None):
    if isinstance(node,str):element_slides[node]=slide or [0,0,0];return
    for child in node['children']:walk(child,slides.get(node['uuid'],slide))
for n in b['outliner']:walk(n)
def fy(y):
    # Keep a 29.5-unit player cavity; compress decorative roof rather than the player.
    if y<=1.15:return y/1.15
    if y<=35.7:return 1+(y-1.15)*29.5/(35.7-1.15)
    return 30.5+(y-35.7)*1.5/4.3
geometry=[];elements=[]
for e in b['elements']:
    if not e.get('export',True):continue
    assert not any(e.get('rotation',[0,0,0])),e['name']
    x0,y0,z0=e['from'];x1,y1,z1=e['to'];y0=fy(y0);y1=fy(y1)
    faces={
      'north':([[x1,y0,z0],[x0,y0,z0],[x0,y1,z0],[x1,y1,z0]],[0,0,-1]),
      'south':([[x0,y0,z1],[x1,y0,z1],[x1,y1,z1],[x0,y1,z1]],[0,0,1]),
      'west':([[x0,y0,z0],[x0,y0,z1],[x0,y1,z1],[x0,y1,z0]],[-1,0,0]),
      'east':([[x1,y0,z1],[x1,y0,z0],[x1,y1,z0],[x1,y1,z1]],[1,0,0]),
      'up':([[x0,y1,z1],[x1,y1,z1],[x1,y1,z0],[x0,y1,z0]],[0,1,0]),
      'down':([[x0,y0,z0],[x1,y0,z0],[x1,y0,z1],[x0,y0,z1]],[0,-1,0])}
    fs=[]; vanilla={}
    for name,f in e['faces'].items():
        if f.get('texture') is None:continue
        verts,normal=faces[name]
        fs.append({'vertices':[[v/16 for v in xyz] for xyz in verts],'normal':normal,'uv':[v/128 for v in f['uv']],'texture':int(f['texture'])})
        vanilla[name]={'uv':[v/8 for v in f['uv']],'texture':'#'+str(f['texture'])}
    geometry.append({'faces':fs,'slide':element_slides.get(e['uuid'],[0,0,0])})
    elements.append({'from':[x0+8,y0,z0+8],'to':[x1+8,y1,z1+8],'faces':vanilla})
put(R/'assets/augment/chamber_geometry.json',geometry)
for name in ['chamber_metal.png','chamber_glass.png','chamber_light.png']:
    dst=R/'assets/augment/textures/block'/name;dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(SOURCE/name,dst)
textures={str(i):'augment:block/'+n for i,n in enumerate(['chamber_metal','chamber_glass','chamber_light'])};textures['particle']='augment:block/chamber_metal'
put(R/'assets/augment/models/block/modification_chamber.json',{'textures':textures,'elements':elements})
put(R/'assets/augment/blockstates/modification_chamber.json',{'variants':{'':{'model':'augment:block/modification_chamber'}}})
put(R/'assets/augment/models/item/modification_chamber.json',{'parent':'augment:block/modification_chamber','display':{'gui':{'rotation':[20,35,0],'translation':[0,-3,0],'scale':[.45,.45,.45]},'ground':{'translation':[0,1,0],'scale':[.25,.25,.25]},'fixed':{'translation':[0,-4,0],'scale':[.4,.4,.4]},'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2,0],'scale':[.25,.25,.25]}}})

p=R/'assets/augment/models/item/modification_chamber.json'
d=json.loads(p.read_text())
for hand,angle in [('righthand',45),('lefthand',225)]:d['display']['firstperson_'+hand]={'rotation':[0,angle,0],'translation':[0,2,0],'scale':[.18,.18,.18]}
put(p,d)
print('Exported',len(geometry),'cubes and',len(slides),'door tracks')
