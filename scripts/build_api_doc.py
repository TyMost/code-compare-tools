import argparse
import json
import subprocess
from pathlib import Path
from typing import Any, Dict, List, Optional

import requests


def load_api_results(path: Path) -> List[Dict[str, Any]]:
    if not path.exists():
        raise FileNotFoundError(f"API verification results not found: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def find_entry(
    entries: List[Dict[str, Any]],
    method: str,
    url_suffix: str,
) -> Dict[str, Any]:
    for entry in entries:
        if entry["method"].upper() == method.upper() and entry["url"].endswith(url_suffix):
            return entry
    raise ValueError(f"Entry not found for {method} {url_suffix}")


def post_detail(base_url: str, task_id: str, file_path: str) -> Dict[str, Any]:
    payload = {"taskId": task_id, "filePath": file_path}
    response = requests.post(f"{base_url}/api/scan/detail", json=payload, timeout=120)
    response.raise_for_status()
    return {
        "request": payload,
        "response": response.json(),
    }


def git_diff(repo_path: Path, range_spec: str, file_path: str) -> str:
    args = [
        "git",
        "-C",
        str(repo_path),
        "diff",
        range_spec,
        "--",
        file_path,
    ]
    completed = subprocess.run(
        args,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if completed.returncode not in (0, 1):
        completed.check_returncode()
    return completed.stdout.strip()


def format_json(data: Any) -> str:
    return json.dumps(data, indent=4, ensure_ascii=False)


def write_doc(content: str, output_path: Path) -> None:
    output_path.write_text(content, encoding="utf-8")


def build_section(
    title: str,
    request: Optional[Dict[str, Any]],
    response: Dict[str, Any],
) -> List[str]:
    lines = [title]
    if request is not None:
        lines.append("**入参**")
        lines.append("```json")
        lines.append(format_json(request))
        lines.append("```")
    else:
        lines.append("**入参** 无")
    lines.append("**出参**")
    lines.append("```json")
    lines.append(format_json(response))
    lines.append("```")
    return lines


def main() -> None:
    parser = argparse.ArgumentParser(description="Build API vs git diff markdown document.")
    parser.add_argument("--host", default="localhost", help="Backend host.")
    parser.add_argument("--port", type=int, default=8081, help="Backend port.")
    parser.add_argument(
        "--results",
        type=Path,
        default=Path("docs/test/api-verification-results.json"),
        help="Path to captured API request/response JSON.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("docs/test/api-vs-git-diff-latest.md"),
        help="Markdown output path.",
    )
    args = parser.parse_args()

    base_dir = Path(__file__).resolve().parents[1]
    results_path = (base_dir / args.results).resolve()
    output_path = (base_dir / args.output).resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    entries = load_api_results(results_path)
    base_url = f"http://{args.host}:{args.port}"

    full_entry = find_entry(entries, "POST", "/api/scan/full")
    incremental_entry = find_entry(entries, "POST", "/api/scan")
    detail_entry = find_entry(entries, "POST", "/api/scan/detail")
    migrate_generate_entry = find_entry(entries, "POST", "/api/migrate/generate")
    migrate_apply_entry = find_entry(entries, "POST", "/api/migrate/apply")
    migrate_revert_entry = find_entry(entries, "POST", "/api/migrate/revert")
    presets_entry = find_entry(entries, "GET", "/api/scan/presets")
    repos_list_entry = find_entry(entries, "GET", "/api/repos")
    repos_create_entry = find_entry(entries, "POST", "/api/repos")
    repos_branches_entry = find_entry(entries, "POST", "/api/repos/branches")
    setting_update_entry = find_entry(entries, "POST", "/api/settings")
    setting_get_entry = find_entry(entries, "GET", "/api/settings/migration.defaultPreset")

    task_id = full_entry["request"]["taskId"]

    customer_detail = post_detail(
        base_url,
        task_id,
        "src/main/java/com/example/migration/customer/CustomerSyncService.java",
    )
    mapper_detail = post_detail(
        base_url,
        task_id,
        "src/main/resources/mapper/billing/SettlementMapper.xml",
    )

    oracle_repo = (base_dir / "examples" / "o").resolve()
    gauss_repo = (base_dir / "examples" / "g").resolve()

    diffs = {
        "settlement_oracle": git_diff(
            oracle_repo,
            "o1..o2",
            "src/main/java/com/example/migration/billing/SettlementProcessor.java",
        ),
        "settlement_gauss": git_diff(
            gauss_repo,
            "g1..g2",
            "src/main/java/com/example/migration/billing/SettlementProcessor.java",
        ),
        "customer_oracle": git_diff(
            oracle_repo,
            "o1..o2",
            "src/main/java/com/example/migration/customer/CustomerSyncService.java",
        ),
        "customer_gauss": git_diff(
            gauss_repo,
            "g1..g2",
            "src/main/java/com/example/migration/customer/CustomerSyncService.java",
        ),
        "mapper_oracle": git_diff(
            oracle_repo,
            "o1..o2",
            "src/main/resources/mapper/billing/SettlementMapper.xml",
        ),
        "mapper_gauss": git_diff(
            gauss_repo,
            "g1..g2",
            "src/main/resources/mapper/billing/SettlementMapper.xml",
        ),
        "faq_oracle": git_diff(
            oracle_repo,
            "o1..o2",
            "docs/reporting-faq.md",
        ),
        "faq_gauss": git_diff(
            gauss_repo,
            "g1..g2",
            "docs/reporting-faq.md",
        ),
    }

    lines: List[str] = []
    lines.append("# 最新接口与 Git Diff 对比")
    lines.append(f"任务 ID：{task_id}")
    lines.append("")

    lines.extend(
        build_section(
            "## /api/scan/full 摘要",
            full_entry["request"],
            full_entry["response"],
        )
    )
    lines.append("")
    lines.extend(
        build_section(
            "## /api/scan (增量)",
            incremental_entry["request"],
            incremental_entry["response"],
        )
    )
    lines.append("")

    # SettlementProcessor
    lines.append("## SettlementProcessor.java")
    lines.extend(
        build_section(
            "### /api/scan/detail",
            detail_entry["request"],
            detail_entry["response"],
        )
    )
    lines.append("")
    lines.extend(
        build_section(
            "### /api/migrate/generate",
            migrate_generate_entry["request"],
            migrate_generate_entry["response"],
        )
    )
    lines.append("")
    lines.extend(
        build_section(
            "### /api/migrate/apply",
            migrate_apply_entry["request"],
            migrate_apply_entry["response"],
        )
    )
    lines.append("")
    lines.extend(
        build_section(
            "### /api/migrate/revert",
            migrate_revert_entry["request"],
            migrate_revert_entry["response"],
        )
    )
    lines.append("")
    lines.append("### git diff Oracle (o1..o2)")
    lines.append("```diff")
    lines.append(diffs["settlement_oracle"])
    lines.append("```")
    lines.append("")
    lines.append("### git diff Gauss (g1..g2)")
    lines.append("```diff")
    lines.append(diffs["settlement_gauss"])
    lines.append("```")
    lines.append("")

    # CustomerSyncService
    lines.append("## CustomerSyncService.java")
    lines.extend(
        build_section(
            "### /api/scan/detail",
            customer_detail["request"],
            customer_detail["response"],
        )
    )
    lines.append("")
    lines.append("### git diff Oracle (o1..o2)")
    lines.append("```diff")
    lines.append(diffs["customer_oracle"])
    lines.append("```")
    lines.append("")
    lines.append("### git diff Gauss (g1..g2)")
    lines.append("```diff")
    lines.append(diffs["customer_gauss"])
    lines.append("```")
    lines.append("")

    # SettlementMapper
    lines.append("## SettlementMapper.xml")
    lines.extend(
        build_section(
            "### /api/scan/detail",
            mapper_detail["request"],
            mapper_detail["response"],
        )
    )
    lines.append("")
    lines.append("### git diff Oracle (o1..o2)")
    lines.append("```diff")
    lines.append(diffs["mapper_oracle"])
    lines.append("```")
    lines.append("")
    lines.append("### git diff Gauss (g1..g2)")
    lines.append("```diff")
    lines.append(diffs["mapper_gauss"])
    lines.append("```")
    lines.append("")

    # FAQ doc
    lines.append("## docs/reporting-faq.md")
    lines.append("### git diff Oracle (o1..o2)")
    lines.append("```diff")
    lines.append(diffs["faq_oracle"])
    lines.append("```")
    lines.append("")
    lines.append("### git diff Gauss (g1..g2)")
    if diffs["faq_gauss"]:
        lines.append("```diff")
        lines.append(diffs["faq_gauss"])
        lines.append("```")
    else:
        lines.append("目标分支无变更。")
    lines.append("")

    # Repo and settings
    lines.append("## 扫描配置与仓库接口")
    lines.extend(
        build_section(
            "### /api/scan/presets",
            None,
            presets_entry["response"],
        )
    )
    lines.append("")
    lines.extend(
        build_section(
            "### /api/repos (GET)",
            None,
            repos_list_entry["response"],
        )
    )
    lines.append("")
    lines.extend(
        build_section(
            "### /api/repos (POST)",
            repos_create_entry["request"],
            repos_create_entry["response"],
        )
    )
    lines.append("")
    lines.extend(
        build_section(
            "### /api/repos/branches (POST)",
            repos_branches_entry["request"],
            repos_branches_entry["response"],
        )
    )
    lines.append("")

    lines.append("## 系统设置接口")
    lines.extend(
        build_section(
            "### /api/settings (POST)",
            setting_update_entry["request"],
            setting_update_entry["response"],
        )
    )
    lines.append("")
    lines.extend(
        build_section(
            "### /api/settings/{key} (GET)",
            None,
            setting_get_entry["response"],
        )
    )
    lines.append("")

    content = "\n".join(lines).rstrip() + "\n"
    write_doc(content, output_path)
    print(f"Wrote documentation to {output_path}")


if __name__ == "__main__":
    main()
