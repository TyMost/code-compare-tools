import argparse
import json
import subprocess
import time
from pathlib import Path
from typing import Any, Dict, List, Optional

import requests


class BackendLauncher:
    """Launch the Spring Boot backend JAR when requested."""

    def __init__(self, jar_path: Path, host: str, port: int) -> None:
        self.jar_path = jar_path
        self.host = host
        self.port = port
        self.process: Optional[subprocess.Popen[Any]] = None
        self.stdout_path = jar_path.parent / "cli-server.out.log"
        self.stderr_path = jar_path.parent / "cli-server.err.log"

    def __enter__(self) -> "BackendLauncher":
        stdout_handle = open(self.stdout_path, "w", encoding="utf-8")
        stderr_handle = open(self.stderr_path, "w", encoding="utf-8")
        self.process = subprocess.Popen(
            [
                "java",
                "-jar",
                str(self.jar_path),
                f"--server.port={self.port}",
                f"--server.address={self.host}",
            ],
            cwd=str(self.jar_path.parent),
            stdout=stdout_handle,
            stderr=stderr_handle,
        )
        stdout_handle.close()
        stderr_handle.close()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        if self.process and self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                self.process.kill()
        self.process = None

    def wait_until_ready(self, timeout: float = 90.0) -> None:
        deadline = time.time() + timeout
        base_url = f"http://{self.host}:{self.port}"
        health_url = f"{base_url}/actuator/health"
        last_exception: Optional[Exception] = None
        while time.time() < deadline:
            try:
                response = requests.get(health_url, timeout=2.0)
                if response.status_code == 200:
                    return
            except requests.RequestException as exc:
                last_exception = exc
            time.sleep(1.0)
        raise RuntimeError(f"Backend failed to start within {timeout} seconds: {last_exception}")


def send_json(
    method: str,
    url: str,
    body: Optional[Dict[str, Any]],
    results: List[Dict[str, Any]],
    *,
    timeout: float = 120.0,
) -> Any:
    request_kwargs: Dict[str, Any] = {"timeout": timeout}
    if body is not None:
        request_kwargs["json"] = body
    response = requests.request(method, url, **request_kwargs)
    try:
        payload: Any = response.json()
    except ValueError:
        payload = response.text
    results.append(
        {
            "method": method,
            "url": url,
            "status": response.status_code,
            "request": body,
            "response": payload,
        }
    )
    response.raise_for_status()
    return payload


def collect_results(base_url: str, task_id: str, base_dir: Path) -> List[Dict[str, Any]]:
    results: List[Dict[str, Any]] = []

    oracle_repo = (base_dir / "examples" / "o").resolve()
    gauss_repo = (base_dir / "examples" / "g").resolve()
    repo_path_oracle = oracle_repo.as_posix()
    repo_path_gauss = gauss_repo.as_posix()

    scan_full_body = {
        "taskId": task_id,
        "persistResult": True,
        "oracle": {
            "repoPath": repo_path_oracle,
            "branchFrom": "o1",
            "branchTo": "o2",
            "deltaType": "DELTA_O",
        },
        "gauss": {
            "repoPath": repo_path_gauss,
            "branchFrom": "g1",
            "branchTo": "g2",
            "deltaType": "DELTA_G",
        },
    }

    scan_incremental_body = {
        "taskId": f"{task_id}-incremental",
        "persistResult": False,
        "oracle": {
            "repoPath": repo_path_oracle,
            "branchFrom": "o1",
            "branchTo": "o2",
        },
        "gauss": {
            "repoPath": repo_path_gauss,
            "branchFrom": "g1",
            "branchTo": "g2",
        },
    }

    diff_detail_body = {
        "taskId": task_id,
        "filePath": "src/main/java/com/example/migration/billing/SettlementProcessor.java",
    }

    migration_payload = {
        "taskId": task_id,
        "filePath": diff_detail_body["filePath"],
    }

    repo_config = {
        "repoPath": {
            "absolutePath": repo_path_oracle,
            "type": "ORACLE",
        },
        "branchFrom": {"name": "o1"},
        "branchTo": {"name": "o2"},
        "deltaType": "DELTA_O",
        "fetchIfMissing": True,
        "includeWorkingTree": False,
        "remoteName": "origin",
    }

    setting_payload = {
        "key": "migration.defaultPreset",
        "value": "oracle-gauss-demo",
        "description": "Default preset captured during verification",
    }

    send_json("POST", f"{base_url}/api/scan/full", scan_full_body, results)
    send_json("POST", f"{base_url}/api/scan", scan_incremental_body, results)
    send_json("POST", f"{base_url}/api/scan/detail", diff_detail_body, results)
    send_json("POST", f"{base_url}/api/migrate/generate", migration_payload, results)
    send_json("POST", f"{base_url}/api/migrate/apply", migration_payload, results)
    send_json("POST", f"{base_url}/api/migrate/revert", migration_payload, results)
    send_json("GET", f"{base_url}/api/scan/presets", None, results)
    send_json("GET", f"{base_url}/api/repos", None, results)
    send_json("POST", f"{base_url}/api/repos", repo_config, results)
    send_json("POST", f"{base_url}/api/repos/branches", repo_config, results)
    send_json("POST", f"{base_url}/api/settings", setting_payload, results)
    send_json("GET", f"{base_url}/api/settings/{setting_payload['key']}", None, results)

    return results


def write_results(results: List[Dict[str, Any]], output_path: Path) -> None:
    output_path.write_text(json.dumps(results, indent=4, ensure_ascii=False), encoding="utf-8")
    print(f"Wrote API verification results to {output_path}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Verify API endpoints and capture responses.")
    parser.add_argument("--host", default="localhost", help="Backend host name.")
    parser.add_argument("--port", type=int, default=8080, help="Backend port.")
    parser.add_argument(
        "--no-launch",
        action="store_true",
        help="Skip launching the backend jar (server already running).",
    )
    parser.add_argument(
        "--task-id",
        default=None,
        help="Custom task id used for scan requests.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("docs/test/api-verification-results.json"),
        help="Output file to store API request/response payloads.",
    )
    args = parser.parse_args()

    base_dir = Path(__file__).resolve().parents[1]
    output_path = (base_dir / args.output).resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    task_id = args.task_id or f"codex-verify-20251103-{args.port}"
    base_url = f"http://{args.host}:{args.port}"

    if args.no_launch:
        results = collect_results(base_url, task_id, base_dir)
    else:
        jar_path = base_dir / "backend" / "target" / "migratediff-backend-0.0.1-SNAPSHOT.jar"
        if not jar_path.exists():
            raise FileNotFoundError(f"Backend JAR not found: {jar_path}")
        with BackendLauncher(jar_path, args.host, args.port) as launcher:
            launcher.wait_until_ready()
            results = collect_results(base_url, task_id, base_dir)

    write_results(results, output_path)


if __name__ == "__main__":
    main()
