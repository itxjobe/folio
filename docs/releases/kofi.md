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

## Who can make a code

Only whoever has `~/.folio/folio-supporter.pem`. A code is an ECDSA P-256 signature over its own text: the public half
in the app can check one, and can't be used to make one. Editing a code - a later date, a different feature - breaks
the signature, and there's a test for that.

So nobody can forge a code. Three things that are worth being clear-eyed about, because they aren't forgery:

- **A code can be passed around.** That's deliberate. If it ever matters, mint dated ones (`--days 90`) or a code per
  person through `tools/kofi-webhook/`.
- **Folio is MIT, and the check runs on the phone.** Anyone can build from source with the check removed. No
  client-side check survives that, and pretending otherwise would mean shipping something closed. The answer is that
  the free core is worth having on its own, so there's little to gain.
- **Beta Updates opens the Market too**, by design - the whole point is testers. If early access should be the only
  early door, that switch has to close first.

Keeping the key safe, in order of how much it buys:

1. **Back it up offline.** Losing it is worse than leaking it: every code already handed out dies with it.
2. **Encrypt it on disk:** `cd ~/.folio && python3 ~/dev/folio-0.7.0/tools/folio-code.py protect`. It asks for a
   passphrase twice, writes the encrypted copy beside the old file, checks it reads back as the same key, and only
   then replaces it - a wrong passphrase or a full disk leaves the key exactly as it was. Minting afterwards asks for
   the passphrase, or reads `FOLIO_KEY_PASSPHRASE`. A new key can start that way with `keygen --passphrase`.

   The passphrase belongs in a password manager: **without it the key is gone**, and losing the key is worse than
   leaking it. Replace the offline backup afterwards, since the old backup is still unencrypted.
3. **Never let it near a server.** `tools/kofi-webhook/` hands out pre-minted codes for exactly this reason.
4. **CI checks the repository for private keys** on every push (`tools/check-secrets.sh`). `.gitignore` covers the
   usual names, but ignoring a file doesn't stop `git add -f` or a key pasted into a document.

If it ever does leak: mint a new key, put its public half in `EarlyAccess.PUBLIC_KEY`, ship it, and say so in the
release notes. Every code made with the old key stops working at that release, including the honest ones - so
supporters need new codes, which is the real cost of a leak.

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
- **Price:** $3 (McCal, 2026-09-18; $2 first, raised once the fees were on the table). A card fee is roughly a fixed
  30c plus a few percent, and Ko-fi's own cut applies unless the account has Gold, so about $2.20 of a $3 sale
  arrives - against roughly $1.30 of a $2 one, because the fixed part is what bites at small prices. It also matches
  Ko-fi's default coffee. Check the live numbers on the Ko-fi page rather than trusting these.
- **Image:** `~/Downloads/folio-kofi-shop.jpg`
- **Digital file:** `~/Downloads/folio-early-access.txt`

Description (McCal, 2026-09-18). Every line is either one of his own sentences from the README, a fact about what the
app does, or - the last line - his answer about where the money goes. Nothing here was written for him.

> Folio Launcher: a clean, iPhone-style Home Screen for Android — with the jailbreak tweaks I always wanted, and none
> of the lockdown.
>
> This gets you the **Folio Market** before it ships. It's how 0.7.0 hands out themes, tweaks and layouts: packages
> you can get, remove and undo, from sources you choose. Every page says what a package changes and what it can't
> reach before you get it.
>
> The tweaks are the ones I missed from jailbreaking — I've been in that world since iOS 7 or 8. Cabinet after Velox,
> Harborline after Harbor, Roll Call after Axon, Palette after Velvet, Colored Albums after ColorFlow. All re-created
> from scratch for Android; none of their code is in here, and everyone is credited in the app.
>
> You'll get a code to paste into Settings › Market › Early access, and the store appears.
>
> Everything in the Market is already in Folio's Settings — this is a head start, not a paywall. Folio is free and
> open source and stays that way, and nothing that has already shipped will ever move behind a code. There's no
> account: the code is checked on your phone, and nothing about you is stored or sent.
>
> Fair warning: it's still very early. A bit rusty in places, and it settles down as more people use it. Developed and
> tested on a Galaxy Z Fold8, and it needs Folio 0.7.0 or later from GitHub.
>
> The $3 goes towards test devices and more time to build.

## Memberships, and one-off support

What Ko-fi's tier form asks for is on the page itself (tier name, 30 characters; minimum price per month; benefit
lines; description; a 2:1 tier image; a welcome message that is sent on joining, which is where a code can go; Discord
roles; address; a join limit). Check the live fees and limits on Ko-fi rather than trusting a number written here.

**What Ko-fi can do:** recurring monthly tiers, posts restricted to a tier, a welcome message per tier, shop items
with a digital file attached, and a webhook on every payment carrying the type (`Subscription`, `Shop Order`,
`Donation`), the tier name and the amount.

**What it can't do:** turn a one-off tip into membership time. There is no "$20 buys four months of this tier" in
Ko-fi. So don't try to buy Ko-fi membership with one-off money — hand out **Folio** time instead, which is ours to
give: a supporter code with an end date. The months are Folio's, not Ko-fi's.

The rule from the top of this file still decides everything below: a tier buys a head start and extras on the side.
Nothing already shipped moves behind one.

### The tiers

Drafts, not copy — **the words are McCal's.** Each is built from what a code actually opens today: the Market before
it ships (`beta`), Folio Keys (`keys`), and the supporters-only posts.

| | Tier name (≤30) | Price | What it is |
|---|---|---|---|
| 1 | `Coffee` | $3/mo | The posts: betas announced first, design previews, the vote |
| 2 | `Early access` | $5/mo | A code for the Market and Folio Keys, renewed while you're a member |
| 3 | `Fold tester` | $10/mo | The above, plus beta builds first and requests read first |

**Tier 1 — Coffee, $3/mo**

- Benefits: `Supporters-only posts, before the public ones` · `Design previews of what I'm building` ·
  `A vote on what comes after each update`
- Description: a coffee towards test devices and more time to build. You see what's coming first and say what you
  want next.
- Welcome message: thanks, what to expect (a post per update), and the link to the GitHub releases page.

**Tier 2 — Early access, $5/mo**

- Benefits: everything in Coffee, plus `A supporter code: the Market before it ships` ·
  `Folio Keys, the keyboard extras` · `A new code whenever the old one runs out`
- Description: the Market is how 0.7.0 hands out themes, tweaks and layouts. Everything in it is already in Folio's
  Settings, so this is a head start, not a paywall.
- Welcome message: the code, and that it goes in Settings › Market › Early access. The worker can send this instead —
  see below.

**Tier 3 — Fold tester, $10/mo**

- Benefits: everything in Early access, plus `Beta builds as soon as they're built` ·
  `Your tweak and theme requests read first` ·  `A thanks line in the app, if you want one`
- Description: for people who want to break it before everyone else does, on folds, tall phones and small windows.
- Welcome message: the code, how to get on the beta track, and how to send a bug report from the app.

The thanks line is **not built** — there is no supporters list in the app today. Either build it before promising it,
or leave that benefit out.

### One-off support: a month per $5

A one-off payment earns a dated code: $5 → one month, $10 → two, $20 → four. Three ways to do it, cheapest first.

1. **Shop items, one per length.** "Early access · 1 month", "· 3 months", "· 6 months", each with a code file
   attached from a pool minted for that horizon. No code to write, works while asleep, and it's the way the shop item
   already works today.
2. **Tips, through the worker.** `tools/kofi-worker/` already picks a pool by tier name, by shop item, and by a single
   tip threshold (`tipFrom` / `tipPool`). Amount bands need a small change: a list of `{from, pool}` instead of one
   threshold, so $5, $10 and $20 land in the one-month, two-month and four-month pools.
3. **Ko-fi's own annual option**, if it suits — a membership paid yearly is still a membership, and the worker sees it
   as `Subscription`.

**The catch worth knowing before promising months.** A code is signed offline, so its end date is fixed when it is
*minted*, not when it is *redeemed*: `scripts/beta-code.py --expires 2027-01-01` gives everyone in that pool the same
last day. Someone who pays on the 28th gets a short month. Two ways out:

- **Re-mint the pools on a schedule** (a batch a month, dated a month out). No app change; a chore forever.
- **Let the phone start the clock.** The code payload already carries a free-form `tier` byte (`BetaCodes`, version 1,
  scope bits + tier + expiry day + serial). Mint `tier` as the number of months, and have Folio count from the day the
  code is redeemed. That gives a true "one month per $5" from an offline pool, with no format change and no key online.
  It is an app change in `BetaCodes` / `Supporter`, plus a minting convention.

The second one is the one to build if months are the offer. Until then, say "early access until <date>" rather than
"one month", because that is what the code does.

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
