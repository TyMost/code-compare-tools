# GenerateAppService Design Draft

## Purpose
- Produce migration templates from `DeltaGroup` inputs; the current iteration only supports preview output and performs no file writes.
- Deliver guidance text that points consumers to `project_rules.md` for manual adaptation.
- Record decisions and generated snippets so future apply/revert features can reuse the data.

## Inputs & Core Objects
- **DeltaGroup**: collection of `DiffBlock` entries, each carrying source (`deltaO`) and target (`deltaG`) code fragments plus metadata.
- **DiffBlock metrics**: confirm availability of coverage / similarity scores; if missing, extend the model or derive from `CoverageDetail`.
- **Template options**: externalised configuration (YAML, DB, or strategy bean) to customise wrapper comments, hint messages, and Insert/Update thresholds; allow repo/task overrides.

## DiffBlock Scenario Mapping
| Scenario | Condition | Template | Notes |
| --- | --- | --- | --- |
| 1 | `deltaO` present, `deltaG` empty | Insert | Output source fragment directly |
| 2 | Both present, content differs | Update | Preserve original and suggested fragment |
| 3 | Both present, identical | Skip | No output |
| 4 | `deltaO` empty, `deltaG` present | Delete / hint | Default to manual delete hint; configurable |
| 5 | Both empty | Skip | Informational only |
| 6/7/8 | Partial coverage / mismatch | Insert or Update | Choose by coverage & similarity thresholds |

## Template Format (configurable defaults)
```text
/** Migration snippet generated
 * Please adjust per project_rules.md
 */
${deltaO.code}
/** End of migration snippet */
```

```text
/** Migration adaptation block
 * Please adjust per project_rules.md
 */
/** Original target implementation */
${deltaG.code}
/** End original target implementation */
/** Suggested migrated implementation */
${deltaO.code}
/** End suggested migrated implementation */
/** End of migration adaptation block */
```

- Delete scenario emits `// TODO: remove target implementation manually` unless disabled.
- Partial coverage uses configurable thresholds, e.g. similarity > 0.7 => Update, else Insert.
- Wrapper text, spacing, and localisation funnel through the template options.

## Workflow (preview-only for now)
1. Iterate blocks, classify scenario, and build `BlockResult` containing template body, decision type, metrics, and hints.
2. Aggregate preview output as a string; do **not** touch filesystem in this iteration.
3. Assemble `MigrationResult` with:
   - `success`: preview completed.
   - `affectedFiles`: inferred from block metadata.
   - `message`: high-level notes (e.g., skipped blocks).
   - `blockResults`: per-block recommendation payload.
4. Persist preview metadata via `MigrationRepository`, including template summary, applied configuration, and decision context.
5. Future extension: reuse the same structure when apply/revert functionality is introduced.

## Template Configuration Capabilities
- **Comment wrappers**: enable/disable hints, adjust wording/localisation, change placement.
- **Decision strategy**: thresholds for Insert vs Update, automatic Delete hints toggles.
- **Output formatting**: extra blank lines, indentation policies, separator markers.
- Configuration precedence: defaults -> repo override -> task override -> method arguments.

## Public API Plan
- `previewMigration(DeltaGroup)`: returns preview-mode `MigrationResult`; `success` reflects generation only.
- `applyMigration(DeltaGroup, MigrationTemplateOption)`: **planned**; will honour configuration once file-write strategy is approved.
- `revertMigration(MigrationResult)`: **planned**; depends on apply implementation and file backup policy.
- Evaluate coexistence with `generateMigrationSummary` to avoid duplicated logic.

## Collaborators & Data Dependencies
- **DiffAppService** supplies merged `DeltaGroup`.
- **MigrationAppService** coordinates task lifecycle and repository updates.
- **MigrationRepository** must persist preview records (and later applied records) together with configuration snapshots.
- Consider storing both full template text and summaries for efficient retrieval.

## Error Handling & Logging
- Capture per-block errors in `blockResults.errorMessage`; log warnings at the service level.
- Fall back to default templates when configuration is missing or invalid.
- With no file writes, focus on validating inputs and repository calls; add rollback logic when apply arrives.

## Test Plan (JDK 8)
1. **Scenario coverage**
   - O present / G absent (Insert).
   - O present / G present / mismatch (Update).
   - O present / G present / identical (Skip).
   - O absent / G present (Delete hint).
   - O absent / G absent (Skip).
   - Partial coverage, high similarity -> Update.
   - Partial coverage, low similarity -> Insert.
2. **Result validation**
   - Templates match expected defaults and reflect configuration overrides.
   - `MigrationResult` fields populated correctly.
   - `MigrationRepository` invoked once with preview record.
3. **Error cases**
   - Null/empty `DeltaGroup`.
   - Missing/invalid configuration falls back safely.
   - Repository exceptions propagate message and mark `success` false.
4. **Future work**
   - When apply/revert exist, extend tests for backups, rollback, and permission errors.

## Open Questions
- Source of partial-coverage metrics and whether new calculations are required.
- Storage medium and refresh strategy for template configuration.
- Compatibility between `MigrationResult` and existing `MigrationSummary`.
- Lifecycle and cleanup policy for stored preview records.

## Next Steps
- Confirm open questions and freeze configuration interfaces.
- Implement preview workflow and unit tests once design is approved.
- Schedule apply/revert implementation after file-write strategy is defined.
