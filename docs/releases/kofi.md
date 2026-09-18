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

### Setting it up, in order

1. `python3 tools/folio-code.py keygen`, on the Mac, with the repo checked out.
2. Paste the printed line into `EarlyAccess.PUBLIC_KEY` (`market/src/main/java/com/mccal/folio/market/EarlyAccess.kt`)
   and build. Until that's done, **every code is refused** — deliberately, because a placeholder key would let anyone
   mint their own.
3. `mint market` once, and keep the code. One code for everyone is fine and is the simplest thing that works.
4. Put the code where supporters see it (below).
5. Try it on the phone: Settings › Market › Early access › paste › Use code. It should say "Thanks — early access is
   on", and the Market row should appear.

## Getting a code to a supporter

Ko-fi has three ways in, in order of how little work they are:

| Way | What the supporter does | What McCal does |
|---|---|---|
| **Supporters-only post** | Follows the page, then reads the post | Writes one post with the code in it, marked supporters-only |
| **Shop item** (digital) | Buys a "Folio early access" item | Uploads a small text file with the code; Ko-fi sends it automatically |
| **Membership tier** | Joins a monthly tier | Same post, restricted to the tier |

The shop item is the only one that works while asleep, and it's the one to start with. A code with no expiry means the
file never needs changing; a dated code means re-uploading it when it runs out.

Later, a Ko-fi webhook into a small serverless function could mint one code per payment. Worth it only if sharing ever
becomes a problem, which it probably won't.

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
