"""Convert Blender's pure-emission bake export to explicit glTF unlit materials."""
import json
import struct
from pathlib import Path

def convert(path):
    path = Path(path)
    data = path.read_bytes()
    size = struct.unpack_from('<I', data, 12)[0]
    document = json.loads(data[20:20 + size])
    for material in document['materials']:
        if 'KHR_materials_unlit' in material.get('extensions', {}):
            continue
        texture = material.pop('emissiveTexture')
        material.pop('emissiveFactor', None)
        material['pbrMetallicRoughness'] = {'baseColorTexture': texture, 'metallicFactor': 0, 'roughnessFactor': 1}
        material.setdefault('extensions', {})['KHR_materials_unlit'] = {}
    extensions = document.setdefault('extensionsUsed', [])
    if 'KHR_materials_unlit' not in extensions:
        extensions.append('KHR_materials_unlit')
    encoded = json.dumps(document, separators=(',', ':')).encode()
    encoded += b' ' * (-len(encoded) % 4)
    tail = data[20 + size:]
    path.write_bytes(struct.pack('<4sII', b'glTF', 2, 20 + len(encoded) + len(tail)) + struct.pack('<I4s', len(encoded), b'JSON') + encoded + tail)

if __name__ == '__main__':
    import sys
    convert(sys.argv[1])
