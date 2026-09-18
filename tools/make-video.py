#!/usr/bin/env python3
"""Builds the release video out of the Mockup Lab's renders.

    python3 tools/make-video.py                       # -> ~/Downloads/folio-070.mp4
    python3 tools/make-video.py --music track.mp3     # the same, with music under it
    python3 tools/make-video.py --draft               # 30 fps and a quick encode, for checking the cut

1920x1080 at 60 fps, the same shape as the 0.6.0 video. Each shot is a screen from `docs/mockups/lab/walls/0.7.0/`
laid on Folio's gradient with a caption, held for a few seconds with a slow push in, and cross-faded into the next.
It carries the same "Preview · Coming in 0.7.0" badge the shop image does, because these are lab renders rather than
a phone.

Swapping in real captures later means replacing the files in `walls/0.7.0/` and running this again - the captions
live here, the pictures don't.

Needs ImageMagick and ffmpeg (`brew install imagemagick ffmpeg`).
"""

from __future__ import annotations

import argparse
import pathlib
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
SHOTS = ROOT / "docs/mockups/lab/walls/0.7.0"
OUT = pathlib.Path.home() / "Downloads/folio-070.mp4"

W, H = 1920, 1080
FPS = 60  # --draft drops this to 30
HOLD = 3.6          # seconds a shot is on screen, before the cross-fade eats into it
FADE = 0.7
TITLE_HOLD = 3.2
END_HOLD = 4.0

TEAL, MID, SAND = "#2E5E66", "#5E7F93", "#C99A7A"
INK = "white"

# The order they appear, and what each one says. One line each: a caption people read while the screen moves.
SHOTS_IN_ORDER = [
    ("market-featured-cover.webp", "A store, on the screen you carry closed"),
    ("market-featured-inner.webp", "And the whole of it when you open the phone"),
    ("market-packages.webp", "Themes and tweaks, as packages"),
    ("market-package.webp", "Every page says what a package can reach"),
    ("market-install-sheet.webp", "Nothing is applied before you say so"),
    ("market-sources.webp", "Sources you choose, pinned to their key"),
    ("market-installed.webp", "Updates when you want them, undo if you don't"),
    ("settings-three-columns.webp", "And Settings uses the whole screen"),
]


def run(args: list[str]) -> None:
    result = subprocess.run(args, capture_output=True)
    if result.returncode != 0:
        sys.exit(f"{args[0]} failed:\n{result.stderr.decode()[-1500:]}")


def background(path: pathlib.Path) -> None:
    """Folio's gradient, the one the app icon and the marketing images use."""
    run(["magick", "-size", f"{W}x{H}",
         f"gradient:{TEAL}-{SAND}", "-rotate", "-20", "-resize", f"{W * 2}x{H * 2}^",
         "-gravity", "center", "-extent", f"{W}x{H}",
         "-fill", MID, "-colorize", "12", str(path)])


def shot_frame(image: pathlib.Path, caption: str, out: pathlib.Path, bg: pathlib.Path) -> None:
    """One screen on the gradient: rounded, shadowed, with its caption above it."""
    with tempfile.TemporaryDirectory() as tmp:
        rounded = pathlib.Path(tmp) / "rounded.png"
        # Scale to fit the room under the caption, then round the corners the way the screen is rounded.
        run(["magick", str(image), "-resize", f"x{H - 250}", "-alpha", "set",
             "(", "+clone", "-alpha", "extract", "-draw", "fill black polygon 0,0 0,28 28,0 fill white circle 28,28 28,0",
             "(", "+clone", "-flip", ")", "-compose", "multiply", "-composite",
             "(", "+clone", "-flop", ")", "-compose", "multiply", "-composite", ")",
             "-alpha", "off", "-compose", "copyopacity", "-composite", str(rounded)])
        # +repage after the shadow: -shadow grows the canvas and leaves a page offset behind, and without clearing
        # it everything composited afterwards - including the caption - lands somewhere else entirely.
        shadow = pathlib.Path(tmp) / "shadow.png"
        run(["magick", str(rounded), "-background", "rgba(0,0,0,0.5)", "-shadow", "55x30+0+18", "+repage", str(shadow)])
        run(["magick", str(bg),
             str(shadow), "-gravity", "south", "-geometry", "+0+52", "-composite",
             str(rounded), "-gravity", "south", "-geometry", "+0+70", "-composite",
             "+repage",
             "-gravity", "north", "-fill", INK, "-pointsize", "56",
             "-font", "Helvetica-Bold", "-annotate", "+0+74", caption,
             "-gravity", "northeast", "-pointsize", "24", "-font", "Helvetica",
             "-fill", "rgba(255,255,255,0.7)", "-annotate", "+44+40", "Preview · Coming in 0.7.0",
             str(out)])


def title_frame(out: pathlib.Path, bg: pathlib.Path, icon: pathlib.Path) -> None:
    run(["magick", str(bg),
         "(", str(icon), "-resize", "190x190", ")", "-gravity", "center", "-geometry", "+0-210", "-composite",
         "-gravity", "center", "-fill", INK, "-font", "Helvetica-Bold", "-pointsize", "132",
         "-annotate", "+0-20", "Folio Market",
         "-font", "Helvetica", "-pointsize", "52", "-fill", "rgba(255,255,255,0.88)",
         "-annotate", "+0+80", "Themes and tweaks you can get, from sources you choose",
         "-pointsize", "34", "-fill", "rgba(255,255,255,0.7)",
         "-annotate", "+0+170", "Folio 0.7.0 · design preview",
         str(out)])


def end_frame(out: pathlib.Path, bg: pathlib.Path, icon: pathlib.Path) -> None:
    run(["magick", str(bg),
         "(", str(icon), "-resize", "160x160", ")", "-gravity", "center", "-geometry", "+0-190", "-composite",
         "-gravity", "center", "-fill", INK, "-font", "Helvetica-Bold", "-pointsize", "96",
         "-annotate", "+0+0", "Folio",
         "-font", "Helvetica", "-pointsize", "44", "-fill", "rgba(255,255,255,0.88)",
         "-annotate", "+0+90", "Free, open source, no root",
         "-pointsize", "36", "-fill", "rgba(255,255,255,0.75)",
         "-annotate", "+0+170", "github.com/McCal-Codes/folio",
         str(out)])


def build(music: pathlib.Path | None, draft: bool = False) -> None:
    for tool in ("magick", "ffmpeg"):
        if not shutil.which(tool):
            sys.exit(f"{tool} isn't installed. brew install imagemagick ffmpeg")
    missing = [name for name, _ in SHOTS_IN_ORDER if not (SHOTS / name).exists()]
    if missing:
        sys.exit("missing shots: " + ", ".join(missing))

    with tempfile.TemporaryDirectory() as tmp:
        work = pathlib.Path(tmp)
        bg = work / "bg.png"
        background(bg)
        icon = SHOTS / "folio-icon.png"

        frames: list[tuple[pathlib.Path, float]] = []
        title = work / "00-title.png"
        title_frame(title, bg, icon)
        frames.append((title, TITLE_HOLD))
        for i, (name, caption) in enumerate(SHOTS_IN_ORDER, start=1):
            frame = work / f"{i:02d}.png"
            shot_frame(SHOTS / name, caption, frame, bg)
            frames.append((frame, HOLD))
        end = work / "99-end.png"
        end_frame(end, bg, icon)
        frames.append((end, END_HOLD))

        # Each still is held, and the stills are cross-faded into each other.
        #
        # No slow push in: ffmpeg's zoompan re-scales the whole frame for every frame it emits, which cost 90 seconds
        # per three-second shot here - minutes of waiting for a move nobody would name. Held shots and a dissolve
        # read as deliberate, and when real screen recordings replace these stills they'll bring their own movement.
        inputs: list[str] = []
        filters: list[str] = []
        for i, (frame, hold) in enumerate(frames):
            inputs += ["-loop", "1", "-t", f"{hold:.2f}", "-i", str(frame)]
            filters.append(f"[{i}:v]scale={W}:{H},fps={FPS},format=yuv420p,setsar=1[v{i}]")

        chain = "[v0]"
        offset = frames[0][1] - FADE
        for i in range(1, len(frames)):
            out_label = f"[x{i}]"
            filters.append(f"{chain}[v{i}]xfade=transition=fade:duration={FADE}:offset={offset:.2f}{out_label}")
            chain = out_label
            offset += frames[i][1] - FADE

        filter_complex = ";".join(filters)
        args = ["ffmpeg", "-y", *inputs]
        if music:
            args += ["-i", str(music)]
        args += ["-filter_complex", filter_complex, "-map", chain]
        if music:
            total = sum(hold for _, hold in frames) - FADE * (len(frames) - 1)
            args += ["-map", f"{len(frames)}:a", "-af",
                     f"afade=t=in:st=0:d=1.5,afade=t=out:st={total - 2.5:.2f}:d=2.5", "-shortest",
                     "-c:a", "aac", "-b:a", "192k"]
        # The push-in has to be rendered frame by frame, so the good encode takes a few minutes; --draft is for
        # looking at the cut and the captions, not for posting.
        args += ["-c:v", "libx264", "-pix_fmt", "yuv420p", "-r", str(FPS),
                 "-crf", "26" if draft else "18", "-preset", "veryfast" if draft else "medium",
                 "-movflags", "+faststart", str(OUT)]
        run(args)

    seconds = sum(hold for _, hold in frames) - FADE * (len(frames) - 1)
    print(f"{OUT}  ·  {seconds:.1f}s  ·  {W}x{H} at {FPS} fps" + ("  ·  with music" if music else "  ·  silent"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--music", type=pathlib.Path, help="an audio file to put under it, faded in and out")
    parser.add_argument("--draft", action="store_true", help="quick and rough, for checking the cut")
    options = parser.parse_args()
    if options.draft:
        FPS = 30
    build(options.music, options.draft)
