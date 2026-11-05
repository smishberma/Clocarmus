#!/usr/bin/env python3
"""Capture the installed debug screen in headunit_a6 and compare it to the mockup.

Requires adb on PATH, a running headunit_a6 AVD, and Pillow (python3 -m pip install Pillow).
Run ./gradlew assembleDebug first, then use --install to install that APK.
"""
import argparse
import io
import re
from pathlib import Path
import subprocess
import time

from PIL import Image, ImageChops, ImageStat, ImageOps

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--screen", choices=("splash", "folders", "folder-directory", "collections-favorites", "collections-empty", "collections-custom", "collections-member", "genres", "genre-options", "genre-tracks", "genre-artists", "genre-albums", "player", "menu", "tracks", "artists", "artist-detail", "artist-tracks", "artist-albums", "albums", "album-options", "album-tracks", "playlists-empty", "playlists", "playlist-favorites", "playlist-added", "playlist-custom", "playlist-custom-empty", "playlist-favorites-empty"), default="player", help="Reference screen to capture")
parser.add_argument("--serial", help="Optional emulator serial; defaults to headunit_a6")
parser.add_argument("--install", action="store_true", help="Install the current debug APK before capture")
parser.add_argument("--output", type=Path, default=ROOT / "build/visual-comparison/final")
args = parser.parse_args()
if args.screen != "player" and args.output == ROOT / "build/visual-comparison/final":
    args.output = ROOT / f"build/visual-comparison/{args.screen}"


def adb(serial, *command):
    return subprocess.check_output(["adb", "-s", serial, *command])


serials = [args.serial] if args.serial else [line.split()[0] for line in
    subprocess.check_output(["adb", "devices"], text=True).splitlines()[1:] if "\tdevice" in line]
serial = args.serial or next((s for s in serials if adb(s, "emu", "avd", "name").decode().splitlines()[0] == "headunit_a6"), None)
if serial is None:
    parser.error("Start the headunit_a6 AVD first; no matching device is connected.")
avd_name = adb(serial, "emu", "avd", "name").decode().splitlines()[0].strip()
if args.install:
    print(adb(serial, "install", "-r", str(ROOT / "app/build/outputs/apk/debug/app-debug.apk")).decode())
extras = ["--ez", "splash_preview", "true"] if args.screen == "splash" else []
if args.screen in ("folders", "folder-directory"):
    extras += ["--es", "folders_preview", args.screen]
if args.screen.startswith("collections-"):
    extras += ["--es", "collections_preview", args.screen]
if args.screen.startswith("genre"):
    extras += ["--es", "genres_preview", args.screen]
if args.screen.startswith("artist"):
    extras += ["--es", "artists_preview", args.screen]
if args.screen.startswith("playlist"):
    extras += ["--es", "playlists_preview", args.screen]
if args.screen.startswith("album"):
    extras += ["--es", "albums_preview", args.screen]
adb(serial, "shell", "am", "start", "-S", "-f", "0x10008000", "-n", "com.example.clocarmus/.MainActivity", "--ez", "reference_preview", "true", "--ez", "menu_preview", str(args.screen == "menu").lower(), "--ez", "tracks_preview", str(args.screen == "tracks").lower(), *extras)
time.sleep(3)  # Allow activity startup and immersive-mode animation to finish.
actual = Image.open(io.BytesIO(adb(serial, "exec-out", "screencap", "-p"))).convert("RGB")
source = Image.open(ROOT / {"splash": "design/029-Splash-screen.png", "folders": "design/027-folders-screen-root-directory.png", "folder-directory": "design/028-folders-screen-subdirectory-playing-file.png", "collections-favorites": "design/023-track-collections-popup-add-to-favorites.png", "collections-empty": "design/024-track-collections-popup-no-custom-playlists.png", "collections-custom": "design/025-track-collections-popup-add-to-custom-playlists.png", "collections-member": "design/026-track-collections-track-present-in-playlists.png", "genres": "design/018-genres-screen.png", "genre-options": "design/019-genre-options.png", "genre-tracks": "design/020-genre-browse-tracks.png", "genre-artists": "design/021-genre-browse-artists.png", "genre-albums": "design/022-genre-browse-albums.png", "player": "design/033-main-player-redesign.png", "menu": "design/02-menu-screen.png", "tracks": "design/03-track-screen.png", "artists": "design/04-artists-screen.png", "artist-detail": "design/05-artists-screen-artist-options.png", "artist-tracks": "design/06-artists-screen-artist-all-tracks.png", "artist-albums": "design/07-artists-screen-artist-all-albums.png", "albums": "design/08-albums-screen.png", "album-options": "design/09-albums-screen-album-options.png", "album-tracks": "design/010-albums-screen-album-tracks.png", "playlists-empty": "design/011-playlists-screen-no-custom-playlists.png", "playlists": "design/012-playlists-screen-populated-custom-playlists.png", "playlist-favorites": "design/013-playlists-favorites-list.png", "playlist-added": "design/014-playlists-recently-added-list.png", "playlist-custom": "design/015-playlists-user-custom-playlist.png", "playlist-custom-empty": "design/016-playlists-user-custom-playlist-empty.png", "playlist-favorites-empty": "design/017-playlists-user-favorites-empty.png", "playlist-played": "design/014-playlists-recently-added-list.png"}[args.screen]).convert("RGB")
if args.screen == "folder-directory":
    # 028 supplies the changed directory pane; 027 supplies shared chrome, which
    # PLAN.md explicitly retains (header, clock, transport, fixed split).
    shared = Image.open(ROOT / "design/027-folders-screen-root-directory.png").convert("RGB")
    shared.paste(source.crop((0, 220, 1000, 1040)), (0, 220))
    source = shared
# The splash uses one full-screen canvas; all other screens keep their 50/50 grid.
# wm size overrides are letterboxed by SurfaceFlinger inside the physical capture.
size_override = re.search(r"Override size: (\d+)x(\d+)", adb(serial, "shell", "wm", "size").decode())
viewport = actual.size
if size_override:
    logical = tuple(map(int, size_override.groups()))
    ratio = min(actual.width / logical[0], actual.height / logical[1])
    viewport = (round(logical[0] * ratio), round(logical[1] * ratio))
reference = Image.new("RGB", viewport)
if args.screen == "splash":
    fitted = ImageOps.contain(source, viewport, Image.Resampling.LANCZOS)
    reference.paste(fitted, ((reference.width - fitted.width) // 2, (reference.height - fitted.height) // 2))
else:
    for side in range(2):
        pane = source.crop((side * source.width // 2, 0, (side + 1) * source.width // 2, source.height))
        fitted = ImageOps.contain(pane, (reference.width // 2, reference.height), Image.Resampling.LANCZOS)
        reference.paste(fitted, (side * reference.width // 2 + (reference.width // 2 - fitted.width) // 2,
                                 (reference.height - fitted.height) // 2))
if viewport != actual.size:
    projected = Image.new("RGB", actual.size)
    projected.paste(reference, ((actual.width - reference.width) // 2, (actual.height - reference.height) // 2))
    reference = projected
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
report = f"Screen: {args.screen}\nAVD: {avd_name} ({serial})\nResolution: {actual.width}x{actual.height}\nMean absolute RGB difference: {mae:.3f}/255\nLeft: reference fitted proportionally (one canvas for splash, one per half otherwise). Right: actual Compose screen with supplied SVG assets.\n"
(args.output / "comparison.txt").write_text(report)
print(report)
print(f"Artifacts: {args.output}")
