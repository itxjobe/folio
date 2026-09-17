# Folio Market threat model

**Scope:** the Market, sources, packages, the installer and the script sandbox (Folio 0.7.0).

**Method:** STRIDE (spoofing, tampering, repudiation, information disclosure, denial of service, elevation of privilege). Each threat lists its mitigation and the test that proves it.

## Assets

- **The user's Home setup:** layout, theme, tweaks and Focus settings.
- **Data Folio can read:** notifications (if allowed), app list, contacts (if allowed), calendar (if allowed).
- **Folio's own stability:** Home must always open.
- **Trust in sources:** the official Folio source, the Community source, and each source's pinned key.

## Trust boundaries

1. The network, between Folio and a source's HTTPS host.
2. A source's publisher, who controls the signing key and the files.
3. The package's contents, which are parsed and applied on the phone.
4. A script's code, which runs in the sandbox.
5. Files the user shares with Folio: `.foliopkg` files and launcher backups.

## Threats and mitigations

| # | Threat | STRIDE | Mitigation | Test |
|---|---|---|---|---|
| T1 | Attacker on the network swaps the index or a package | T | HTTPS only. The signed `entry.json` pins the index's sha256; the index pins every package's sha256 and size. | Tampered-index and hash-mismatch fixtures fail |
| T2 | An old, vulnerable index is replayed | T | Refuse any `timestamp` older than the last accepted one for that source | Rollback fixture fails |
| T3 | A stale index is served forever to hide an update or revocation | D | Refuse the index after `timestamp + maxAge` (at most 30 days) and show "Couldn't refresh" | Expired-entry fixture fails |
| T4 | Someone impersonates a source | S | The key fingerprint is shown and pinned the first time the user adds a source. A changed key blocks the source until the user confirms. The official key ships in the app. | Wrong-key fixture fails |
| T5 | A publisher's key is stolen | S/T | Revocation list; key rotation needs the user's confirmation; Community source signing happens only in CI; build provenance shows the repo and commit | Revoked-package fixture is turned off |
| T6 | A malicious package ships code | E | Folio never loads DEX, JAR or native code; unknown file types are rejected; kinds are a closed list | A package with `classes.dex` is rejected |
| T7 | Zip-slip, zip bomb or symlink in a package | T/D | Paths are normalized and checked; size, entry-count and compression-ratio caps; symlinks rejected | Fuzz targets plus fixtures |
| T8 | A malformed manifest or depiction crashes Folio | D | Strict parsers with size caps, where unknown values fall back to safe defaults; Jazzer fuzzing | 5-minute fuzz runs stay clean |
| T9 | A package or script misbehaves and makes Home unusable | D | Safe Mode: two crashes within 60 s of a package change start Folio with third-party packages off | Crash-package fixture triggers Safe Mode |
| T10 | A script escapes the sandbox or does too much | E | QuickJS or LuaJ with no network, file or reflection access; memory and CPU caps; actions only through declared permissions; auto-disable after 3 failures | Over-budget and undeclared-action scripts are stopped |
| T11 | A package hides what it does | I | The privacy label is generated from `permissions`, never from the author's text; permissions are checked when the package runs | A permission missing from the manifest is denied |
| T12 | A depiction leaks data or phishes | I/S | Closed set of block types; Markdown subset with no HTML or remote images; links must be https and show their domain | HTML and http depictions are rejected by the schema |
| T13 | A source tracks users | I | Only static files; no accounts; no cookies; Folio sends only a plain `User-Agent: Folio`; refresh is opt-in and can be Wi-Fi only | Network log review |
| T14 | Market traffic uses up GitHub rate limits | D | Static files only, conditional requests (ETag), refresh at most every 6 h | Client test counts requests |
| T15 | A launcher backup import is malicious | T/D | Only files the user picks; strict parsing and size caps; preview before anything is applied; full undo | Oversized and malformed backup fixtures fail |
| T16 | A reported package stays live | R | Report opens the source's issue form with the package id, version and sha256; Community takedowns go into `revoked.json` | Process documented in the Community repo |

## Out of scope

- **A compromised phone:** root, or a malicious accessibility service.
- **Apps installed from Play Store, F-Droid or Obtainium** after the Market hands off to them (later feature). Those stores' own protections apply.
- **Sources the user explicitly trusts after the warning.** Folio still enforces everything above except the review.
