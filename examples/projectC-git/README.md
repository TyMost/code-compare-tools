# projectC-git 增量 diff 示例数据

该目录包含两个独立的 Git 仓库：

- `source-app`：模拟源侧工程。
- `target-app`：模拟目标侧工程。

每个仓库都提供：

1. `main` 分支：初始代码基线。
2. `feature/incremental-demo` 分支：包含以下变更，覆盖增量 diff 的主要场景：
   - **修改**：现有服务类新增方法并调整实现。
   - **新增**：加入新的功能类或配置项。
   - **删除**：移除遗留类，验证删除片段在详情页的展示。

## 使用方式

1. 切换到仓库根目录，例如：
   ```bash
   cd examples/projectC-git/source-app
   ```
2. 查看分支：
   ```bash
   git branch
   ```
3. 可使用 diff 查看 `main` 与 `feature/incremental-demo` 的差异：
   ```bash
   git diff main feature/incremental-demo
   ```
4. 将 `migration.scan.project.sources/targets` 指向对应路径，并把 `git-base-ref-*`、`git-target-ref-*` 分别配置为 `main` 与 `feature/incremental-demo`，即可在新流水线中验证：
   - diffMode 透传
   - diffSegments 渲染
   - 增量迁移的新增 / 删除 / 修改逻辑。
