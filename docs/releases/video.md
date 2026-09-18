# The 0.7.0 video

`python3 tools/make-video.py` builds it from the Mockup Lab's renders: 1920×1080 at 60 fps, about 35 seconds, the
same shape as the 0.6.0 one. Output lands at `~/Downloads/folio-070.mp4`.

Every shot carries **"Preview · Coming in 0.7.0"**, because they're lab renders rather than a phone. Replacing the
files in `docs/mockups/lab/walls/0.7.0/` with real captures and running the script again is the whole update; the
captions live in the script, the pictures don't.

## The cut

| | |
|---|---|
| Title | Folio Market · themes and tweaks you can get, from sources you choose |
| 1 | A store, on the screen you carry closed |
| 2 | And the whole of it when you open the phone |
| 3 | Themes and tweaks, as packages |
| 4 | Every page says what a package can reach |
| 5 | Nothing is applied before you say so |
| 6 | Sources you choose, pinned to their key |
| 7 | Updates when you want them, undo if you don't |
| 8 | And Settings uses the whole screen |
| End | Folio · free, open source, no root · github.com/McCal-Codes/folio |

Roughly three seconds a shot with a slow push in and a cross-fade, so a piece of music with a steady pulse sits on
the cuts without being edited to them.

## Music

Two separate rights, and they catch people out: **the composition** is public domain for anything by Bach, Rossini,
Vivaldi or Satie, but **the recording** has its own copyright, usually held by whoever played it. A "royalty-free
classical" search result is not evidence of either.

The 0.6.0 video used Rossini's William Tell Overture (finale), arranged for strings by Gregor Quendel from
classicals.de, CC BY-NC 4.0, credited in the post. That worked because the video advertised a free app. **It's worth
thinking about again now:** this video points at a $3 shop item, and a non-commercial licence is at best arguable
when the thing being shown is for sale. Safer to pick a CC0 or public-domain recording, or a CC BY one, and keep NC
for posts that sell nothing.

Where recordings are genuinely free to use:

| Source | Licence | Worth knowing |
|---|---|---|
| **Open Goldberg Variations** (Bach, Kimiko Ishizaka) | CC0 | Studio piano, released to the public domain on purpose. The Aria is the calm one; the faster variations suit a montage. |
| **Open Well-Tempered Clavier** (Bach, same pianist) | CC0 | Prelude in C is the one everyone knows. Gentle, steady, builds. |
| **Musopen** | Mixed: much of it PD or CC0 | Check each recording's own licence; the site mixes them. Good for orchestral. |
| **Wikimedia Commons** | Per file, mostly PD or CC BY-SA | Slower to browse, but each file states its licence plainly. |
| **classicals.de** (Gregor Quendel) | CC BY-NC 4.0 | What 0.6.0 used. Fine for posts that sell nothing; see above. |

Picking for this cut, in order of fit:

1. **Bach, Well-Tempered Clavier Book 1, Prelude in C.** A steady pulse that never stops, which is exactly what eight
   three-second cuts want. Calm and precise, like the app. CC0 recording available.
2. **Vivaldi, Spring (first movement).** Brighter and more cheerful; carries a montage well and ends cleanly.
3. **Bach, Goldberg Aria.** The most beautiful and the least energetic. Better under a slow single-screen video than
   under a cut every three seconds.
4. **Satie, Gymnopédie No. 1.** Lovely, but slow and a little sad; 35 seconds of it barely gets going.
5. **Rossini, William Tell (finale).** Works, and 0.6.0 already used it — a different piece makes the two videos feel
   like two releases rather than one template.

Once a file is chosen:

```bash
python3 tools/make-video.py --music ~/Downloads/track.mp3
```

It fades in over a second and a half and out over the last two and a half, and stops when the video does. Credit the
recording in the post the way 0.6.0 did: piece, performer or arranger, where it came from, and the licence.

## Where it goes

Reddit (r/GalaxyFold, r/androidapps), the Ko-fi post, and the GitHub release. `docs/launch-0.6.0.md` has the posts
that went with the last one, including the credit line's wording.
