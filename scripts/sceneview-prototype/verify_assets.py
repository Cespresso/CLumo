import json, struct
from pathlib import Path
root=Path(__file__).resolve().parents[2]
assets=root/'companion/android/app/src/main/assets/models'
for p in assets.glob('*.glb'):
    data=p.read_bytes()
    magic,version,length=struct.unpack_from('<4sII',data)
    assert magic==b'glTF' and version==2 and length==len(data)
    size,kind=struct.unpack_from('<II',data,12)
    doc=json.loads(data[20:20+size])
    if p.name.endswith('-live.glb'):
        assert not doc.get('images')
        roles={n['name']:n for n in doc['nodes'] if 'mesh' in n}
        assert {'enclosure','button_b','matrix_surface','led_matrix'} <= roles.keys()
        assert ({'stand_left','stand_right','stand_knob'} if p.name.startswith('headphone') else {'button_a'}) <= roles.keys()
        assert all(len(m['primitives'])==1 for m in doc['meshes'])
        primitive=doc['meshes'][roles['led_matrix']['mesh']]['primitives'][0]
        accessor=doc['accessors'][primitive['attributes']['TEXCOORD_0']]
        assert accessor['componentType']==5126 and accessor['type']=='VEC2'
        view=doc['bufferViews'][accessor['bufferView']]
        binary_start=20+size+8
        offset=binary_start+view.get('byteOffset',0)+accessor.get('byteOffset',0)
        stride=view.get('byteStride',8)
        uvs=[struct.unpack_from('<ff',data,offset+i*stride) for i in range(accessor['count'])]
        expected={((c+.5)/8,(r+.5)/8) for r in range(8) for c in range(8)}
        assert set(uvs)==expected, 'LED texture cells must preserve row-major glTF UVs'
        led_bounds=doc['accessors'][primitive['attributes']['POSITION']]
        surface_primitive=doc['meshes'][roles['matrix_surface']['mesh']]['primitives'][0]
        surface_bounds=doc['accessors'][surface_primitive['attributes']['POSITION']]
        for axis in (0,1):
            led_center=(led_bounds['min'][axis]+led_bounds['max'][axis])/2
            surface_center=(surface_bounds['min'][axis]+surface_bounds['max'][axis])/2
            assert abs(led_center-surface_center)<1e-5, f'{p.name}: LED/window center mismatch on axis {axis}: {led_center-surface_center} m'
        assert all('uri' not in b for b in doc['buffers'])
        print(p.name,len(data),'bytes;',len(roles),'regions; 64 LED texels; OK')
        continue
    if p.name == 'clumo.glb':
        assert len(doc['meshes']) == 38
        assert not doc.get('images')
        assert all('uri' not in buffer for buffer in doc['buffers'])
        print(p.name, len(data), 'bytes; 38 meshes; embedded geometry and solid materials; OK')
        continue
    if p.name == 'unknown-tag-box.glb':
        assert len(doc['meshes']) == 3 and not doc.get('images')
        print(p.name, len(data), 'bytes; tag-box with question mark; OK')
        continue
    stand=p.name.startswith('headphone-stand')
    assert len(doc['meshes'])==(1 if stand else 4)
    assert len(doc['images'])==((1 if 'baked' in p.name else 3) if stand else 4)
    assert all('uri' not in i for i in doc['images'])
    if 'baked' in p.name:
        assert all('KHR_materials_unlit' in m.get('extensions',{}) for m in doc['materials'])
    else:
        assert all('normalTexture' in m for m in doc['materials'])
    print(p.name, len(data), 'bytes;',len(doc['meshes']),'meshes;',len(doc['images']),'embedded textures; OK')
