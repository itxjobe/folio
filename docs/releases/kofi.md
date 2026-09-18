# Ko-fi

How supporting Folio works, and what it does and doesn't buy. Local notes; nothing here is published until McCal says
so, and the page copy is his to write.

Page: <https://ko-fi.com/mccal> (already linked from Settings › Support Folio, and from Settings › Market).

## The rule that comes first

Folio's free core has to be good on its own. A supporter feature is an extra on top, and **nothing already shipped
ever becomes paid** — decided 2026-09-17. Two things follow from it:

- MIT code that's already published can't be locked. A genuinely closed feature has to be new code, in its own module,
  under its own licence, decided up front.
- Google Play requires Play Billing for paid unlocks in Play builds, so Ko-fi codes are for the GitHub and sideloaded
  builds only.

Right now the only thing behind a code is **early access to the Market**, and the Market hands out themes and tweaks
that are all in Settings anyway. So a code buys time, not features: you see it first.

## How a code works

`folio-early:<feature>:<expires>.<signature>` — one line, signed with a key that lives offline, checked on the phone
against the public half built into the app. No account, no server call, nothing stored about who paid: Folio keeps the
code and the date it runs out.

Codes are shareable on purpose. A supporter passing one to a friend is fine — it's a thank-you, not a licence — so
they're minted per feature, not per person.

```bash
python3 tools/folio-code.py keygen            # once; prints what to paste into EarlyAccess.PUBLIC_KEY
python3 tools/folio-code.py mint market       # a code that never runs out
python3 tools/folio-code.py mint market --days 90
python3 tools/folio-code.py verify <code> market
```

`folio-supporter.pem` never leaves the machine it's made on and is never committed. A new key stops every code already
handed out from working, so keep an offline copy.

### Where things stand

Done on 2026-09-18:

- The key exists: `~/.folio/folio-supporter.pem`, outside the repo, mode 600. **Back it up somewhere offline.** If it
  is lost, a new key stops every code already handed out from working.
  Fingerprint `6423 2915 002A CAD7 FD07 CC99 AC27 B99F`.
- Its public half is in `EarlyAccess.PUBLIC_KEY`, and a test checks the built-in key parses.
- One Market code is minted, at `~/.folio/market-code.txt`. It never runs out.
- `~/Downloads/folio-early-access.txt` is the file to attach to the shop item, with the code and what to do with it.
- `~/Downloads/folio-kofi-shop.jpg` is a 1080×1080 image for the item, from the Mockup Lab, badged
  "Preview · Coming in 0.7.0".

Left to do, on Ko-fi itself:

1. Ko-fi → **Shop** → add an item, with the image and the text below.
2. Attach `folio-early-access.txt` as the digital file.
3. Buy it yourself for the minimum, check the file arrives, and paste the code into
   Settings › Market › Early access on the phone. It should say "Thanks — early access is on".

More codes whenever they're wanted: `cd ~/.folio && python3 ~/dev/folio-0.7.0/tools/folio-code.py mint market`.

## Getting a code to a supporter

Ko-fi has three ways in, in order of how little work they are:

| Way | What the supporter does | What McCal does |
|---|---|---|
| **Supporters-only post** | Follows the page, then reads the post | Writes one post with the code in it, marked supporters-only |
| **Shop item** (digital) | Buys a "Folio early access" item | Uploads a small text file with the code; Ko-fi sends it automatically |
| **Membership tier** | Joins a monthly tier | Same post, restricted to the tier |

The shop item is the only one that works while asleep, and it's the one to start with. A code with no expiry means the
file never needs changing; a dated code means re-uploading it when it runs out.

### A code per person, automatically

`tools/kofi-webhook/` is a Cloudflare Worker that does this when the shop item isn't enough: Ko-fi posts to it on every
payment, it checks the payment is really Ko-fi's, takes one code off a batch minted offline, and emails it.

The point of the design is that **the signing key never goes online**. Codes are minted on the Mac and uploaded; the
worker only hands them out, so breaking into it leaks a handful of codes rather than the ability to make them. Its
README has the setup, and the whole thing is optional — start with the shop item.

## The shop item

A draft, not copy — **the words are McCal's.** Ko-fi asks for a title, a price, a description and an image.

- **Title:** Folio early access
- **Price:** McCal's call. Ko-fi takes a minimum; the point is a thank-you, so low is fine.
- **Image:** `~/Downloads/folio-kofi-shop.jpg`
- **Digital file:** `~/Downloads/folio-early-access.txt`

Draft description:

> Get the Folio Market before it ships.
>
> Folio is a launcher for foldables that looks and behaves like iOS. The Market is how 0.7.0 hands out themes, tweaks
> and layouts: packages you can get, remove and undo, from sources you choose, with a page for each that says what it
> changes and what it can't reach before you get it.
>
> You get a code to paste into Settings › Market › Early access, and the store appears.
>
> **Everything in it is already in Folio's Settings.** This is a head start, not a paywall — Folio is free and open
> source, and nothing that has already shipped will ever move behind a code. There's no account: the code is checked
> on your phone, and nothing about you is stored or sent.
>
> Needs Folio 0.7.0 or later from GitHub.

## The page itself

Things Ko-fi asks for, with what Folio needs each one to say. **The words are McCal's — these are placeholders, not
copy:**

- **Page title and tagline.** What Folio is, in one line, for someone who has never seen it.
- **About.** A short paragraph: what Folio does, that it's free and open source, and what a coffee actually pays for
  (test devices, time). `docs/launch-0.6.5-kofi.md` has the tone from the last update.
- **Goal.** Optional, and honest if used: a named thing the money is for, not a number with nothing behind it.
- **Shop item.** "Folio early access" — what it unlocks now (the Market, before it ships), and plainly that everything
  in it is already in Settings.
- **Gallery.** The feature wall from the Mockup Lab, and real Fold8 screenshots.

## What it says inside the app

- **Settings › Support Folio** — a row that opens the page.
- **Settings › Market › Early access** — paste a code, see when it runs out, forget it, and a link to the page.
- The Market is hidden entirely without a code, Beta Updates, or a dev build (`MarketFeature`), and it says so where
  someone would look for it.
