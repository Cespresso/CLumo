import bpy
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
bpy.ops.wm.open_mainfile(filepath=str(ROOT / "build/sceneview-prototype/assignment-scroll-blender/clumo-assignment-scroll.blend"))
bpy.context.scene.frame_set(1)
bpy.ops.object.select_all(action='DESELECT')
for o in bpy.context.scene.objects:
    if o.type=='MESH' and (o.name.startswith(('choice_plate_','choice_icon_','choice_')) or o.name=='Current assignment rim' or o.name.startswith('Branch /')):
        o.animation_data_clear();o.select_set(True)
bpy.ops.export_scene.gltf(filepath=str(ROOT / "companion/android/app/src/main/assets/models/floor-assignment.glb"),use_selection=True,export_animations=False,export_apply=True)
