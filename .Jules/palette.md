## 2024-05-18 - Avoid committing auto-generated lockfiles on micro-UX tasks
**Learning:** Running `pnpm install` generates a large `pnpm-lock.yaml` file. Including this in a PR bounded to "Keep changes under 50 lines" pollutes the review and creates an out-of-scope side effect.
**Action:** Always verify `git status` before committing and use `git restore --staged` or `git checkout` to remove unmodified/auto-generated lockfiles like `pnpm-lock.yaml` from micro-UX pull requests.
