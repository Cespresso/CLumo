"""A concept: physical assignment tiles on a branch of the floor ribbon."""
import bpy, math, ast
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/sceneview-prototype/assignment-scroll-blender'
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
# Two columns keep all eight firmware-supported actions visible in a portrait view.
choices=[('play','Play / Pause'),('next','Next track'),('previous','Previous track'),('mute','Mute'),('voice','Voice assistant'),('none','None'),('forward','Fast forward'),('rewind','Rewind')]
rows=[]
for i,(key,label) in enumerate(choices):
    x=.232;y=-.370-i*.05978
    before=set(collection.objects)
    # Identical dimensions and relative layout to the original knob row.
    plate=rounded('choice_plate_'+key,x,y,.160,.043,.0012,.007,plate_mat);plate.location.z=.00104
    tab=rounded('choice_icon_'+key,x-.060,y,.029,.029,.0025,.006,ivory);tab.location.z=.00226
    text('choice_label_'+key,label,x-.0318,y-.013,.014).location.z=.00230
    caption='CURRENT' if key=='mute' else ('BETA' if key in ('forward','rewind') else 'ASSIGN ACTION')
    text('choice_caption_'+key,f'{i+1:02}  /  '+caption,x-.0318,y+.006,.008,muted).location.z=.00230
    for source_name in ['pattern_double_0','pattern_double_1']:
        original=bpy.data.objects.get(source_name)
        if original:
            o=original.copy();o.data=original.data.copy();o.name='choice_'+key+'_'+source_name;link(o)
            o.location.x+=x+.0132;o.location.y+=y-y_source
    if key=='mute':
        under=rounded('Current assignment rim',x-.060,y,.032,.032,.00045,.0065,orange);under.location.z=.00225
        tab.location.z=.00215
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

    rows.append(list(set(collection.objects)-before))
text('End of list','08 / 08    End of actions',.202,-.840,.007,muted).location.z=.00012
# All screens use the same model dimensions. Orthographic review cameras make
# equal dp-sized controls straightforward to compare without perspective shrinkage.
for o in collection.objects:o.hide_render=False
s.frame_start=1;s.frame_end=100;s.render.fps=30
s.cycles.samples=24;s.cycles.use_denoising=True
pitch=math.radians(65)
def camera(name, width, target_y):
    data=bpy.data.cameras.new(name);data.type='ORTHO';data.ortho_scale=width;data.sensor_fit='HORIZONTAL'
    cam=link(bpy.data.objects.new(name,data))
    target=Vector((.232,target_y,0));cam.location=target+Vector((0,-math.cos(pitch),math.sin(pitch)))
    cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler()
    return cam
portrait=camera('01 Portrait / scroll start',.190,-.455)
landscape=camera('02 Landscape / same control size',.380,-.366)
landscape.location.x=.270
scrolled=camera('03 Portrait / scroll end',.190,-.651)
# The review cameras keep scale and angle fixed; only translation changes on scroll.
for frame,ty in [(1,-.455),(15,-.455),(65,-.651),(100,-.651)]:
    portrait.location=(.232,ty-math.cos(pitch),math.sin(pitch));portrait.keyframe_insert(data_path='location',frame=frame)
s.timeline_markers.clear()
for name,frame in [('Top of list',1),('Scroll along ribbon',16),('End of list',65)]:s.timeline_markers.new(name,frame=frame)
for cam,name,w,h,frame in [(portrait,'portrait-start',900,1800,1),(landscape,'landscape-start',1800,900,1),(scrolled,'portrait-end',900,1800,65)]:
    s.camera=cam;s.frame_set(frame);s.render.resolution_x=w;s.render.resolution_y=h
    s.render.filepath=str(OUT/(name+'.png'));bpy.ops.render.render(write_still=True)
s.camera=portrait;s.frame_set(1);s.render.resolution_x=900;s.render.resolution_y=1800
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=='VIEW_3D':area.spaces.active.region_3d.view_perspective='CAMERA'
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'clumo-assignment-scroll.blend'))
print('SCROLL_PROTOTYPE_DONE',flush=True)
