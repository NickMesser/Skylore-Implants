(()=>{
const t1=Texture.all.find(t=>t.name.startsWith('armor1')),t2=Texture.all.find(t=>t.name.startsWith('armor2'));
t1.uv_width=64;t1.uv_height=32;t2.uv_width=64;t2.uv_height=32;
const old=Outliner.root.find(n=>n.name==='armor');if(old)old.remove();
const g=new Group({name:'armor',origin:[0,0,0]}).init();g.export=false;
const P=[
 ['helmet',[-4,24,-4],[4,32,4],[0,0],1,t1,false],
 ['chest',[-4,12,-2],[4,24,2],[16,16],1,t1,false],
 ['chest_right_arm',[-8,12,-2],[-4,24,2],[40,16],1,t1,false],
 ['chest_left_arm',[4,12,-2],[8,24,2],[40,16],1,t1,true],
 ['leggings_body',[-4,12,-2],[4,24,2],[16,16],0.5,t2,false],
 ['leggings_right',[-4,0,-2],[0,12,2],[0,16],0.5,t2,false],
 ['leggings_left',[0,0,-2],[4,12,2],[0,16],0.5,t2,true],
 ['boots_right',[-4,0,-2],[0,12,2],[0,16],1,t1,false],
 ['boots_left',[0,0,-2],[4,12,2],[0,16],1,t1,true]];
for(const [n,from,to,uv,inflate,tex,mirror] of P){const c=new Cube({name:n,from,to,inflate,box_uv:true,uv_offset:uv,mirror_uv:mirror}).addTo(g).init();c.applyTexture(tex,true);}
Canvas.updateAll();
return 'armor';
})()
