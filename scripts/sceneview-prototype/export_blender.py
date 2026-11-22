"""Export a disposable SceneView study; never save over the source .blend."""
import bpy, sys, json, hashlib
from pathlib import Path
args = sys.argv[sys.argv.index('--') + 1:]
source, output = map(Path, args)
output.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.open_mainfile(filepath=str(source))
s = bpy.context.scene
s.render.engine = 'CYCLES'
s.cycles.device = 'CPU'
s.cycles.samples = 16
s.render.bake.margin = 8
products = [o for o in s.objects if o.type == 'MESH' and o.name.startswith('Noise Emitter /')]
assert len(products) == 4
records = []
for o in products:
    bpy.ops.object.select_all(action='DESELECT')
    o.select_set(True)
    bpy.context.view_layer.objects.active = o
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(angle_limit=1.15, island_margin=.015)
    bpy.ops.object.mode_set(mode='OBJECT')
    material = o.data.materials[0].copy()
    o.data.materials[0] = material
    nodes = material.node_tree.nodes
    tex = nodes.new('ShaderNodeTexImage')
    name = o.name.split('/')[-1].strip()
    # Combined bake retains the window-scene illumination; normal bake supports dynamic PBR.
    maps = {}
    for kind in ['NORMAL', 'COMBINED']:
        im = bpy.data.images.new(name + '_' + kind.lower(), width=1024, height=1024)
        if kind == 'NORMAL': im.colorspace_settings.name = 'Non-Color'
        tex.image = im
        nodes.active = tex
        bpy.ops.object.bake(type=kind)
        im.pack()
        maps[kind] = im
    records.append((o, material, maps))
# Export product only, preserving metre scale and glTF Y-up conversion.
def export(name):
    bpy.ops.object.select_all(action='DESELECT')
    for o, _, _ in records: o.select_set(True)
    bpy.ops.export_scene.gltf(filepath=str(output / name), export_format='GLB', use_selection=True, export_yup=True, export_cameras=False, export_lights=False)
for o, old, maps in records:
    m = bpy.data.materials.new(o.name + ' baked window light'); m.use_nodes=True
    n=m.node_tree.nodes; n.clear(); links=m.node_tree.links
    tex=n.new('ShaderNodeTexImage'); tex.image=maps['COMBINED']
    emission=n.new('ShaderNodeEmission'); links.new(tex.outputs['Color'],emission.inputs['Color'])
    out=n.new('ShaderNodeOutputMaterial'); links.new(emission.outputs[0],out.inputs['Surface'])
    o.data.materials[0]=m
export('noise-emitter-baked.glb')
for o, old, maps in records:
    oldbs=next(n for n in old.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
    m=bpy.data.materials.new(o.name + ' PBR');m.use_nodes=True
    bs=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
    bs.inputs['Base Color'].default_value=oldbs.inputs['Base Color'].default_value
    bs.inputs['Roughness'].default_value=oldbs.inputs['Roughness'].default_value
    tex=m.node_tree.nodes.new('ShaderNodeTexImage');tex.image=maps['NORMAL']
    normal=m.node_tree.nodes.new('ShaderNodeNormalMap')
    m.node_tree.links.new(tex.outputs['Color'],normal.inputs['Color'])
    m.node_tree.links.new(normal.outputs['Normal'],bs.inputs['Normal'])
    o.data.materials[0]=m
export('noise-emitter-pbr.glb')
sys.path.insert(0, str(Path(__file__).resolve().parent))
from unlit_glb import convert
convert(output / 'noise-emitter-baked.glb')
report={'source':str(source),'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'parts':[o.name for o,_,_ in records],'texture_size':1024,'bake_samples':16,'modes':{'baked':'Cycles combined lighting, unlit glTF. Fixed lighting and LED; no dynamic reflections.','pbr':'Base color, roughness, baked tangent normals. No LED emission.'},'files':{p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in output.glob('*.glb')}}
(output/'export-report.json').write_text(json.dumps(report,indent=2)+'\n')
