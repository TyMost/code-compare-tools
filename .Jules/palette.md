## 2024-05-18 - Element UI v-for A11y
**Learning:** Dynamic lists (`v-for`) containing generic inputs and icon-only buttons in Element UI components (like `el-input` and `el-button`) need explicit, localized accessibility labels (`aria-label` and `title`) because they lack context otherwise.
**Action:** Always verify loops rendering interactive elements to ensure they have proper ARIA attributes.
