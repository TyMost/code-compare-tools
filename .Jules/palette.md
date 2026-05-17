## 2024-05-17 - Missing ARIA Labels on Icon-only Buttons
**Learning:** Found multiple instances of `<el-button>` components being used with only an `<i class="el-icon-*">` tag inside, lacking any accessibility attributes. Screen readers cannot interpret these buttons without text content.
**Action:** When adding or auditing icon-only buttons, specifically look for `<el-button>` components containing only an `<i>` tag with an icon class. Ensure that both `aria-label` (for screen readers) and `title` (for sighted user tooltips) are added to improve usability and accessibility.
