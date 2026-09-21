# Translating Folio

Folio follows your phone's language. On Android 13 and later you can also pick Folio's language on its own, in
Android Settings › Apps › Folio › Language. Every word Folio shows lives in one file per language, so a translation
is a single file.

| Language | File | State |
|---|---|---|
| English | `app/src/main/res/values/strings.xml` | the source |
| Simplified Chinese | `app/src/main/res/values-b+zh+Hans/strings.xml` | beta: drafted, waiting for a native speaker's review |

## Correcting a translation

Open an issue with the **Translation / correction** form, or send a pull request that edits the language's
`strings.xml`. Say what the English means where it isn't obvious; the English file is the reference.

## Adding a language

1. Copy `values/strings.xml` to `values-b+<language>/strings.xml`, for example `values-b+zh+Hant` or `values-b+es`.
2. Translate the text between the tags. Leave the `name="…"` attributes alone.
3. Keep every placeholder exactly: `%1$s` is text, `%1$d` is a number, and the number says which one, so they can move
   within a sentence. `\n` is a line break.
4. Escape an apostrophe as `\'` and a straight double quote as `\"`. Curly quotes (“ ” 「 」) need nothing.
5. A plural has one `<item>` per form the language uses. Chinese and Japanese use only `other`.
6. Leave product names as they are: Folio, Market, Keyd, and the tweak names (Cabinet, Harborline, Roll Call, Palette,
   Colored Albums).

The build checks that every placeholder in a translation matches the English, so a slip there fails before it ships.
Where Folio borrows an iOS idea, follow Apple's own wording in that language; the Simplified Chinese file says which
terms it uses at the top.
