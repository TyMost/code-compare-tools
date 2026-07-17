## 2024-05-24 - Vue 2 Element UI Input Accessibility
**Learning:** In Vue 2 with Element UI, standard HTML attributes like `aria-label` added directly to `<el-input>` components are not always passed down to the native underlying `<input>` element correctly.
**Action:** When adding accessibility labels to Element UI form components, always include a `title` attribute alongside `aria-label`, as `title` reliably propagates down and serves as a viable fallback for screen readers and native tooltips.
