#!/usr/bin/env python3
"""Derive the onboarding logo's flat colors from the shared brand SVG."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
work = root / 'build/welcome-import'
work.mkdir(parents=True, exist_ok=True)
source = (root / 'design/assets/shared/logo-brand/logo.svg').read_text()
source = source.replace('fill:url(#linearGradient6)', 'fill:#ff7f22').replace('fill:#a6414f', 'fill:#ba0b63')
prepared = work / 'welcome-logo.svg'
prepared.write_text(source)
target = root / 'app/src/main/res/drawable-nodpi/welcome_logo.png'
subprocess.run(['inkscape', str(prepared), '--export-area-page', '--export-background-opacity=0',
                '--export-width=800', f'--export-filename={target}'], check=True)
