#!/usr/bin/env python3
"""Convert the supplied SVGs to Android vectors. Requires Inkscape, a JDK, and resolved Gradle dependencies."""
import argparse
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ET.register_namespace('', 'http://www.w3.org/2000/svg')
ET.register_namespace('xlink', 'http://www.w3.org/1999/xlink')

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--group', choices=('main-player', 'menu-page', 'track-menu', 'shared', 'playlists-screen', 'track-collections', 'folders-screen', 'long-press', 'queue-screen'), help='Import only one asset directory')
parser.add_argument('--asset', help='Import only this SVG stem, for example no-album-art')
args = parser.parse_args()

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / 'build/svg-import'
DEST = ROOT / 'app/src/main/res/drawable'
WORK.mkdir(parents=True, exist_ok=True)
DEST.mkdir(parents=True, exist_ok=True)
cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
jars = sorted(str(p) for p in cache.rglob('*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))
if not jars:
    raise SystemExit('Run ./gradlew assembleDebug first to resolve the Android build tools.')
classpath = ':'.join(jars)
subprocess.run(['javac', '-cp', classpath, '-d', str(WORK), str(ROOT / 'scripts/ConvertPlayerSvg.java')], check=True)
for source in sorted((ROOT / 'design/assets').rglob('*.svg')):
    # Branding has separate adaptive layers and legacy PNG exports.
    if source.parent.name == 'logo-brand':
        continue
    if args.group and source.parent.name != args.group:
        continue
    if args.asset and source.stem != args.asset:
        continue
    name = source.stem.replace('-', '_')
    prefix = {'menu-page': 'menu', 'track-menu': 'track', 'playlists-screen': '', 'track-collections': 'collections', 'folders-screen': 'folder', 'long-press': 'options', 'queue-screen': 'queue'}.get(source.parent.name, 'player')
    prepared = WORK / f'{prefix}_{source.name}'
    # Outlining text preserves Inter lettering in menu/repeat-one without a runtime SVG library.
    subprocess.run(['inkscape', str(source), '--export-text-to-path', '--export-plain-svg',
                    '--export-type=svg', f'--export-filename={prepared}',
                    *(['--actions=select-all;object-stroke-to-path;export-do'] if source.parent.name == 'folders-screen' else [])], check=True)
    # Inkscape can retain an unused mesh polyfill script after mesh effects are
    # removed. Scripts have no drawable content and are unsupported by Svg2Vector.
    tree = ET.parse(prepared)
    root = tree.getroot()
    for parent in root.iter():
        for child in list(parent):
            if child.tag == '{http://www.w3.org/2000/svg}script':
                parent.remove(child)
    tree.write(prepared, encoding='utf-8', xml_declaration=True)
    if name == 'volume_slider':
        # Keep the supplied background path. Compose draws the changing fill and separate icon.
        tree = ET.parse(prepared)
        root = tree.getroot()
        for parent in root.iter():
            for child in list(parent):
                if child.get('id') in ('path190', 'g205'):
                    parent.remove(child)
        tree.write(prepared, encoding='utf-8', xml_declaration=True)
        name = 'volume_track'
    target = DEST / f'{prefix + "_" if prefix else ""}{name}.xml'
    subprocess.run(['java', '-cp', f'{WORK}:{classpath}', 'ConvertPlayerSvg', str(prepared), str(target)], check=True)
    print(target.relative_to(ROOT))

    xml = target.read_text()
    if name == 'no_album_art':
        # Intrinsic dp size is only a fallback; Compose supplies the square frame.
        xml = xml.replace('android:width="512dp"', 'android:width="200dp"').replace('android:height="512dp"', 'android:height="200dp"')
    if '<gradient' in xml or 'android:fillType=' in xml:
        # Compose parses these attributes on API 23 as well; platform-only lint cannot infer that.
        xml = xml.replace('<vector ', '<!-- Loaded with Compose painterResource; gradient/fillType verified on API 23. -->\n<vector xmlns:tools="http://schemas.android.com/tools" tools:ignore="UnusedAttribute" ', 1)
    target.write_text(xml)
    if name == 'no_album_art':
        (DEST.parent / 'drawable-nodpi/player_no_album_art.png').unlink(missing_ok=True)
