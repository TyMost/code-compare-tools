## 2024-07-18 - Accessibility for Dynamic Lists and Element UI Inputs
**Learning:** `v-for` dynamic lists containing Element UI inputs and icon-only buttons need explicit `aria-label` and `title` attributes for accessibility. `title` is specifically essential for `el-input` as `aria-label` may not pass down natively to the inner input element. They need to be localized to Chinese to match the UI context.
**Action:** Always add `title` alongside `aria-label` for `el-input` components and icon-only buttons inside `v-for` loops, providing localized text.
