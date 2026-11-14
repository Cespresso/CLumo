"""Fetch one published release and prepare same-origin installer assets."""
import argparse
import json
from pathlib import Path
import subprocess

FIRMWARE = ('standalone', 'companion', 'headphone-stand')


def prepare(directory, release, download):
    assets = {asset['name'] for asset in release.get('assets', [])}
    tag = release.get('tag_name')
    available = {}
    for variant in FIRMWARE:
        name = f'clumo-{variant}.bin'
        target = directory / name
        target.unlink(missing_ok=True)
        ready = name in assets and (variant != 'headphone-stand' or 'clumo.apk' in assets)
        manifest_path = directory / f'manifest-{variant}.json'
        manifest = json.loads(manifest_path.read_text())
        if ready:
            download(tag, name, directory)
            if not target.is_file() or target.stat().st_size == 0:
                raise RuntimeError(f'Missing or empty downloaded firmware: {name}')
        manifest['version'] = tag or 'unreleased'
        manifest['builds'] = ([{'chipFamily': 'ESP32-C3', 'improv': False,
                               'parts': [{'path': './' + name, 'offset': 0}]}] if ready else [])
        manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
        available[variant] = ready
    (directory / 'availability.json').write_text(json.dumps({'version': tag, 'firmware': available}) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', required=True)
    parser.add_argument('--directory', type=Path, default=Path('installer'))
    args = parser.parse_args()
    result = subprocess.run(['gh', 'api', f'repos/{args.repo}/releases/latest'], capture_output=True, text=True)
    if result.returncode:
        if 'HTTP 404' not in result.stderr:
            raise RuntimeError(result.stderr)
        release = {}
    else:
        release = json.loads(result.stdout)
    def download(tag, name, directory):
        subprocess.run(['gh', 'release', 'download', tag, '--repo', args.repo,
                        '--pattern', name, '--dir', str(directory), '--clobber'], check=True)
    prepare(args.directory, release, download)
