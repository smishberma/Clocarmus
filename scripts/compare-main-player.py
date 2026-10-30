#!/usr/bin/env python3
"""Capture the installed debug screen in headunit_a6 and compare it to the mockup.

Requires adb on PATH, a running headunit_a6 AVD, and Pillow (python3 -m pip install Pillow).
Run ./gradlew assembleDebug first, then use --install to install that APK.
"""
import argparse
import io
from pathlib import Path
import subprocess
import time

from PIL import Image, ImageChops, ImageStat, ImageOps

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--screen", choices=("player", "menu"), default="player", help="Reference screen to capture")
parser.add_argument("--serial", help="Optional adb serial; must be headunit_a6")
parser.add_argument("--install", action="store_true", help="Install the current debug APK before capture")
parser.add_argument("--output", type=Path, default=ROOT / "build/visual-comparison/final")
args = parser.parse_args()
if args.screen == "menu" and args.output == ROOT / "build/visual-comparison/final":
    args.output = ROOT / "build/visual-comparison/menu"


def adb(serial, *command):
    return subprocess.check_output(["adb", "-s", serial, *command])


serials = [args.serial] if args.serial else [line.split()[0] for line in
    subprocess.check_output(["adb", "devices"], text=True).splitlines()[1:] if "\tdevice" in line]
serial = next((s for s in serials if adb(s, "emu", "avd", "name").decode().splitlines()[0] == "headunit_a6"), None)
if serial is None:
    parser.error("Start the headunit_a6 AVD first; no matching device is connected.")
if args.install:
    print(adb(serial, "install", "-r", str(ROOT / "app/build/outputs/apk/debug/app-debug.apk")).decode())
adb(serial, "shell", "am", "start", "-S", "-f", "0x10008000", "-n", "com.example.clocarmus/.MainActivity", "--ez", "reference_preview", "true", "--ez", "menu_preview", str(args.screen == "menu").lower())
time.sleep(3)  # Allow activity startup and immersive-mode animation to finish.
actual = Image.open(io.BytesIO(adb(serial, "exec-out", "screencap", "-p"))).convert("RGB")
source = Image.open(ROOT / ("design/02-menu-opened.png" if args.screen == "menu" else "design/01-main-player.png")).convert("RGB")
# Fit each half uniformly, matching the app's fixed split without distorting the mockup.
reference = Image.new("RGB", actual.size)
for side in range(2):
    pane = source.crop((side * source.width // 2, 0, (side + 1) * source.width // 2, source.height))
    fitted = ImageOps.contain(pane, (actual.width // 2, actual.height), Image.Resampling.LANCZOS)
    reference.paste(fitted, (side * actual.width // 2 + (actual.width // 2 - fitted.width) // 2,
                             (actual.height - fitted.height) // 2))
difference = ImageChops.difference(reference, actual)
args.output.mkdir(parents=True, exist_ok=True)
actual.save(args.output / "actual.png")
reference.save(args.output / "reference.png")
difference.save(args.output / "difference.png")
side_by_side = Image.new("RGB", (actual.width * 2, actual.height))
side_by_side.paste(reference)
side_by_side.paste(actual, (actual.width, 0))
side_by_side.save(args.output / "comparison.png")
mae = sum(ImageStat.Stat(difference).mean) / 3
report = f"Screen: {args.screen}\nAVD: headunit_a6 ({serial})\nResolution: {actual.width}x{actual.height}\nMean absolute RGB difference: {mae:.3f}/255\nLeft: reference fitted proportionally within each half. Right: actual Compose screen with supplied SVG assets.\n"
(args.output / "comparison.txt").write_text(report)
print(report)
print(f"Artifacts: {args.output}")
