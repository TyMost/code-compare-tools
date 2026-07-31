## 2024-07-31 - [Adding Unique ARIA Labels in Vue v-for Loops]
**Learning:** When using dynamic lists rendered with `v-for` containing interactive controls (like delete buttons or input fields) in Vue, these elements often lack proper, unique labels. Screen readers may read all of them identically, causing confusion.
**Action:** Always ensure that input fields and generic icon-only buttons within `v-for` loops explicitly include context-specific, localized `aria-label` and `title` attributes (e.g., using template literals appending the loop index: `:aria-label="\`删除规则 ${index + 1}\`"`).
