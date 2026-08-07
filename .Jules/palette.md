## 2026-08-07 - Dynamic Accessibility in Vue v-for Loops

**Learning:** When adding accessibility properties (`aria-label` or `title`) inside Vue `v-for` loops (like in a dynamic pattern list), statically stringed text is insufficient as it prevents screen readers from distinguishing between different items. Additionally, custom components like `<el-input>` may fail to propagate standard `aria-label` attributes to the native `<input>` element underneath.

**Action:** Always use Vue template literals tied to the loop index or unique item content (e.g., `:aria-label="\`删除规则 ${index + 1}\`"`) to provide unique context per iteration. When targeting element UI inputs, fall back to adding a dynamic `:title` attribute as it correctly propagates and is read by screen readers on these custom components.
