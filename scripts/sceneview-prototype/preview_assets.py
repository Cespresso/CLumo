import bpy,sys,math
from pathlib import Path
from mathutils import Vector
root=Path(__file__).resolve().parents[2]
out=root/'build/sceneview-prototype'
out.mkdir(exist_ok=True,parents=True)
stand='--stand' in sys.argv
prefix='headphone-stand' if stand else 'noise-emitter'
for mode in ['baked','pbr']:
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(root/f'companion/android/app/src/main/assets/models/{prefix}-{mode}.glb'))
    s=bpy.context.scene
    w=bpy.data.worlds.new('World');w.use_nodes=True;w.node_tree.nodes.get('Background').inputs[0].default_value=(.78,.82,.74,1);w.node_tree.nodes.get('Background').inputs[1].default_value=.6;s.world=w
    d=bpy.data.lights.new('Softbox','AREA');d.energy=1.1;d.size=.20
    o=bpy.data.objects.new('Softbox',d);s.collection.objects.link(o);o.location=(-.18,.06,.30);o.rotation_euler=(Vector((0,0,.023))-o.location).to_track_quat('-Z','Y').to_euler()
    d=bpy.data.cameras.new('Camera');o=bpy.data.objects.new('Camera',d);s.collection.objects.link(o)
    target=Vector((0,0,.122 if stand else .023))
    o.location=target+(Vector((-.4,-.65,.22)).normalized()*.90 if stand else (Vector((-.158698846,-.180179567,.090))-target).normalized()*.34)
    o.rotation_euler=(target-o.location).to_track_quat('-Z','Y').to_euler();d.lens=85;s.camera=o
    s.render.engine='CYCLES';s.cycles.samples=24;s.cycles.use_denoising=True
    s.render.resolution_x=1000;s.render.resolution_y=750;s.render.resolution_percentage=100
    s.view_settings.view_transform='AgX';s.view_settings.look='AgX - Medium High Contrast'
    s.render.filepath=str(out/f'{prefix}-{mode}-asset-check.png');bpy.ops.render.render(write_still=True)
