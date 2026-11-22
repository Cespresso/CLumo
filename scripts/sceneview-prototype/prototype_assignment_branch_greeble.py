"""A concept: physical assignment tiles on a branch of the floor ribbon."""
import bpy, math, ast
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/sceneview-prototype/assignment-branch-greeble-blender'
OUT.mkdir(parents=True,exist_ok=True)
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'build/sceneview-prototype/c-tabs-blender/clumo-c-tabs.blend'))
s=bpy.context.scene
bpy.context.preferences.filepaths.save_version=0
collection=bpy.data.collections.new('A / Assignment branch');s.collection.children.link(collection)
def link(o):collection.objects.link(o);return o
ink=bpy.data.materials['CLumo / charcoal']
ivory=bpy.data.materials['CLumo / ivory keys']
orange=bpy.data.materials['CLumo / orange solid']
plate_mat=bpy.data.materials['CLumo / grouped control plate']
muted=bpy.data.materials['Secondary text']
font=bpy.data.fonts.load('/System/Library/Fonts/Helvetica.ttc')
# Reuse the existing prototype's mesh/text construction without running its scene generator.
source=ast.parse((ROOT/'scripts/sceneview-prototype/prototype_floor_tabs.py').read_text())
for n in source.body:
    if isinstance(n,ast.FunctionDef) and n.name in ['mat','mesh','rounded','stroke','symbolpoly','text']:
        exec(compile(ast.Module(body=[n],type_ignores=[]),'<prototype-helpers>','exec'))
# Freeze the previous button demo, keeping the existing parent destination intact.
for o in s.objects:
    o.animation_data_clear()
s.frame_set(1)
bpy.data.objects['tab_rotation'].location.z=.00226
# A single branch exits the selected row and bends towards the assignment tiles.
# Same width, radius and thickness as the primary ribbon.
y_source=-(.57+2*.245)*.244
width=.048; radius=.03172
stations=[(-.0732,y_source,0,1),(.172-radius,y_source,0,1)]
for j in range(33):
    a=math.pi/2-j*math.pi/2/32
    stations.append((.172-radius+radius*math.cos(a),y_source-radius+radius*math.sin(a),math.cos(a),math.sin(a)))
stations.append((.172,-.90,1,0))
vs=[]
for x,y,nx,ny in stations:
    for z,side in [(0,-1),(0,1),(.001,-1),(.001,1)]:vs.append((x+side*width/2*nx,y+side*width/2*ny,z+.000025))
faces=[]
for i in range(len(stations)-1):
    a=i*4;b=a+4;faces += [(a+2,b+2,b+3,a+3),(a+1,b+1,b,a),(a,b,b+2,a+2),(a+3,b+3,b+1,a+1)]
faces += [(0,2,3,1),(len(vs)-4,len(vs)-3,len(vs)-1,len(vs)-2)]
branch=mesh('Branch / Double press / 48mm x 1mm',vs,faces,orange)
bpy.context.view_layer.objects.active=branch;bpy.ops.object.select_all(action='DESELECT');branch.select_set(True)
bpy.ops.object.mode_set(mode='EDIT');bpy.ops.mesh.select_all(action='SELECT');bpy.ops.mesh.normals_make_consistent(inside=False);bpy.ops.object.mode_set(mode='OBJECT');branch.select_set(False)
# A small orange rail marks the selected parent row, without changing its text.
rail=rounded('Selected gesture rail',.061,y_source,.003,.032,.0005,.001,orange);rail.location.z=.0023
text('Branch heading','Double press',.200,-.295,.019).location.z=.00012
text('Branch subtitle','KNOB  /  ASSIGN ACTION',.201,-.313,.006,muted).location.z=.00012
text('Branch back','<  Knob',.202,-.270,.008,muted).location.z=.00012
relief_mat=mat('Panel / shallow brand relief',(.55,.55,.51))
index_mat=mat('Panel / technical markings',(.18,.20,.18))
# Two columns keep all eight firmware-supported actions visible in a portrait view.
choices=[('play','Play / Pause'),('next','Next track'),('previous','Previous track'),('mute','Mute'),('voice','Voice assistant'),('none','None'),('forward','Fast forward'),('rewind','Rewind')]
rows=[]
for i,(key,label) in enumerate(choices):
    col=i%2; row=i//2;x=.204+col*.112;y=-.362-row*.068
    before=set(collection.objects)
    # The solid backing unites a square icon with its English label.
    plate=rounded('choice_plate_'+key,x,y,.102,.060,.0012,.007,plate_mat);plate.location.z=.00104
    tab=rounded('choice_icon_'+key,x-.030,y+.009,.029,.029,.0025,.006,ivory);tab.location.z=.00226
    # Large typographic blocks, with compact two-line labels for longer actions.
    lines={'previous':['PREVIOUS','TRACK'],'voice':['VOICE','ASSISTANT'],
           'forward':['FAST','FORWARD'],'rewind':['REWIND'], 'next':['NEXT TRACK'],
           'play':['PLAY / PAUSE'],'mute':['MUTE'],'none':['NONE']}[key]
    for line_index,line in enumerate(lines):
        yy=y-.016-line_index*.012 if len(lines)>1 else y-.022
        title=text('choice_label_'+key+'_'+str(line_index),line,x-.043,yy,.0125 if len(lines)>1 else .0135)
        title.location.z=.00255;title.data.extrude=.000015
        bpy.context.view_layer.update()
        if title.dimensions.x>.087:title.scale.x*=.087/title.dimensions.x
    text('choice_index_'+key,f'{i+1:02}',x+.019,y+.001,.023,index_mat).location.z=.00255
    # Oversized mirrored-L relief: a shallow solid, not floating decals or fake text.
    points=[(-.010,-.014,0,-1)]
    for j in range(25):
        a=math.radians(-90+j*90/24)
        points.append((.026+.008*math.cos(a),-.006+.008*math.sin(a),math.cos(a),math.sin(a)))
    points.append((.034,.023,1,0))
    verts=[];quads=[]
    for ax,ay,nx,ny in points:
        for zz,side in [(0,-1),(0,1),(.00016,-1),(.00016,1)]:
            verts.append((x+ax+side*.003*nx,y+ay+side*.003*ny,.002245+zz))
    for j in range(len(points)-1):
        a=j*4;b=a+4
        quads.extend([(a+2,b+2,b+3,a+3),(a,b,b+2,a+2),(a+3,b+3,b+1,a+1)])
    mesh('brand_relief_'+key,verts,quads,relief_mat)
    # A short register rail, tiny relief bars and a restrained dot matrix fill the upper field.
    rail=rounded('register_'+key,x-.047,y+.009,.0018,.018,.0003,.0006,orange if key=='mute' else relief_mat);rail.location.z=.00225
    for j in range(6):
        bar=rounded('rib_'+key+'_'+str(j),x-.008+j*.0028,y+.008,.0008,.004+(j%3)*.001,.00028,.0003,relief_mat);bar.location.z=.00225
    for j in range(6):
        xx=x-.007+(j%3)*.0035;yy=y+.021+(j//3)*.003
        mesh('dot_'+key+'_'+str(j),[(xx+.00045*math.cos(a*math.pi/6),yy+.00045*math.sin(a*math.pi/6),.00248) for a in range(12)],[tuple(range(12))],muted)

    # A recessed-looking current value marker and orange key outline.
    if key=='mute':
        under=rounded('Current assignment rim',x-.030,y+.009,.032,.032,.00045,.0065,orange);under.location.z=.00225
        tab.location.z=.00215
        text('Current value','CURRENT',x+.015,y+.024,.0042,muted).location.z=.00230
    if key=='play':
        symbolpoly('play',[(-.007,-.005),(-.007,.005),(.0005,0)],tab)
        for xx in [.003,.007]:symbolpoly('pause',[(xx-.001,-.005),(xx+.001,-.005),(xx+.001,.005),(xx-.001,.005)],tab)
    elif key in ('next','previous','forward','rewind'):
        d=-1 if key in ('previous','rewind') else 1
        for off in ([0] if key in ('next','previous') else [-.004,.004]):
            symbolpoly(key,[(off+d*.005,0),(off-d*.004,-.006),(off-d*.004,.006)],tab)
        if key in ('next','previous'):stroke('stop',[(d*.008,-.006,.00265),(d*.008,.006,.00265)],parent=tab)
    elif key=='mute':
        symbolpoly('speaker',[(-.009,-.0025),(-.005,-.0025),(0,-.006),(0,.006),(-.005,.0025),(-.009,.0025)],tab)
        stroke('mute1',[(.003,-.004,.00265),(.009,.004,.00265)],parent=tab)
        stroke('mute2',[(.003,.004,.00265),(.009,-.004,.00265)],parent=tab)
    elif key=='none':
        stroke('none_ring',[(.007*math.cos(a*math.pi/24),.007*math.sin(a*math.pi/24),.00265) for a in range(49)],parent=tab)
        stroke('none_slash',[(-.005,-.005,.00265),(.005,.005,.00265)],parent=tab)
    else:
        stroke('mic_capsule',[(.003*math.cos(a*math.pi/24),.004+.003*math.sin(a*math.pi/24),.00265) for a in range(49)],parent=tab)
        stroke('mic_arc',[(.006*math.cos(a*math.pi/24),.001+.006*math.sin(a*math.pi/24),.00265) for a in range(24,49)],parent=tab)
        stroke('mic_stand',[(0,-.005,.00265),(0,-.009,.00265)],parent=tab)
    if key in ('forward','rewind'):text('beta_'+key,'BETA',x+.023,y+.024,.0042,muted).location.z=.00230
    rows.append(list(set(collection.objects)-before))
text('Footer hint','Select to assign and return',.202,-.618,.0065,muted).location.z=.00012
# Editable navigation animation; all objects stay in one scene.
cam=bpy.data.objects['Phone view'];start_location=cam.location.copy();start_rotation=cam.rotation_euler.copy()
end_target=Vector((.268,-.436,.002));end_location=Vector((.25,-1.20,.93))
end_rotation=(end_target-end_location).to_track_quat('-Z','Y').to_euler()
for frame,loc,rot in [(1,start_location,start_rotation),(12,start_location,start_rotation),(42,end_location,end_rotation),(82,end_location,end_rotation),(112,start_location,start_rotation)]:
    cam.location=loc;cam.rotation_euler=rot;cam.keyframe_insert(data_path='location',frame=frame);cam.keyframe_insert(data_path='rotation_euler',frame=frame)
# Preview visibility follows the branch arrival; geometry stays editable.
for group_index,group in enumerate([[branch]]+rows):
    enter=16+group_index*2
    leave=100-group_index*2
    for o in group:
        for frame,hidden in [(1,True),(enter-1,True),(enter,False),(leave,False),(leave+1,True)]:
            o.hide_render=hidden;o.keyframe_insert(data_path='hide_render',frame=frame)
for name,frame in [('Knob actions',1),('Follow branch',16),('Choose assignment',60),('Return without change',90)]:s.timeline_markers.new(name,frame=frame)
s.frame_start=1;s.frame_end=112;s.render.fps=30
s.cycles.samples=32;s.cycles.use_denoising=True
s.render.resolution_x=900;s.render.resolution_y=1800;s.render.resolution_percentage=100
s.camera=cam;s.frame_set(60)
s.render.filepath=str(OUT/'assignment-phone.png');bpy.ops.render.render(write_still=True)
# Overview makes the spatial relationship inspectable.
data=bpy.data.cameras.new('Branch overview');overview=link(bpy.data.objects.new('Branch overview',data));data.type='ORTHO';data.ortho_scale=.80
location=Vector((.10,-1.03,1.10));target=Vector((.10,-.265,.02));overview.location=location;overview.rotation_euler=(target-location).to_track_quat('-Z','Y').to_euler()
s.camera=overview;s.render.resolution_x=1600;s.render.resolution_y=1200;s.render.filepath=str(OUT/'assignment-overview.png');bpy.ops.render.render(write_still=True)
s.camera=cam;s.render.resolution_x=900;s.render.resolution_y=1800;s.frame_set(60)
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=='VIEW_3D':area.spaces.active.region_3d.view_perspective='CAMERA'
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'clumo-assignment-branch-greeble.blend'))
print('ASSIGNMENT_BRANCH_DONE',flush=True)
