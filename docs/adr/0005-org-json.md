# 0005: Keep org.json for Market parsing

- **Status:** accepted, 2026-09-17

## Context

Folio already parses themes, layout backups, the roadmap and Software Update responses with `org.json`, using hand-written strict validators (`LayoutBackup.kt`). The Market adds manifests, depictions, indexes and entry files.

## Decision

- **Parsing:** keep `org.json`, with explicit, strict readers and size caps, following the `LayoutBackup` validator style.
- **Schemas:** the JSON Schemas in `docs/sdk/schema/v1/` are the contract. Parser tests check that valid and invalid samples give the same answer in Folio and in the schemas.

## Consequences

- **Good:** no new dependency or compiler plugin; the parsing style matches the rest of the codebase; each field has explicit fallbacks, which keeps malformed input from crashing Folio.
- **Bad:** more hand-written code than kotlinx.serialization would need, and the schema and the parser can drift apart. The conformance tests and fuzzing cover that.
