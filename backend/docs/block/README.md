# Block Module Notes

## Responsibilities
- Define line-level diff and code snapshot domain models shared by diff, repositories, statistics, and agent modules.
- Provide Jackson-friendly serialization so `BlockDiff` can be stored or served through REST without additional glue code.

## Key Structures
- `block/model/BlockDiff.java`
  - Captures start lines, changed line counts, replacements, similarity metrics, labels, and `DiffMetrics`.
  - The `metadata` map now carries Git payloads: `gitSnapshots` plus `gitDiff` (hunk ranges, raw lines, byte sizes) so clients can recover commit context and exact diff windows without mutating the core schema.
  - The builder normalises inputs (start line >= 1, empty collections -> immutable lists, content uses `\n`).
- `block/model/CodeSnapshot.java`
  - Holds language, path, and content for one side of the comparison.
  - `of`/`builder` provide immutable construction, defaulting language to `plain` and content to an empty string.

## Typical Usage
- `DiffService` + `DiffResultAssembler` emit `BlockDiff` lists that are persisted via `DiffSnapshotRepository`.
- `MetricsAggregator` reads labels/line counts to compute completion ratios and category share.
- `CodeBlockMigrationService` uses block line ranges to annotate target files and update statuses via `labelDescriptors`.

## Extension Notes
- Prefer adding metrics to `DiffMetrics` first; keep the primary `BlockDiff` surface stable and rely on `metadata` for backwards-compatible extensions.
- If additional language hints are needed, extend `CodeSnapshot` but remember to update serialization defaults.
- Large payloads can be expensive to serialise; avoid populating `sourceLines`/`targetLines` unless the consumer requires the raw text.
