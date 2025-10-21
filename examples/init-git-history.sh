#!/usr/bin/env bash

set -euo pipefail

#
# 初始化 examples/projectA 与 projectB 的 Git 历史，用于演示增量扫描。
# - projectA: main 分支 + feature/order-sync 分支
# - projectB: release/gauss 分支
#

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

init_repo() {
  local project_dir="$1"
  local main_branch="$2"
  local feature_branch="$3"

  if [ -d "${project_dir}/.git" ]; then
    echo "[跳过] ${project_dir} 已存在 Git 仓库，若需重新生成请先删除 .git 目录。"
    return
  fi

  echo "[初始化] ${project_dir}"
  git -C "${project_dir}" init
  git -C "${project_dir}" checkout -b "${main_branch}"
  git -C "${project_dir}" config user.name "CodeCompare Demo"
  git -C "${project_dir}" config user.email "demo@example.com"

  # 提交基础业务代码（仅 Java），用于模拟既有版本
  git -C "${project_dir}" add src/main/java
  GIT_AUTHOR_DATE="2024-01-01T10:00:00" GIT_COMMITTER_DATE="2024-01-01T10:00:00" \
    git -C "${project_dir}" commit -m "feat: baseline services" >/dev/null

  if [ -n "${feature_branch}" ]; then
    git -C "${project_dir}" checkout -b "${feature_branch}"
  fi

  # 提交多语言资源文件，作为增量扫描需要捕获的改动
  git -C "${project_dir}" add src/main/resources src/main/webapp || true
  GIT_AUTHOR_DATE="2024-03-15T09:30:00" GIT_COMMITTER_DATE="2024-03-15T09:30:00" \
    git -C "${project_dir}" commit -m "feat: add sql/xml/js assets" >/dev/null

  # 返回主分支，保持 HEAD 指向最新提交
  git -C "${project_dir}" checkout "${main_branch}"
  git -C "${project_dir}" merge --ff-only "${feature_branch:-${main_branch}}" >/dev/null

  echo "[完成] ${project_dir} Git 历史已就绪"
}

init_repo "${ROOT_DIR}/projectA" "main" "feature/order-sync"
init_repo "${ROOT_DIR}/projectB" "release/gauss" ""
init_repo "${ROOT_DIR}/projectA-git" "main" "feature/git-demo"
init_repo "${ROOT_DIR}/projectB-git" "main" "release/git-demo"

echo "全部示例仓库初始化完成。"
