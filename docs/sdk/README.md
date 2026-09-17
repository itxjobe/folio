# Folio SDK

Make themes, tweaks and layouts for the Folio Market.

**Status:** draft for Folio 0.7.0. The format can still change before 0.7.0 ships.

| Page | What it covers |
|---|---|
| [Package format v1](format-v1.md) | `.foliopkg` files, sources, permissions, links |
| [JSON Schemas](schema/v1/) | Manifest, page, index, entry, revocation list, tweak bundle, settings page |
| [Threat model](threat-model.md) | What the Market defends against, and how |
| [Cabinet sample](examples/cabinet/) | A complete `tweakBundle` package |
| [Decision records](../adr/) | Why the format works this way |

## Quick start

1. Copy `examples/cabinet/` and change `id`, `name` and `author` in `manifest.json`.
2. Add `"$schema"` to each JSON file (the sample already does). VS Code then autocompletes fields and flags mistakes as you type.
3. Check your package with `folio-pkg validate`. The tool arrives later in the 0.7.0 cycle.
4. Publish it in one of three ways:
   - **Your own source:** use the template repo; a GitHub Action signs it and publishes it to Pages.
   - **The Community source:** open a pull request, CI checks it, and the Folio project reviews it.
   - **A single file:** share the `.foliopkg`. Folio labels it "Unknown developer" because it isn't signed.

## Rules for every package

- **Data only:** JSON and images, plus an optional sandboxed script. No DEX, JAR or native code.
- **Declare everything:** list every permission your package uses. The privacy label is built from that list.
- **Credit and licensing:** credit anything that inspired you, and don't include GPL code.
