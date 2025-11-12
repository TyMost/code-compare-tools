2025-10-30 含工作区变更的 Git diff 支持：当前 includeWorkingTree=true 时会缺失未提交文件的 blob，需要在 GitDiffAdapter 中落地文件系统回退逻辑并补齐 DiffBlock，待后续实现。
