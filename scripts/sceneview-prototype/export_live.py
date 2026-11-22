"""Export independent appearance regions and a single 64-cell LED mesh; never save source assets."""
import bpy, math, json, hashlib
from pathlib import Path
from collections import defaultdict
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'companion/android/app/src/main/assets/models'
STAND = Path('/Users/noguchinaoya/learn/maker-projects/nothing-headphone-accessory/publishing/makerworld/features/clumo-front-controls-matrix-red.blend')
BOX = OUT / 'clumo.glb'
COLORS = {'enclosure':(.72,.72,.67,1),'panel':(.38,.39,.36,1),'button_a':(.85,.85,.82,1),'button_b':(.75,.18,.07,1),'matrix_surface':(.82,.81,.77,1),'window':(.66,.68,.62,1),'fixed_dark':(.03,.03,.03,1),'fixed_internal':(.25,.25,.25,1),'led_matrix':(.1,.1,.1,1)}

def bounds(o):
    pts=[o.matrix_world@Vector(v) for v in o.bound_box]
    return ([min(p[i] for p in pts) for i in range(3)], [max(p[i] for p in pts) for i in range(3)])

def material(role):
    m=bpy.data.materials.new('live_'+role);m.use_nodes=True
    bs=m.node_tree.nodes.get('Principled BSDF');bs.inputs['Base Color'].default_value=COLORS[{'stand_left':'button_a','stand_right':'button_a','stand_knob':'button_b','power_led':'button_b','front_holder':'fixed_dark','rear_holder':'button_b','stand_clip':'button_b','stand_logo':'button_b','front_insert':'button_b'}.get(role,'window' if role.startswith('volume_') else role)];bs.inputs['Roughness'].default_value=.72
    if role.startswith('volume_'):
        bs.inputs['Emission Color'].default_value=(1,.94,.8,1)
        bs.inputs['Emission Strength'].default_value=1.5
    return m

def consolidate(groups):
    result=[]
    for role,objects in groups.items():
        bpy.ops.object.select_all(action='DESELECT')
        for o in objects:
            o.hide_set(False);o.hide_render=False;o.select_set(True)
        bpy.context.view_layer.objects.active=objects[0]
        bpy.ops.object.join();o=bpy.context.object
        world=o.matrix_world.copy();o.parent=None;o.matrix_world=world
        bpy.ops.object.transform_apply(location=True,rotation=True,scale=True)
        o.name=role;o.data.materials.clear();o.data.materials.append(material(role))
        for p in o.data.polygons:p.material_index=0
        result.append(o)
    return result

def leds(kind, window_bounds):
    lo,hi=window_bounds
    center_x=(lo[0]+hi[0])/2
    center_z=(lo[2]+hi[2])/2
    vertices=[];faces=[];cell_for_face=[];centers=[]
    for row in range(8):
        for col in range(8):
            if kind=='stand':x,y,z,r=center_x-.014+col*.004,lo[1]-.00006,center_z+.014-row*.004,.0013
            else:x,y,z,r=center_x+.014-col*.004,hi[1]+.00008,center_z+.014-row*.004,.0013
            centers.append([x,y,z]);base=len(vertices);vertices.append((x,y,z))
            for k in range(16):
                angle=k*math.tau/16;vertices.append((x+r*math.cos(angle),y,z+r*math.sin(angle)))
            for k in range(16):
                f=(base,base+1+k,base+1+(k+1)%16)
                faces.append(f if kind=='stand' else tuple(reversed(f)));cell_for_face.append(row*8+col)
    mesh=bpy.data.meshes.new('led_matrix');mesh.from_pydata(vertices,[],faces);mesh.update()
    uv=mesh.uv_layers.new(name='LED_cells')
    for p,cell in zip(mesh.polygons,cell_for_face):
        for loop in p.loop_indices:uv.data[loop].uv=((cell%8+.5)/8,1-(cell//8+.5)/8)
    o=bpy.data.objects.new('led_matrix',mesh);bpy.context.collection.objects.link(o);mesh.materials.append(material('led_matrix'))
    return o,centers

def export(name,objects):
    bpy.ops.object.select_all(action='DESELECT')
    for o in objects:o.select_set(True)
    p=OUT/name
    bpy.ops.export_scene.gltf(filepath=str(p),export_format='GLB',use_selection=True,export_yup=True,export_cameras=False,export_lights=False)
    return {'file':name,'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'nodes':[o.name for o in objects]}

bpy.ops.wm.open_mainfile(filepath=str(STAND))
groups=defaultdict(list)
for o in list(bpy.data.objects['white'].children_recursive):
    if o.type!='MESH':continue
    name=o.data.materials[0].name;lo,hi=bounds(o)
    if name=='matrix_window_soft_8x8':
        role='matrix_surface';window_bounds=(lo,hi)
    elif name=='grey_front':role='panel'
    elif name.startswith('black_') and lo[2]>.23:role='front_holder'
    elif name.startswith('black_'):role='fixed_dark'
    elif name.startswith('orange_') and lo[2]>.23:role='rear_holder'
    elif name.startswith('orange_') and lo[1]>.015 and lo[2]>.15:role='stand_clip'
    elif name.startswith('orange_') and .08<lo[2]<.10 and hi[2]<.13:role='front_insert'
    elif name.startswith('orange_') and lo[2]>.20:role='stand_logo'
    elif name.startswith('orange_') and lo[0]>.01 and hi[2]<.035 and lo[1]<-.046:role='stand_knob'
    # The power lens sits directly below the three volume windows.
    elif name.startswith('orange_') and .017 < lo[0] < hi[0] < .028 and .039 < lo[2] < hi[2] < .049 and lo[1] < -.043:
        role='power_led'
    elif name.startswith('orange_'):role='button_b'
    elif name=='white_front_indicator_lit':role='window'
    elif name.startswith('material_'):role='fixed_internal'
    elif name=='white_front' and lo[2]>.015 and hi[2]<.035 and hi[0]<.006 and lo[1]<-.0445:role='stand_left' if hi[0]<0 else 'stand_right'
    else:role='enclosure'
    groups[role].append(o)
# The four pieces of each indicator share a height (Blender Z, bottom first).
assert groups['power_led'], 'Missing front power lens'
lenses=sorted(groups.pop('window'),key=lambda o: sum(v[2] for v in bounds(o)))
assert len(lenses)==12
for index in range(3):
    groups[f'volume_{index+1}']=lenses[index*4:index*4+4]
objects=consolidate(groups);matrix,centers=leds('stand',window_bounds);objects.append(matrix)
stand=export('headphone-stand-live.glb',objects);stand['led_centers_blender']=centers
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=str(BOX))
groups=defaultdict(list)
for o in list(bpy.data.objects):
    if o.type!='MESH':continue
    if o.name=='front_surface':window_bounds=bounds(o)
    role={'outer_shell':'enclosure','back_panel':'enclosure','front_surface':'matrix_surface','button:left':'button_a','button_ring:left':'button_a','button:right':'button_b','button_ring:right':'button_b','module_envelope':'fixed_dark'}.get(o.name,'fixed_internal')
    groups[role].append(o)
objects=consolidate(groups);matrix,centers=leds('box',window_bounds);objects.append(matrix)
box=export('clumo-live.glb',objects);box['led_centers_blender']=centers
report={'sources':{str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in [STAND,BOX]},'assets':[stand,box],'led_uv':'glTF U=(col+.5)/8, V=(row+.5)/8; triangle fan discs share one texel per LED','surface':'PBR solid appearance regions, no baked lighting/LEDs; original geometry retained','telemetry':'8x8 matrix and three volume lenses; power lens follows the app connection'}
(OUT/'live-export-report.json').write_text(json.dumps(report,indent=2)+'\n')
print('LIVE_EXPORT_DONE',[(r['file'],r['bytes']) for r in report['assets']],flush=True)
