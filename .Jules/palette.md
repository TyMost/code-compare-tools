## 2024-06-05 - ARIA labels for dynamic list item actions
**Learning:** Dynamic lists with inline action buttons (like delete) often rely entirely on icons without visible text. This presents an accessibility issue as screen readers won't know the context of the button. The surrounding input may also be rendered without a corresponding label tag.
**Action:** Always ensure `aria-label` and `title` are added to icon-only buttons in dynamically rendered lists, and ensure inputs that lack visual `<label>` elements have an appropriate `aria-label` to describe their purpose.
