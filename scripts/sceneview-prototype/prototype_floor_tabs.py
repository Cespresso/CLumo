"""Blender-only editable C concept. Run with Blender --background --factory-startup --python."""
import bpy, math, json
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/sceneview-prototype/c-tabs-blender'
OUT.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)
s=bpy.context.scene
bpy.context.preferences.filepaths.save_version=0
s.unit_settings.system='METRIC'; s.unit_settings.length_unit='MILLIMETERS'
bpy.ops.import_scene.gltf(filepath=str(ROOT/'companion/android/app/src/main/assets/models/headphone-stand-live.glb'))
product=list(s.objects)
collection=bpy.data.collections.new('C / Floor controls');s.collection.children.link(collection)
def link(o):
    collection.objects.link(o); return o
def mat(name,color,rough=.75):
    m=bpy.data.materials.new(name);m.use_nodes=True;m.diffuse_color=(*color,1)
    bs=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
    bs.inputs['Base Color'].default_value=(*color,1);bs.inputs['Roughness'].default_value=rough
    return m
orange=mat('CLumo / orange solid',(.85,.10,.012));ivory=mat('CLumo / ivory keys',(.79,.78,.70));ink=mat('CLumo / charcoal',(.012,.016,.013));floor_mat=mat('Warm grey floor',(.57,.55,.51));muted=mat('Secondary text',(.07,.08,.065))
def mesh(name,verts,faces,material):
    me=bpy.data.meshes.new(name);me.from_pydata(verts,[],faces);me.update()
    o=link(bpy.data.objects.new(name,me));o.data.materials.append(material);return o
H=.244
RIBBON_WIDTH=.048
plate_mat=mat('CLumo / grouped control plate',(.64,.635,.59))
pattern_mats=[mat('Pattern / distant L',(.59,.587,.548)),mat('Pattern / near L',(.53,.535,.50))]
stations=[]
def point(x,z,nx,nz): stations.append((x*H,-z*H,nx,-nz))
def arc(cx,cz,start,sweep):
    for i in range(49):
        a=math.radians(start+sweep*i/48);c=math.cos(a);v=math.sin(a);sign=1 if sweep>0 else -1
        point(cx+.13*c,cz+.13*v,sign*c,sign*v)
point(-.8,-.4,0,-1);arc(.19,-.27,270,90);arc(.19,.16,0,90);arc(-.17,.42,270,-90);point(-.3,2.8,1,0)
v=[]
for x,y,nx,ny in stations:
    for z,side in [(0,-1),(0,1),(.001,-1),(.001,1)]:v.append((x+side*RIBBON_WIDTH/2*nx,y+side*RIBBON_WIDTH/2*ny,z+.00002))
f=[]
for i in range(len(stations)-1):
    a=i*4;b=a+4
    f += [(a+2,b+2,b+3,a+3),(a+1,b+1,b,a),(a,b,b+2,a+2),(a+3,b+3,b+1,a+1)]
f += [(0,2,3,1),(len(v)-4,len(v)-3,len(v)-1,len(v)-2)]
ribbon=mesh('ribbon / width 48mm / thickness 1mm',v,f,orange)
# Recalculate the closed ribbon's normals for exported use.
bpy.context.view_layer.objects.active=ribbon;ribbon.select_set(True)
bpy.ops.object.mode_set(mode='EDIT');bpy.ops.mesh.select_all(action='SELECT');bpy.ops.mesh.normals_make_consistent(inside=False);bpy.ops.object.mode_set(mode='OBJECT');ribbon.select_set(False)
def rounded(name,x,y,w,h,t,r,material):
    poly=[]
    for cx,cy,a0 in [(w/2-r,h/2-r,0),(-w/2+r,h/2-r,90),(-w/2+r,-h/2+r,180),(w/2-r,-h/2+r,270)]:
        for j in range(13):
            a=math.radians(a0+j*90/12);poly.append((cx+r*math.cos(a),cy+r*math.sin(a)))
    n=len(poly);vs=[(px,py,z) for z in [0,t] for px,py in poly]
    faces=[tuple(reversed(range(n))),tuple(range(n,2*n))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
    o=mesh(name,vs,faces,material);o.location=(x,y,.00004)
    mod=o.modifiers.new('Soft rim 0.45mm','BEVEL');mod.width=.00045;mod.segments=3
    o['height_mm']=t*1000;o['corner_radius_mm']=r*1000
    return o
def stroke(name,pts,r=.00045,parent=None,material=ink):
    cu=bpy.data.curves.new(name,'CURVE');cu.dimensions='3D';cu.bevel_depth=r;cu.bevel_resolution=3
    sp=cu.splines.new('POLY');sp.points.add(len(pts)-1)
    for p,co in zip(sp.points,pts):p.co=(*co,1)
    o=link(bpy.data.objects.new(name,cu));o.data.materials.append(material)
    if parent:o.parent=parent
    return o
def symbolpoly(name,coords,tab):
    o=mesh(name,[(x,y,.00265) for x,y in coords],[tuple(range(len(coords)))],ink);o.parent=tab;return o
fontpath=Path('/System/Library/Fonts/Helvetica.ttc')
font=bpy.data.fonts.load(str(fontpath))
def text(name,body,x,y,size,material=ink):
    cu=bpy.data.curves.new(name,'FONT');cu.body=body;cu.font=font;cu.size=size;cu.extrude=0
    o=link(bpy.data.objects.new(name,cu));o.location=(x,y,.00010);cu.materials.append(material);return o
def plate_pattern(key, row_y):
    # Clip oversized overlapping brand motifs to the rounded plate outline.
    boundary=[]
    w,h,r=.160,.043,.007
    for cx,cy,a0 in [(w/2-r,h/2-r,0),(-w/2+r,h/2-r,90),(-w/2+r,-h/2+r,180),(w/2-r,-h/2+r,270)]:
        for j in range(13):
            a=math.radians(a0+j*90/12)
            boundary.append((-.0132+cx+r*math.cos(a),row_y+cy+r*math.sin(a)))
    def clip(poly):
        for j,a in enumerate(boundary):
            b=boundary[(j+1)%len(boundary)]
            def dist(p):return (b[0]-a[0])*(p[1]-a[1])-(b[1]-a[1])*(p[0]-a[0])
            result=[]
            if not poly:break
            prev=poly[-1];dp=dist(prev)
            for cur in poly:
                dc=dist(cur)
                if (dp>=0)!=(dc>=0):
                    t=dp/(dp-dc);result.append((prev[0]+t*(cur[0]-prev[0]),prev[1]+t*(cur[1]-prev[1])))
                if dc>=0:result.append(cur)
                prev,dp=cur,dc
            poly=result
        return poly
    for layer,(cx,cy,scale) in enumerate([(-.009,.006,1.30),(.047,-.006,1.65)]):
        path=[(-.039,-.019,0,-1)]
        for j in range(25):
            a=math.radians(-90+j*90/24)
            path.append((.007+.012*math.cos(a),-.007+.012*math.sin(a),math.cos(a),math.sin(a)))
        path.append((.019,.043,1,0))
        verts=[];faces=[]
        for a,b in zip(path,path[1:]):
            poly=[(cx+scale*(p[0]+side*.0045*p[2]),row_y+cy+scale*(p[1]+side*.0045*p[3])) for p,side in [(a,-1),(a,1),(b,1),(b,-1)]]
            poly=clip(poly)
            if len(poly)<3:continue
            start=len(verts);verts.extend([(x,y,.002255+layer*.000015) for x,y in poly]);faces.append(tuple(range(start,start+len(poly))))
        mesh('pattern_'+key+'_'+str(layer),verts,faces,pattern_mats[layer])

labels=[('rotation','Rotate','Volume'),('single','Single press','Play / Pause'),('double','Double press','Mute'),('triple','Triple press','Previous track')]
buttons=[]
for i,(key,gesture,value) in enumerate(labels):
    y=-(.57+i*.245)*H
    plate=rounded('group_plate_'+key,-.0132,y,.160,.043,.0012,.007,plate_mat)
    plate.location.z=.00104
    plate['purpose']='Groups icon key and text as one control'
    plate_pattern(key,y)
    tab=rounded('tab_'+key,-.3*H,y,.029,.029,.0025,.006,ivory);buttons.append(tab)
    tab.location.z=.00226
    tab['action']=key;tab['press_travel_mm']=.7
    # Icons are children of the key and follow its press, with no text baked into the mesh.
    if i==0:
        pts=[(.007*math.cos(math.radians(a)),.007*math.sin(math.radians(a)),.00265) for a in range(35,321,5)]
        stroke('icon_rotate',pts,parent=tab)
        symbolpoly('rotate_arrow',[(.0063,-.0057),(.0022,-.006),(.0055,-.002)],tab)
        marker=rounded('active_indicator',0,0,.0016,.006,.0003,.0007,orange);marker.parent=tab;marker.location=(-.011,0,.00255)
    elif i==1:
        symbolpoly('play',[(-.007,-.005),(-.007,.005),(.0005,0)],tab)
        for x in [.003,.007]:symbolpoly('pause',[(x-.001,-.005),(x+.001,-.005),(x+.001,.005),(x-.001,.005)],tab)
    elif i==2:
        symbolpoly('speaker',[(-.009,-.0025),(-.005,-.0025),(0,-.006),(0,.006),(-.005,.0025),(-.009,.0025)],tab)
        stroke('mute_1',[(.003,-.004,.00265),(.009,.004,.00265)],parent=tab)
        stroke('mute_2',[(.003,.004,.00265),(.009,-.004,.00265)],parent=tab)
    else:
        symbolpoly('previous',[(.007,-.006),(.007,.006),(-.004,0)],tab)
        stroke('previous_stop',[(-.007,-.006,.00265),(-.007,.006,.00265)],parent=tab)
    text('gesture_'+key,gesture,-.045,y+.006,.010,muted).location.z=.00230
    text('assignment_'+key,value,-.045,y-.013,.016).location.z=.00230
text('section_title','Knob',-.042,-.105,.017)
# An editable press demonstration; rest at frame 1, held at frame 18, released at 30.
first=buttons[0]
for frame,z in [(1,.00226),(10,.00226),(18,.00156),(24,.00156),(30,.00226),(48,.00226)]:
    first.location.z=z;first.keyframe_insert(data_path='location',frame=frame)
s.frame_start=1;s.frame_end=48;s.render.fps=30;s.frame_set(1)
# Ground and daylight-like studio lighting.
mesh('Ground',[(-3,-3,-.00003),(3,-3,-.00003),(3,3,-.00003),(-3,3,-.00003)],[(0,1,2,3)],floor_mat)
w=bpy.data.worlds.new('Soft studio');w.use_nodes=True;next(n for n in w.node_tree.nodes if n.type=='BACKGROUND').inputs[0].default_value=(.65,.65,.65,1);next(n for n in w.node_tree.nodes if n.type=='BACKGROUND').inputs[1].default_value=.5;s.world=w
ld=bpy.data.lights.new('Key','AREA');lo=link(bpy.data.objects.new('Key',ld));lo.location=(-.45,-.3,.8);ld.energy=5;ld.shape='DISK';ld.size=.055;lo.rotation_euler=(Vector((0,-.1,0))-lo.location).to_track_quat('-Z','Y').to_euler()
camdata=bpy.data.cameras.new('Phone view');cam=link(bpy.data.objects.new('Phone view',camdata));camdata.lens=85;camdata.sensor_fit='VERTICAL';camdata.sensor_height=36
cam.location=(-.073,-.81,.71);target=Vector((-.008,-.156,.005));cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler();s.camera=cam
s.view_settings.view_transform='Standard'
s.render.engine='CYCLES';s.cycles.samples=24;s.cycles.use_denoising=True
s.render.resolution_x=900;s.render.resolution_y=1800;s.render.resolution_percentage=100
s.render.image_settings.file_format='PNG';s.render.film_transparent=False
# Preserve the installed default color transform rather than assuming version-specific enums.
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=='VIEW_3D':area.spaces.active.region_3d.view_perspective='CAMERA'
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'clumo-c-tabs.blend'))
report={'blender':bpy.app.version_string,'source':'headphone-stand-live.glb','ribbon_width_mm':48,'group_plate_size_mm':[160,43,1.2],'ribbon_thickness_mm':1,'button_size_mm':[29,29,2.5],'button_corner_radius_mm':6,'press_travel_mm':.7,'buttons':[o.name for o in buttons],'frames':{'rest':1,'pressed':18,'released':30}}
(OUT/'dimensions.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
s.render.filepath=str(OUT/'c-tabs-camera.png');bpy.ops.render.render(write_still=True)
# Detail view for checking physical relief, with its own camera retained in the blend.
detail=cam.copy();detail.data=cam.data.copy();detail.name='Button detail';collection.objects.link(detail)
detail.location=(-.13,-.20,.16);detail.rotation_euler=(Vector((-.025,-.139,0))-detail.location).to_track_quat('-Z','Y').to_euler();detail.data.lens=62;detail.data.sensor_fit='HORIZONTAL'
s.camera=detail;s.render.resolution_x=1200;s.render.resolution_y=900;s.render.filepath=str(OUT/'c-tabs-detail.png');bpy.ops.render.render(write_still=True)
s.camera=cam;s.render.resolution_x=900;s.render.resolution_y=1800;s.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'clumo-c-tabs.blend'))
print('C_TABS_DONE',flush=True)
