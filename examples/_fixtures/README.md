# Example Fixtures

The `_fixtures` directory captures tiny repositories used by docs and tests.

- `timeline.json` enumerates the relative commit chronology for each repo.
- `snapshot-validation.json` lists representative time windows together with the
  commits that should be considered the earliest/latest snapshot during a scan.

## Validating Snapshot Windows

When exercising the incremental snapshot scanner, pick one of the windows in
`snapshot-validation.json` and ensure the resolved commit IDs/branches match the
`expected` block of that entry. Each scenario intentionally spans multiple repos
and branches so you can confirm the “global earliest/global latest” behaviour.
