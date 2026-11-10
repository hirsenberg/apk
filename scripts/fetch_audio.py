#!/usr/bin/env python3
"""Fetch the two licensed Mixkit sounds for LOCAL builds. Never redistribute them with source."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOUNDS = (
    (937, "Happy bells notification", "class_start", "c2be68e9eb59f744e7153678fc3e3c3c03692461a16cf41e7153b7e0478df248"),
    (600, "Achievement bell", "class_end", "7a5c0b2fb7f7cf77f6b3d36e9e165176a0b2f463c6ff3e6f3c133eda7fc945a1"),
)


def main():
    for tool in ("curl", "ffmpeg"):
        if not shutil.which(tool):
            raise SystemExit(f"Missing {tool}; install it before preparing audio.")
    print("License: https://mixkit.co/license/#sfxFree")
    print("For local application builds only; do not redistribute audio with source files.")
    destination = ROOT / "app/src/main/res/raw"
    with tempfile.TemporaryDirectory(prefix="studybell-audio-") as directory:
        temporary = Path(directory)
        for item_id, title, filename, expected in SOUNDS:
            wav = temporary / f"{item_id}.wav"
            subprocess.run([
                "curl", "--fail", "--location", "--silent", "--show-error", "--max-time", "60",
                "--proto", "=https", "--proto-redir", "=https",
                f"https://assets.mixkit.co/active_storage/sfx/{item_id}/{item_id}.wav", "--output", str(wav),
            ], check=True)
            if hashlib.sha256(wav.read_bytes()).hexdigest() != expected:
                raise SystemExit(f"Official file hash changed for {title}; stop and re-check identity/license. No replacement used.")
            subprocess.run([
                "ffmpeg", "-v", "error", "-nostdin", "-y", "-i", str(wav), "-map_metadata", "-1",
                "-codec:a", "libmp3lame", "-b:a", "192k", str(temporary / f"{filename}.mp3"),
            ], check=True)
        # Publish only after BOTH files have been downloaded, verified and transcoded.
        destination.mkdir(parents=True, exist_ok=True)
        for _, title, filename, _ in SOUNDS:
            target = destination / f"{filename}.mp3"
            with tempfile.NamedTemporaryFile(dir=destination, suffix=".tmp", delete=False) as output:
                staged = Path(output.name)
                output.write((temporary / f"{filename}.mp3").read_bytes())
            try:
                os.replace(staged, target)
            finally:
                staged.unlink(missing_ok=True)
            print(f"Prepared {title}: {target.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
