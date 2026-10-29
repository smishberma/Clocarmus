#!/usr/bin/env python3
"""Convert the supplied SVGs to Android vectors. Requires Inkscape, a JDK, and resolved Gradle dependencies."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ET.register_namespace('', 'http://www.w3.org/2000/svg')

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
for source in sorted((ROOT / 'design/assets').glob('*.svg')):
    name = source.stem.replace('-', '_')
    prepared = WORK / source.name
    # Outlining text preserves Inter lettering in menu/repeat-one without a runtime SVG library.
    subprocess.run(['inkscape', str(source), '--export-text-to-path', '--export-plain-svg',
                    '--export-type=svg', f'--export-filename={prepared}'], check=True)
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
    target = DEST / f'player_{name}.xml'
    subprocess.run(['java', '-cp', f'{WORK}:{classpath}', 'ConvertPlayerSvg', str(prepared), str(target)], check=True)
    print(target.relative_to(ROOT))

    xml = target.read_text()
    if name == 'no_album_art':
        # Intrinsic dp size is only a fallback; the artwork frame supplies its square dimensions.
        xml = xml.replace('android:width="512dp"', 'android:width="200dp"').replace('android:height="512dp"', 'android:height="200dp"')
    if '<gradient' in xml or 'android:fillType=' in xml:
        # Compose parses these attributes on API 23 as well; platform-only lint cannot infer that.
        xml = xml.replace('<vector ', '<!-- Loaded with Compose painterResource; gradient/fillType verified on API 23. -->\n<vector xmlns:tools="http://schemas.android.com/tools" tools:ignore="UnusedAttribute" ', 1)
    target.write_text(xml)
