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
VERSION = "0.7.0"

# The block in the corner of the end card, the way 0.6.0 credited its icons and its music.
CREDITS = [
    "Screens are design previews from the Folio Mockup Lab, not final.",
    "Music: Vivaldi, Spring (Allegro) — John Harrison with the Wichita State University Chamber Players, "
    "CC BY-SA 3.0, trimmed for this video.",
    "Not affiliated with Apple or Samsung. iOS is a trademark of Apple Inc.",
]

W, H = 1920, 1080
FPS = 60  # --draft drops this to 30
HOLD = 3.6          # seconds a shot is on screen, before the cross-fade eats into it
FADE = 0.7
TITLE_HOLD = 3.2
END_HOLD = 4.0

# Sampled from the 0.6.0 video: near-black, not a gradient. The teal only shows as a faint glow behind the screen.
BACKDROP = "#090B0F"
GLOW = "#16323A"
INK = "white"
MUTED = "rgba(255,255,255,0.55)"
FAINT = "rgba(255,255,255,0.4)"
LINK = "#6CB4FF"
PILL = "#2E5E66"
BEZEL = 13             # the dark frame around a screen, so it reads as a device
BEZEL_COLOUR = "#0B0B0C"

TEXT_X = 132           # the text column's left edge
TEXT_W = 560
DEVICE_BOX = (980, 860)  # the most room a screen gets, on the right

# The order they appear: the screen, the heading, the line under it, and the small pill above it.
SHOTS_IN_ORDER = [
    ("market-featured-cover.webp", "A store, in\nyour pocket", "Featured, Sources, Packages,\nInstalled and Settings", "New in 0.7.0"),
    ("market-featured-inner.webp", "And all of it\nwhen you open", "The same store, using\nthe whole inner screen", None),
    ("market-packages.webp", "Themes and tweaks,\nas packages", "Folio's own come with the app,\nwith a page each", None),
    ("market-package.webp", "What it can reach,\nbefore you get it", "Built from the manifest,\nnever from the author's words", "Privacy"),
    ("market-install-sheet.webp", "Nothing is applied\nuntil you say so", "What changes, what it can't touch,\nand where it came from", None),
    ("market-sources.webp", "Sources you choose", "Folio shows the key fingerprint\nbefore it trusts one", "Sources"),
    ("market-installed.webp", "Updates when\nyou want them", "Nothing installs itself, and\nUndo keeps the old version", None),
    ("settings-three-columns.webp", "Settings uses\nthe whole screen", "The list, the page, and what\nyou opened from it", "Unfolded"),
]


def run(args: list[str]) -> None:
    result = subprocess.run(args, capture_output=True)
    if result.returncode != 0:
        sys.exit(f"{args[0]} failed:\n{result.stderr.decode()[-1500:]}")


def size_of(path: pathlib.Path) -> tuple[int, int]:
    out = subprocess.run(["magick", "identify", "-format", "%w %h", str(path)], check=True, capture_output=True)
    w, h = out.stdout.decode().split()
    return int(w), int(h)


def background(path: pathlib.Path) -> None:
    """Near-black, with a soft glow where the screen sits so it doesn't float on a flat field."""
    run(["magick", "-size", f"{W}x{H}", f"xc:{BACKDROP}",
         "(", "-size", "1500x1500", f"radial-gradient:{GLOW}-{BACKDROP}", "-resize", "1500x1500", ")",
         "-gravity", "center", "-geometry", "+330+0", "-compose", "screen", "-composite",
         # No film grain: on a still frame it doubled the bitrate and the encode time, because noise is the one
         # thing a video codec can't predict. A slight blur on the glow deals with banding instead.
         "-blur", "0x18",
         str(path)])


def rounded(image: pathlib.Path, out: pathlib.Path, box: tuple[int, int]) -> None:
    """A screen: scaled to fit, corners rounded, with the thin light edge the 0.6.0 video's device has.

    The mask is drawn at a size read back from the resized image rather than with `%[fx:w]` inside `-draw`, which
    isn't expanded there - it silently painted a black rectangle over every screen.
    """
    with tempfile.TemporaryDirectory() as tmp:
        work = pathlib.Path(tmp)
        scaled = work / "scaled.png"
        run(["magick", str(image), "-resize", "{}x{}".format(*box), "+repage", str(scaled)])
        w, h = size_of(scaled)
        mask = work / "mask.png"
        run(["magick", "-size", f"{w}x{h}", "xc:black", "-fill", "white",
             "-draw", f"roundrectangle 0,0 {w - 1},{h - 1} 26,26", str(mask)])
        screen = work / "screen.png"
        run(["magick", str(scaled), str(mask), "-alpha", "off", "-compose", "copyopacity", "-composite",
             "+repage", str(screen)])

        # The bezel: the 0.6.0 video shows a device, not a bare screenshot, so the screen sits in a dark frame with
        # a hairline edge on it.
        bw, bh = w + BEZEL * 2, h + BEZEL * 2
        body, body_mask = work / "body.png", work / "body-mask.png"
        run(["magick", "-size", f"{bw}x{bh}", "xc:black", "-fill", "white",
             "-draw", f"roundrectangle 0,0 {bw - 1},{bh - 1} {26 + BEZEL},{26 + BEZEL}", str(body_mask)])
        run(["magick", "-size", f"{bw}x{bh}", f"xc:{BEZEL_COLOUR}", str(body_mask),
             "-alpha", "off", "-compose", "copyopacity", "-composite", "+repage", str(body)])
        run(["magick", str(body), str(screen), "-geometry", f"+{BEZEL}+{BEZEL}", "-composite",
             "-fill", "none", "-stroke", "rgba(255,255,255,0.14)", "-strokewidth", "1.5",
             "-draw", f"roundrectangle 1,1 {bw - 2},{bh - 2} {26 + BEZEL},{26 + BEZEL}",
             "+repage", str(out)])


def text_block(out: pathlib.Path, text: str, points: int, colour: str, bold: bool) -> None:
    run(["magick", "-background", "none", "-fill", colour,
         "-font", "Helvetica-Bold" if bold else "Helvetica", "-pointsize", str(points),
         "-interline-spacing", str(int(points * 0.18)), f"label:{text}", "+repage", str(out)])


def pill(out: pathlib.Path, text: str) -> None:
    run(["magick", "-background", "none", "-fill", INK, "-font", "Helvetica-Bold", "-pointsize", "24",
         f"label:{text}", "-bordercolor", "none", "-border", "14x9",
         "(", "+clone", "-alpha", "extract", "-fill", "white", "-colorize", "100%", ")",
         "-delete", "0", "-fill", PILL, "-colorize", "100%",
         "(", "-background", "none", "-fill", INK, "-font", "Helvetica-Bold", "-pointsize", "24",
         f"label:{text}", ")", "-gravity", "center", "-composite", "+repage", str(out)])


def shot_frame(image: pathlib.Path, heading: str, sub: str, tag: str | None, out: pathlib.Path,
               bg: pathlib.Path, version: str) -> None:
    """One screen on the right, its words on the left: the shape the 0.6.0 video uses."""
    with tempfile.TemporaryDirectory() as tmp:
        work = pathlib.Path(tmp)
        screen = work / "screen.png"
        rounded(image, screen, DEVICE_BOX)
        sw, sh = size_of(screen)

        head = work / "head.png"
        text_block(head, heading, 68, INK, True)
        hw, hh = size_of(head)
        line = work / "sub.png"
        text_block(line, sub, 29, MUTED, False)
        lw, lh = size_of(line)

        tag_h, tag_gap = 0, 0
        if tag:
            badge = work / "tag.png"
            pill(badge, tag)
            _, tag_h = size_of(badge)
            tag_gap = 22

        stack = tag_h + tag_gap + hh + 26 + lh
        top = (H - stack) // 2

        shadow = work / "shadow.png"
        run(["magick", str(screen), "-background", "rgba(0,0,0,0.55)", "-shadow", "70x34+0+16", "+repage", str(shadow)])
        shw, shh = size_of(shadow)
        left, top_of_screen = W - sw - 110, (H - sh) // 2

        # Placed from the top-left so the column lines up whatever the text does.
        args = ["magick", str(bg),
                str(shadow), "-geometry", f"+{left - (shw - sw) // 2}+{top_of_screen - (shh - sh) // 2 + 14}",
                "-composite",
                str(screen), "-geometry", f"+{left}+{top_of_screen}", "-composite"]
        if tag:
            args += [str(work / "tag.png"), "-geometry", f"+{TEXT_X}+{top}", "-composite"]
        args += [str(head), "-geometry", f"+{TEXT_X}+{top + tag_h + tag_gap}", "-composite",
                 str(line), "-geometry", f"+{TEXT_X}+{top + tag_h + tag_gap + hh + 26}", "-composite",
                 "-gravity", "southwest", "-fill", FAINT, "-font", "Helvetica", "-pointsize", "24",
                 "-annotate", f"+{TEXT_X}+54", f"Folio {version} · design preview",
                 "+repage", str(out)]
        run(args)


def title_frame(out: pathlib.Path, bg: pathlib.Path, icon: pathlib.Path, version: str) -> None:
    run(["magick", str(bg),
         "(", str(icon), "-resize", "168x168", ")", "-gravity", "center", "-geometry", "+0-150", "-composite",
         "-gravity", "center", "-fill", INK, "-font", "Helvetica-Bold", "-pointsize", "108",
         "-annotate", "+0+20", "Folio Market",
         "-font", "Helvetica", "-pointsize", "38", "-fill", MUTED,
         "-annotate", "+0+110", "Themes and tweaks you can get, from sources you choose",
         "-pointsize", "26", "-fill", FAINT,
         "-annotate", "+0+190", f"Folio {version} · screens are design previews",
         str(out)])


def end_frame(out: pathlib.Path, bg: pathlib.Path, icon: pathlib.Path, credits: list[str]) -> None:
    args = ["magick", str(bg),
            "(", str(icon), "-resize", "150x150", ")", "-gravity", "center", "-geometry", "+0-170", "-composite",
            "-gravity", "center", "-fill", INK, "-font", "Helvetica-Bold", "-pointsize", "96",
            "-annotate", "+0+0", "Folio",
            "-font", "Helvetica", "-pointsize", "38", "-fill", MUTED,
            "-annotate", "+0+80", "Free and open source. No root, no ads.",
            "-pointsize", "36", "-fill", LINK,
            "-annotate", "+0+150", "github.com/McCal-Codes/folio",
            "-gravity", "south", "-font", "Helvetica", "-pointsize", "20", "-fill", FAINT]
    for i, line in enumerate(reversed(credits)):
        args += ["-annotate", f"+0+{34 + i * 28}", line]
    args += [str(out)]
    run(args)


def build(music: pathlib.Path | None, draft: bool = False) -> None:
    for tool in ("magick", "ffmpeg"):
        if not shutil.which(tool):
            sys.exit(f"{tool} isn't installed. brew install imagemagick ffmpeg")
    missing = [shot[0] for shot in SHOTS_IN_ORDER if not (SHOTS / shot[0]).exists()]
    if missing:
        sys.exit("missing shots: " + ", ".join(missing))

    credits = list(CREDITS) if music else [line for line in CREDITS if not line.startswith("Music")]

    with tempfile.TemporaryDirectory() as tmp:
        work = pathlib.Path(tmp)
        bg = work / "bg.png"
        background(bg)
        icon = SHOTS / "folio-icon.png"

        frames: list[tuple[pathlib.Path, float]] = []
        title = work / "00-title.png"
        title_frame(title, bg, icon, VERSION)
        frames.append((title, TITLE_HOLD))
        for i, (name, heading, sub, tag) in enumerate(SHOTS_IN_ORDER, start=1):
            frame = work / f"{i:02d}.png"
            shot_frame(SHOTS / name, heading, sub, tag, frame, bg, VERSION)
            frames.append((frame, HOLD))
        end = work / "99-end.png"
        end_frame(end, bg, icon, credits)
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
