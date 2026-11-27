#!/usr/bin/env python3
"""Generate silent MP3 regression assets with exact ID3v2.4 frames (requires ffmpeg)."""
from pathlib import Path
import subprocess
import tempfile


def synchsafe(value):
    return bytes((value >> shift) & 0x7f for shift in (21, 14, 7, 0))


def frame(name, value):
    payload = b"\x03" + value.encode("utf-8")
    return name.encode("ascii") + synchsafe(len(payload)) + b"\0\0" + payload


assets = Path(__file__).resolve().parents[1] / "app/src/androidTest/assets"
with tempfile.TemporaryDirectory() as directory:
    audio = Path(directory) / "silence.mp3"
    subprocess.run([
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i",
        "anullsrc=r=44100:cl=mono", "-t", "0.25", "-c:a", "libmp3lame",
        "-b:a", "64k", "-map_metadata", "-1", "-id3v2_version", "0",
        "-write_xing", "0", str(audio),
    ], check=True)
    for name, tags in {
        "id3-recording-release.mp3": [("TDRC", "2007"), ("TDRL", "2007-08-21")],
        "id3-conflicting-dates.mp3": [("TDRL", "2008-08-21"), ("TDRC", "2007")],
        "id3-release-only.mp3": [("TDRL", "2007-08-21")],
        "id3-no-date.mp3": [("TDEN", "2026-11-27"), ("TDTG", "2026-11-27")],
    }.items():
        body = b"".join(frame(key, value) for key, value in tags)
        (assets / name).write_bytes(b"ID3\x04\0\0" + synchsafe(len(body)) + body + audio.read_bytes())
