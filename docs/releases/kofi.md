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

One short signed ticket, checked on the phone against a public key built into Folio. No account, no server call,
nothing stored about who paid: Folio keeps the code and reads what it says.

```
FOLIO-style groups of five, Crockford base32 (no I, L, O or U, so a typed code can't be misread)
 └─ 9 bytes: version · scopes · tier · expiry day · serial      + a 64-byte ECDSA P-256 signature
```

**Scopes** are what the code opens: `beta` (features a release or two early, which is what the Market is today),
`look`, `power` and `keys` (Folio Keys). **Expiry** is a day, or none. **Serial** is what a withdrawal names.
Editing any of it breaks the signature, and there are tests for that.

Codes are shareable on purpose. A supporter passing one to a friend is fine — it's a thank-you, not a licence — so
they're minted per scope, not per person.

```bash
./scripts/beta-code.py newkey --passphrase                    # once; prints what to paste into BetaKeys.SUPPORTER
./scripts/beta-code.py mint --scopes beta                     # a code that never runs out
./scripts/beta-code.py mint --scopes beta,keys --expires 2027-03-01 --count 25
./scripts/beta-code.py pool --scopes beta --count 200 > pool.sql   # a batch for the Ko-fi worker
```

`supporter-key.pem` never leaves the machine it's made on and is never committed. A new key stops every code already
handed out from working, so keep an offline copy.

`BetaCodeToolTest` mints a code with that script and reads it with the app's own `BetaCodes`, so the tool and the
phone can't drift apart without a test saying so.

### Where things stand

**One system, since 2026-09-19.** Two grew in parallel — `BetaCodes`/`Supporter` on `main` and an `EarlyAccess` in
the 0.7.0 branch — and they merged in together. `BetaCodes` won on every axis (short typeable codes, scopes,
withdrawal, a Settings page, a redeem link, a worker with tests), so `EarlyAccess`, `tools/folio-code.py` and
`tools/kofi-webhook/` are gone. What the retired side was better at came across: the signing key can be encrypted
at rest, and the verification now runs through the Market's `SourceKey`, so Folio has one ECDSA implementation
rather than two.

**What that costs McCal, concretely.** The key made on 2026-09-18 with the retired tool
(`~/.folio/folio-supporter.pem`, fingerprint `6423 2915 002A CAD7 FD07 CC99 AC27 B99F`) is **not** the key Folio
carries. The live one is `BetaKeys.SUPPORTER`, and its private half is `supporter-key.pem` in the main checkout
(gitignored, mode 600). Checked on 2026-09-19: the public half on disk is byte for byte the one in the app.

Done on 2026-09-19:

- **Re-minted.** `~/.folio/market-code.txt` holds a new code, scope `beta`, no expiry, serial `4195613006` — that
  serial is what `BetaKeys.WITHDRAWN` would name if it ever had to be pulled. A throwaway test read the file and
  verified it against `BetaKeys.SUPPORTER` before it went anywhere, so the code in the shop file is known to work.
- **`~/Downloads/folio-early-access.txt` rebuilt** around it, with the instructions pointing at Settings › Supporter
  and a line about the beta switch. Same words otherwise.
- `~/.folio/folio-supporter.pem` and any code from it are dead. Delete them when convenient; nothing reads them.

Still to do, and it is McCal's to do because it needs a passphrase typed:

- **Encrypt the live key.** `cd ~/dev/duo-fold-launcher && ~/dev/folio-0.7.0/scripts/beta-code.py protect`. It asks
  twice, proves the encrypted copy opens before replacing the old file, and from then on minting asks for the
  passphrase or reads `FOLIO_KEY_PASSPHRASE`. Put the passphrase in a password manager first: without it the key is
  gone, and losing the key is worse than leaking it.
- **Back it up offline**, after encrypting rather than before.

Still done and still good: `~/Downloads/folio-kofi-shop.jpg`, the 1080×1080 shop image from the Mockup Lab.

Left to do, on Ko-fi itself:

1. Ko-fi → **Shop** → add an item, with the image and the text below.
2. Attach the re-minted `folio-early-access.txt` as the digital file.
3. Buy it yourself for the minimum, check the file arrives, and paste the code into Settings › Supporter on the
   phone. It should say "Code added — thank you", and the Market should appear.

## Who can make a code

Only whoever has `supporter-key.pem`. A code is an ECDSA P-256 signature over its own bytes: the public half in the
app can check one, and can't be used to make one.

So nobody can forge a code. Three things that are worth being clear-eyed about, because they aren't forgery:

- **A code can be passed around.** That's deliberate. If it ever matters, mint dated ones (`--expires`) or a code
  per person through `tools/kofi-worker/`.
- **Folio is MIT, and the check runs on the phone.** Anyone can build from source with the check removed. No
  client-side check survives that, and pretending otherwise would mean shipping something closed. The answer is that
  the free core is worth having on its own, so there's little to gain.
- **Beta Updates opens the Market too**, by design - the whole point is testers. If early access should be the only
  early door, that switch has to close first.

Keeping the key safe, in order of how much it buys:

1. **Back it up offline.** Losing it is worse than leaking it: every code already handed out dies with it.
2. **Encrypt it on disk:** `./scripts/beta-code.py protect`. It asks for a passphrase twice, writes the encrypted
   copy beside the old file, checks it reads back as the same key, and only then replaces it - a wrong passphrase or
   a full disk leaves the key exactly as it was. Minting afterwards asks for the passphrase, or reads
   `FOLIO_KEY_PASSPHRASE`. A new key can start that way with `newkey --passphrase`.

   The passphrase belongs in a password manager: **without it the key is gone**, and losing the key is worse than
   leaking it. Replace the offline backup afterwards, since the old backup is still unencrypted.
3. **Never let it near a server.** `tools/kofi-worker/` hands out pre-minted codes for exactly this reason.
4. **CI checks the repository for private keys** on every push (`tools/check-secrets.sh`). `.gitignore` covers the
   usual names, but ignoring a file doesn't stop `git add -f` or a key pasted into a document.

If it ever does leak: mint a new key, put its public half in `BetaKeys.SUPPORTER`, ship it, and say so in the
release notes. Every code made with the old key stops working at that release, including the honest ones - so
supporters need new codes, which is the real cost of a leak. A code that went around publicly rather than a key can
be withdrawn on its own, by putting its serial in `BetaKeys.WITHDRAWN`; that takes effect when people update.

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

`tools/kofi-worker/` is a Cloudflare Worker that does this when the shop item isn't enough: Ko-fi posts to it on every
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
- **Digital file:** `~/Downloads/folio-early-access.txt` (re-mint it; see Where things stand)

Description (McCal, 2026-09-18). Every line is either one of his own sentences from the README, a fact about what the
app does, or - the last line - his answer about where the money goes. Nothing here was written for him. One factual
edit since: the code goes into Settings › Supporter, because Early access moved there when the two supporter-code
systems became one.

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
> You'll get a code to paste into Settings › Supporter, and the store appears.
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
- Welcome message: the code, and that it goes in Settings › Supporter. The worker can send this instead — see below.

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
2. **Tips, through the worker.** `tools/kofi-worker/` picks a pool by tier name, by shop item, by a single tip
   threshold (`tipFrom` / `tipPool`), and — since 19 Sep 2026 — by amount: `tipBands` is a list of `{from, pool}`, so
   $5, $10 and $20 land in the one-month, two-month and four-month pools. A payment earns the largest band it clears.
3. **Ko-fi's own annual option**, if it suits — a membership paid yearly is still a membership, and the worker sees it
   as `Subscription`.

**How the months work, now that the phone starts the clock.** A code is signed offline, so a fixed end date is
decided when the code is *minted*, not when it is *redeemed* — everyone in a `--expires 2027-01-01` pool gets the same
last day, and whoever pays on the 28th gets a short month. So codes grew a second shape, built 19 Sep 2026:

```bash
python3 scripts/beta-code.py mint --scopes beta,keys --months 1        # one month from the day it's redeemed
python3 scripts/beta-code.py pool --scopes beta,keys --months 4 --count 50 --pool months4 > pool.sql
```

`--months` mints a version 2 code: months and tier share one byte (months in the high nibble), so the code is the same
length and version 1 codes read exactly as before. Folio writes down the day a code was first redeemed on that phone,
one date per serial, and counts from there. Pasting the same code again resumes the window it started rather than
handing out another month, and removing the code doesn't reset it. A code carrying both months and a fixed date ends
on whichever comes first. Settings › Supporter shows the day it runs out.

Two things this does not change: a pre-0.6.5 build has no key at all, and a build older than this change reads a
version 2 code as "not a Folio code" — so months codes are for 0.6.5 and later. And `--months` takes 1 to 15; for
longer, use `--expires`.

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
- **Settings › Supporter** — redeem a code, see what it unlocks and when it runs out, remove it, and turn the beta
  features it carries on or off. The Market's own settings page points here rather than offering a second box.
- The Market is hidden entirely without a code, Beta Updates, or a dev build (`MarketFeature`), and it says so where
  someone would look for it.
