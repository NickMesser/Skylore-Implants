(()=>{
const SPECS=__SPEC__;
Outliner.root.slice().forEach(n=>n.remove());
const steve=Texture.all.find(t=>t.name.startsWith('steve'));
if(steve){steve.uv_width=64;steve.uv_height=64;
const ref=new Group({name:'reference',origin:[0,0,0]}).init();ref.export=false;
const body=[['head',[-4,24,-4],[4,32,4],[0,0]],['body',[-4,12,-2],[4,24,2],[16,16]],['right_arm',[-8,12,-2],[-4,24,2],[40,16]],
 ['left_arm',[4,12,-2],[8,24,2],[32,48]],['right_leg',[-4,0,-2],[0,12,2],[0,16]],['left_leg',[0,0,-2],[4,12,2],[16,48]]];
for(const [n,from,to,uv] of body){const c=new Cube({name:'ref_'+n,from,to,box_uv:true,uv_offset:uv}).addTo(ref).init();c.applyTexture(steve,true);}}
const bones={};
for(const S of SPECS){
 const tex=Texture.all.find(t=>t.name===S.name||t.name===S.name+'.png');
 tex.uv_width=S.size;tex.uv_height=S.size;
 if(!bones[S.bone])bones[S.bone]=new Group({name:S.bone,origin:S.pivot}).init();
 const g=new Group({name:S.name+'_'+S.slot,origin:S.pivot}).addTo(bones[S.bone]).init();
 for(const c of S.cubes){const faces={};for(const f in c.faces)faces[f]={uv:c.faces[f],texture:tex.uuid};
  new Cube({name:c.name,from:c.from,to:c.to,origin:S.pivot,box_uv:false,faces}).addTo(g).init();}
}
Canvas.updateAll();
return Outliner.elements.length+' elements';
})()
