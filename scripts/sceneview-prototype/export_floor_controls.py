"""Export approved C control solids; live labels/icons remain runtime UI."""
import bpy
from pathlib import Path
root=Path(__file__).resolve().parents[2]
bpy.ops.wm.open_mainfile(filepath=str(root/'build/sceneview-prototype/c-tabs-blender/clumo-c-tabs.blend'))
bpy.context.scene.frame_set(1)
bpy.ops.object.select_all(action='DESELECT')
for o in bpy.context.scene.objects:
    if o.name.startswith(('tab_', 'group_plate_', 'pattern_')):
        o.select_set(True)
        o.animation_data_clear()
bpy.ops.export_scene.gltf(filepath=str(root/'companion/android/app/src/main/assets/models/floor-controls-c.glb'),export_format='GLB',use_selection=True,export_apply=True,export_animations=False,export_cameras=False,export_lights=False)
print('FLOOR_CONTROLS_EXPORTED')
