#!/usr/bin/env python3
"""Export supplied splash SVGs faithfully, including their gradient transforms."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
WORK = ROOT / 'build/splash-import'
WORK.mkdir(parents=True, exist_ok=True)

def export(source, target, width):
    target.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(['inkscape', str(source), '--export-area-page',
                    '--export-background-opacity=0', f'--export-width={width}',
                    f'--export-filename={target}'], check=True)

export(ROOT / 'design/assets/splash-screen/splash-screen-background.svg', RES / 'drawable-nodpi/splash_background.png', 2000)
source = ROOT / 'design/assets/shared/logo-brand/logo.svg'
export(source, RES / 'drawable-nodpi/splash_logo.png', 800)
# Android 12's icon is masked: keep the entire circular mark in the safe circle.
root = ET.parse(source).getroot()
x, y, w, h = map(float, root.attrib['viewBox'].split())
root.set('viewBox', f'{x-w/4} {y-h/4} {w*1.5} {h*1.5}')
root.set('width', '288')
root.set('height', '288')
prepared = WORK / 'system-icon.svg'
ET.ElementTree(root).write(prepared, encoding='utf-8', xml_declaration=True)
export(prepared, RES / 'drawable-xxxhdpi/splash_system_icon.png', 1152)
