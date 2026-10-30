#!/usr/bin/env python3
"""Import the supplied brand SVGs. Requires Inkscape, a JDK, and resolved Gradle dependencies."""
from copy import deepcopy
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'design/assets/shared/logo-brand'
RES = ROOT / 'app/src/main/res'
WORK = ROOT / 'build/brand-import'
SVG = 'http://www.w3.org/2000/svg'
ANDROID = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('', SVG)
ET.register_namespace('xlink', 'http://www.w3.org/1999/xlink')
WORK.mkdir(parents=True, exist_ok=True)

cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
jars = sorted(str(p) for p in cache.rglob('*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))
if not jars:
    raise SystemExit('Run ./gradlew assembleDebug first to resolve the Android build tools.')
classpath = ':'.join(jars)
subprocess.run(['javac', '-cp', classpath, '-d', str(WORK), str(ROOT / 'scripts/ConvertPlayerSvg.java')], check=True)


def clean(source):
    root = ET.parse(source).getroot()
    for parent in root.iter():
        for child in list(parent):
            if child.tag == f'{{{SVG}}}script':
                parent.remove(child)
    return root


def convert(root, name, dp):
    prepared = WORK / f'{name}.svg'
    ET.ElementTree(root).write(prepared, encoding='utf-8', xml_declaration=True)
    target = RES / 'drawable' / f'{name}.xml'
    subprocess.run(['java', '-cp', f'{WORK}:{classpath}', 'ConvertPlayerSvg', str(prepared), str(target)], check=True)
    # Keep Svg2Vector's gradient namespaces while changing only intrinsic size.
    xml = target.read_text()
    xml = re.sub(r'android:(width|height)="[^"]+"', lambda m: f'android:{m[1]}="{dp}dp"', xml)
    if name == 'brand_logo':
        xml = xml.replace('<vector ', '<!-- Use with Compose painterResource on API 23; it supports gradients. -->\n<vector xmlns:tools="http://schemas.android.com/tools" tools:ignore="UnusedAttribute" ', 1)
    target.write_text(xml)
    print(target.relative_to(ROOT))


convert(clean(SOURCE / 'logo.svg'), 'brand_logo', 200)
icon = clean(SOURCE / 'icon.svg')
# Derive the adaptive background from the supplied icon's circular backdrop.
backdrops = [e for e in icon.iter(f'{{{SVG}}}circle')]
if len(backdrops) != 1:
    raise SystemExit('Expected one circular launcher background in icon.svg.')
backdrop = backdrops[0]
style = dict(item.split(':', 1) for item in backdrop.attrib['style'].split(';') if ':' in item)
color = style['fill']
for parent in icon.iter():
    if backdrop in list(parent):
        parent.remove(backdrop)
        break

# Fit the entire original circular canvas inside Android's central 66dp safe
# circle, preserving the source mark's placement, paths, and gradients.
viewbox = [float(v) for v in icon.attrib['viewBox'].split()]
if viewbox[0:2] != [0, 0] or viewbox[2] != viewbox[3]:
    raise SystemExit('Expected a square brand icon viewport starting at the origin.')
foreground = ET.Element(f'{{{SVG}}}svg', width='108', height='108', viewBox='0 0 108 108')
group = ET.SubElement(foreground, f'{{{SVG}}}g', transform=f'translate(21,21) scale({66 / viewbox[2]:.12g})')
for child in icon:
    if child.tag == f'{{{SVG}}}defs':
        foreground.insert(0, deepcopy(child))
    elif child.tag == f'{{{SVG}}}g':
        group.append(deepcopy(child))
convert(foreground, 'ic_launcher_foreground', 108)

colors = ET.parse(RES / 'values/colors.xml')
colors.getroot().find("color[@name='ic_launcher_background']").text = color.upper()
ET.indent(colors, space='    ')
colors.write(RES / 'values/colors.xml', encoding='utf-8', xml_declaration=True)

for density, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    destination = RES / f'mipmap-{density}'
    destination.mkdir(exist_ok=True)
    target = destination / 'ic_launcher.png'
    subprocess.run(['inkscape', str(SOURCE / 'icon.svg'), '--export-area-page',
                    '--export-background-opacity=0', f'--export-width={size}',
                    f'--export-height={size}', f'--export-filename={target}'], check=True)
    # The source already has a circular background, suitable for both legacy entries.
    (destination / 'ic_launcher_round.png').write_bytes(target.read_bytes())
    print(target.relative_to(ROOT))

# Unqualified anydpi vectors take precedence over density PNGs on old devices.
for name in ('ic_launcher', 'ic_launcher_round'):
    (RES / 'mipmap-anydpi' / f'{name}.xml').unlink(missing_ok=True)
