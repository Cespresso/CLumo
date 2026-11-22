"""Bake the white/orange headphone stand for the disposable Android study."""
import bpy, sys, json, hashlib
from pathlib import Path
from mathutils import Vector
sys.path.insert(0,str(Path(__file__).resolve().parent))
from unlit_glb import convert
root=Path(__file__).resolve().parents[2]
out=root/'companion/android/app/src/main/assets/models'
source=Path('/Users/noguchinaoya/learn/maker-projects/nothing-headphone-accessory/publishing/makerworld/features/clumo-front-controls-matrix-red.blend')
bpy.ops.wm.open_mainfile(filepath=str(source))
s=bpy.context.scene;s.render.engine='CYCLES';s.cycles.samples=32;s.cycles.device='CPU';s.render.bake.margin=4
products=[o for o in bpy.data.objects['white'].children_recursive if o.type=='MESH']
original_count=len(products);faces=sum(len(o.data.polygons) for o in products)
bpy.ops.object.select_all(action='DESELECT')
for o in products:o.hide_set(False);o.hide_render=False;o.select_set(True)
bpy.context.view_layer.objects.active=products[0]
bpy.ops.object.join()
o=bpy.context.object;o.name='Headphone Stand / white-orange'
# Preserve world-space position, so procedural print grain and LED masks stay aligned.
world=o.matrix_world.copy();o.parent=None;o.matrix_world=world
bpy.ops.object.transform_apply(location=True,rotation=True,scale=True)
assert len(o.data.polygons)==faces
print('STAND_UV',faces,flush=True)
bpy.ops.object.mode_set(mode='EDIT');bpy.ops.mesh.select_all(action='SELECT')
bpy.ops.uv.smart_project(angle_limit=1.15,island_margin=.001)
bpy.ops.object.mode_set(mode='OBJECT')
# A shared atlas lets the many original pieces render as one primitive.
materials=list(o.data.materials)
textures=[]
for m in materials:
    tex=m.node_tree.nodes.new('ShaderNodeTexImage');textures.append(tex)
images={}
for kind in ['NORMAL','DIFFUSE','EMIT','COMBINED']:
    im=bpy.data.images.new('stand_'+kind.lower(),width=4096,height=4096)
    if kind=='NORMAL':im.colorspace_settings.name='Non-Color'
    for m,tex in zip(materials,textures):tex.image=im;m.node_tree.nodes.active=tex
    s.render.bake.use_pass_direct=kind!='DIFFUSE';s.render.bake.use_pass_indirect=kind!='DIFFUSE';s.render.bake.use_pass_color=True
    print('STAND_BAKE',kind,flush=True);bpy.ops.object.bake(type=kind);im.pack();images[kind]=im

def fresh(name):
    m=bpy.data.materials.new(name);m.use_nodes=True
    o.data.materials.clear();o.data.materials.append(m)
    for f in o.data.polygons:f.material_index=0
    return m

def image_node(m,kind):
    n=m.node_tree.nodes.new('ShaderNodeTexImage');n.image=images[kind];return n

def export(mode):
    bpy.ops.export_scene.gltf(filepath=str(out/f'headphone-stand-{mode}.glb'),export_format='GLB',use_selection=True,export_yup=True,export_cameras=False,export_lights=False)

m=fresh('Stand baked studio lighting');n=m.node_tree.nodes;n.clear()
tex=image_node(m,'COMBINED');em=n.new('ShaderNodeEmission');output=n.new('ShaderNodeOutputMaterial')
m.node_tree.links.new(tex.outputs['Color'],em.inputs['Color']);m.node_tree.links.new(em.outputs[0],output.inputs['Surface'])
export('baked');convert(out/'headphone-stand-baked.glb')
m=fresh('Stand PBR with red matrix and status LEDs');bs=m.node_tree.nodes.get('Principled BSDF');bs.inputs['Roughness'].default_value=.72
tex=image_node(m,'DIFFUSE');m.node_tree.links.new(tex.outputs['Color'],bs.inputs['Base Color'])
tex=image_node(m,'NORMAL');normal=m.node_tree.nodes.new('ShaderNodeNormalMap');m.node_tree.links.new(tex.outputs['Color'],normal.inputs['Color']);m.node_tree.links.new(normal.outputs['Normal'],bs.inputs['Normal'])
tex=image_node(m,'EMIT');m.node_tree.links.new(tex.outputs['Color'],bs.inputs['Emission Color']);bs.inputs['Emission Strength'].default_value=1
export('pbr')
# Full-product reference from the original materials, never save the source scene.
o.data.materials.clear()
# Reopen to restore all original material assignments for the reference render.
bpy.ops.wm.open_mainfile(filepath=str(source));s=bpy.context.scene
cam=s.camera;target=Vector((0,0,.122));cam.data.type='PERSP';cam.data.lens=85;cam.data.dof.use_dof=False
cam.location=target+Vector((-.4,-.65,.22)).normalized()*.85
cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler()
s.render.resolution_x=1000;s.render.resolution_y=1000;s.render.resolution_percentage=100
s.render.film_transparent=False;s.render.image_settings.file_format='JPEG';s.render.image_settings.color_mode='RGB';s.render.image_settings.quality=90
s.cycles.samples=32;s.cycles.device='CPU';s.render.filepath=str(out/'headphone-stand-reference.jpg');bpy.ops.render.render(write_still=True)
report={'source':str(source),'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'source_objects':original_count,'polygons':faces,'exported_meshes':1,'texture_size':4096,'samples':32,'variant':'white/orange; red 8x8 all-on illustrative matrix and status LEDs','geometry':'joined without decimation; original coordinates preserved','files':{p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in out.glob('headphone-stand-*')}}
(out/'headphone-stand-export-report.json').write_text(json.dumps(report,indent=2)+'\n')
print('STAND_DONE',flush=True)
