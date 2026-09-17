# Folio Package Format v1

Status: **draft** for Folio 0.7.0. Fields marked *(0.7.x)* are reserved: parsers accept them, but Folio doesn't act on them yet.

This page is the reference for packages and sources. The machine-readable versions are the JSON Schemas in
[`schema/v1/`](schema/v1/). Folio's parser and the `folio-pkg` tool are both tested against these schemas.

## Principles

- **Declarative first.** A package is data: JSON, images, and (optionally) a sandboxed script. Folio never downloads or loads DEX, JAR or native code. See [ADR 0004](../adr/0004-declarative-first.md).
- **Signed sources, verified files.** A source signs its entry file, the entry file pins the index's hash, and the index pins every package's hash. See [ADR 0001](../adr/0001-repo-format.md).
- **Permissions are the source of truth.** Everything a package can do maps to a permission it declares. Folio shows those permissions before install, builds the privacy label from them, and enforces them at runtime.
- **Local-first.** Folio only goes online for sources the user adds.

## Package: `.foliopkg`

A `.foliopkg` file is a zip archive:

```
manifest.json      required
depiction.json     optional, the package page
assets/            optional, png, webp or jpg
script.js          optional, only when kind includes "script"
```

Limits:
- **Size:** at most 20 MB compressed and 50 MB uncompressed.
- **Entries:** at most 500.
- **Names:** relative paths with `/` separators; no `..`, no absolute paths, no symlinks.
- **Rejected on sight:** anything that breaks these rules, plus files with extensions other than `.json`, `.png`, `.webp`, `.jpg`, `.jpeg` and `.js`.

### `manifest.json`

| Field | Type | Notes |
|---|---|---|
| `$schema` | string | Optional. `https://folio.mccal.dev/schema/v1/manifest.schema.json` gives editors autocomplete. |
| `format` | integer | Always `1`. |
| `id` | string | Reverse-DNS, lowercase: `dev.maya.sunset-icons`. Unique across all sources. |
| `name` | text | Display name, at most 40 characters. |
| `version` | string | dpkg ordering: `[epoch:]upstream[-revision]`; `~` sorts before release (`1.0~beta1` < `1.0`). |
| `author` | object | `{ "name": text, "url"?: https URL }`. |
| `minFolio` | string | Oldest Folio version it supports, e.g. `0.7.0`. |
| `section` | enum | `themes`, `tweaks`, `layouts`, `wallpapers`, `scripts`. |
| `kind` | array of enum | One or more of the kinds below. |
| `permissions` | array of enum | See [Permissions](#permissions). Empty means "appearance only". |
| `screens` | array of enum | `cover`, `inner`. Both if omitted. |
| `depends` | array of string | Package ids, optionally `id (>= 1.2)`. |
| `conflicts` | array of string | Same syntax. |
| `icon` | path | `assets/icon.png`, square, at least 180 px. |
| `depiction` | path | Usually `depiction.json`. |
| `license` | string | SPDX id, e.g. `MIT`. GPL code isn't accepted in the Community source. |
| `description` | text | One or two sentences, shown under the name. |
| `provides` *(later)* | array of enum | `iconPack`, `wallpapers`, `widgets`, `folioTheme`: what an external app brings. |
| `via` *(later)* | array of object | Required with kind `externalApp`: `{ "store": "playStore"\|"fdroid", "id": … }` or `{ "store": "obtainium", "repoUrl": … }`. |
| `requires` | object | `{ "features": [capability ids] }`: the Folio capabilities this package configures. See [Capabilities](#capabilities). |

**Text values:** any *text* field is either a plain string, or an object of language tags with an `en` fallback:
`{ "en": "Sunset Icons", "es": "Iconos Atardecer" }`. Tags look like `en`, `es` or `pt-BR`, there are at most 64 of them, and
two tags that differ only in case are refused. Folio shows the exact tag if it has it, then the bare language, then any
regional variant of it, then `en`. Text is at most 400 characters, except `name` (40) and a `markdown` block (4,000).

**Where Folio records the origin:** `folio-source`, `file`, `play-icon-pack` or `launcher-import` is stored with the
installed package, not in the manifest. Authors never set it, and a manifest that includes it has that field skipped.

**Limits Folio enforces while reading:** `manifest.json` is at most 64 KB and `depiction.json` at most 256 KB; JSON nests
at most 32 levels; `depends` and `conflicts` list at most 32 entries each. Files must be strict JSON (RFC 8259):
comments, unquoted keys, single quotes, trailing commas, trailing text and duplicate keys are all refused.

**Kinds:**

| Kind | Payload | Applied through |
|---|---|---|
| `theme` | `theme.json` in the same format as `themes/*.json` (`"folioTheme": 1`) | the existing theme importer |
| `layoutPreset` | `layout.json`, a subset of `LayoutPreset` | the layout model, with undo |
| `wallpaper` | images in `assets/` | the wallpaper picker |
| `iconPackLink` | `{ "package": "com.example.icons" }` | the ADW/Nova icon-pack lookup |
| `tweakBundle` | `tweaks.json`: built-in tweak ids and their options | `installTweak` and feature scopes |
| `settingsSchema` *(0.7.x)* | `settings.json` (see its schema) | Folio's settings renderer |
| `script` *(0.7.x)* | `script.js` | the script sandbox |
| `externalApp` *(later)* | a `via` list: `playStore`, `fdroid`, `obtainium` | Get opens the store, then Apply |

### `depiction.json`

This is the package page. It's a list of blocks, and Folio draws every block with its own components.

```json
{
  "format": 1,
  "tint": "#D85A30",
  "blocks": [
    { "type": "hero", "image": "assets/hero.webp" },
    { "type": "screenshots", "images": ["assets/s1.webp", "assets/s2.webp"] },
    { "type": "markdown", "text": "Warm, rounded icons for **2,400** apps." },
    { "type": "featureList", "items": ["Themed icon fallback", "Cover and inner screens"] },
    { "type": "changelog", "entries": [{ "version": "1.2.0", "date": "2026-09-14", "notes": "180 new icons." }] },
    { "type": "link", "title": "Website", "url": "https://example.com" },
    { "type": "donation", "url": "https://ko-fi.com/example" }
  ]
}
```

Block types:
- `hero`
- `screenshots`
- `markdown`: a safe subset (paragraphs, bold, italic, lists and links); no HTML or images.
- `featureList`
- `compatibility`
- `changelog`
- `link`: https only.
- `donation`: https only.

Folio adds its own rows for privacy, source, "Built from" and Report.

## Source: static files

Host a source on any HTTPS server; GitHub Pages is the easy path. The layout:

```
entry.json          signed pointer to the index
entry.json.sig      detached signature over entry.json's exact bytes
index.json          repo info and package list
revoked.json        signed list of disabled packages (optional)
revoked.json.sig
packages/*.foliopkg
icon.png
```

### `entry.json`

```json
{
  "format": 1,
  "keyId": "7F3A91C25B0ED418",
  "timestamp": 1789660320,
  "maxAge": 1209600,
  "index": { "path": "index.json", "sha256": "…", "size": 18342 }
}
```

The client accepts an entry only when all of these hold:
1. The signature verifies with the pinned key.
2. `timestamp` isn't older than the last one it accepted for this source (rollback protection).
3. `timestamp + maxAge` hasn't passed; `maxAge` is at most 30 days (freeze protection).
4. The downloaded index matches `size` and `sha256`.

The signature algorithm is decided in ADR 0002, during Phase 2.

### `index.json`

```json
{
  "format": 1,
  "name": "Folio Community",
  "description": "Themes and tweaks reviewed by the Folio project.",
  "icon": "icon.png",
  "issuesUrl": "https://github.com/McCal-Codes/folio-community/issues/new",
  "featured": [{ "package": "dev.maya.sunset-icons", "label": "Theme of the week" }],
  "packages": [
    {
      "id": "dev.maya.sunset-icons",
      "version": "1.2.0",
      "url": "packages/dev.maya.sunset-icons_1.2.0.foliopkg",
      "sha256": "…",
      "size": 1468211,
      "manifest": { "…": "copy of the package's manifest.json" },
      "provenance": { "repo": "maya/sunset-icons", "commit": "3f9c2a1", "workflow": "publish.yml" }
    }
  ]
}
```

- **Package URLs:** relative to the index, or absolute https (for example a GitHub Release asset URL). Folio never calls the GitHub API for each package.
- **Consistency:** the manifest copy must match the manifest inside the downloaded file, or the install fails.

### `revoked.json`

```json
{ "format": 1, "timestamp": 1789660320, "packages": [{ "id": "dev.bad.pkg", "versions": ["*"], "reason": "Malware" }], "sources": [] }
```

It's signed the same way as `entry.json`. Revoked packages are turned off, and the user is told why.

### Trust

- **Official key:** the Folio source's key is built into the app.
- **Other sources:** the key fingerprint is shown when the user adds the source, and then pinned. If a source's key changes, Folio stops trusting it until the user confirms.

## Links

| Link | Opens |
|---|---|
| `folio://source/<url-encoded https URL>` | the Add Source sheet, pre-filled |
| `folio://package/<id>` | the package page, if a source the user added lists it |
| a shared `.foliopkg` | the install sheet; unsigned files say "Unknown developer" |

## Capabilities

Every feature in Folio belongs to one of three classes:
- **Core:** Folio implements it (folders, dock, status, widgets, search, Focus).
- **Package:** a package configures a Core capability; Cabinet and themes are examples.
- **Integration:** it connects to something outside Folio (icon pack apps).

A package never adds behavior Folio doesn't already have. When you remove a package, the capability returns to its defaults.

`requires.features` lists the capabilities a package configures. Before installing, Folio checks that it has every one. If it doesn't, it shows "Needs a newer Folio" and doesn't install the package.

| Capability id | What Folio implements | Since |
|---|---|---|
| `theme` | Theme presets and theme files (`"folioTheme": 1`) | 0.4.0 |
| `home.layout` | Home grid, dock and layout presets | before 0.7.0 |
| `wallpaper` | Folio's wallpaper | 0.1.0 |
| `icons` | Icon style, shape and tint | before 0.7.0 |
| `icons.packs` | Icon packs found through the ADW/Nova/Lawnchair intents | by 0.4.0 |
| `tweaks.appPanels` | Cabinet (formerly App Panels) | 0.6.0 |
| `tweaks.dockMagnify` | Harborline (formerly Dock Magnification) | 0.6.0 |
| `tweaks.notificationAppRow` | Roll Call (formerly Notification App Row) | 0.6.0 |
| `tweaks.tintNotifications` | Palette (formerly Tinted Notifications) | 0.6.0 |
| `tweaks.tintMedia` | Colored Albums (formerly Album Art Colors) | 0.6.0 |
| `island.messages` | Messages in the Dynamic Island | 0.7.x |
| `focus.modes` | Switching Home Modes / Focus | 0.7.x |
| `settings.pages` | Settings pages drawn from `settings.json` | 0.7.x |
| `scripts` | The script sandbox | 0.7.x |

"Since" comes from CHANGELOG.md. "Before 0.7.0" means the feature exists today but the changelog doesn't record when it arrived. The 0.1.0 entry is inherited from DuoLauncher, and 0.7.x means planned.

**Packages never get Android permissions.** Folio holds its own permissions (for example Notification access). The permissions below are Folio's rules for what a package may change; they're what the privacy label lists.

## Permissions

| Permission | What it allows | Privacy label wording |
|---|---|---|
| `home.appearance` | Colors, materials, icon style | none (appearance only) |
| `home.layout` | Changing the Home grid and dock | Changes your Home layout |
| `icons` | Replacing app icons | none |
| `wallpaper` | Setting Folio's wallpaper | none |
| `tweaks` | Turning built-in tweaks on or off | Changes Folio tweaks |
| `island.messages` | Showing messages in the Dynamic Island | Shows island messages |
| `focus.switch` | Switching Home Modes / Focus | Switches Home Modes |
| `fold.state` | Reading whether the phone is folded | Reads fold state |
| `time` | Running on a schedule | Runs on a schedule |
| `apps.open` | Opening an app the user picked | Opens apps |

Scripts can only use actions whose permission they declare. New permissions come with a new format version.

## Versioning this format

- **Additive changes** (new optional fields, new kinds or block types) keep `format: 1`. Older Folio versions ignore what they don't know:
  - An unknown **field** is skipped, and the rest of the file is used.
  - An unknown **block** in a page is skipped, so the rest of the page still shows.
  - An unknown **kind, permission, section, screen or capability**, or a higher `format`, means the package needs a newer
    Folio. Folio won't install it rather than guess: those values decide what a package may change.
- **Breaking changes** use `format: 2`, with a new schema folder. Folio keeps reading v1.
- **Compatibility:** every v1 package in `market/src/test/resources/corpus/v1/` must keep installing in every future Folio version.
