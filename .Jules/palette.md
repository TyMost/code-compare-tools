## 2023-10-27 - Vue 2 v-for Accessibility Patterns
**Learning:** When using Element UI `<el-input>` inside a Vue 2 `v-for` loop, `aria-label` may not pass down directly to the native inner `<input>`, but the `title` attribute does. Screen readers and users hovering need contextual information in lists, so using template literals (like `\`规则 ${index + 1}\``) ensures uniqueness and acts as an effective fallback.
**Action:** Always add both `aria-label` and `title` to Element UI inputs and icon-only buttons in `v-for` lists to ensure maximal accessibility coverage.
