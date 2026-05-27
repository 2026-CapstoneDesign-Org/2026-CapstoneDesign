#!/usr/bin/env python3
"""Run sidecar/Spring contract drift checks.

This script only runs local tests and file scans. It does not start a Voice
Agent, call Spring outside the test JVM, call ClawOps/OpenAI, or place calls.
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys


REPO_ROOT = pathlib.Path(__file__).resolve().parents[3]
SIDECAR_DIR = REPO_ROOT / "sidecars" / "clawops-voice-agent"
CAPSTONE_DIR = REPO_ROOT / "Capstone"
FORBIDDEN_PATTERNS = {
    "openai_secret_like": re.compile(r"sk-[A-Za-z0-9]"),
    "jwt_like": re.compile(r"\b[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"),
    "bearer_token_like": re.compile(r"Bearer\s+[A-Za-z0-9._~+/=-]+"),
    "korean_phone_like": re.compile(
        r"\b(?:010|011|016|017|018|019|070|02|031|032|033|041|042|043|044|051|052|053|054|055|061|062|063|064)"
        r"[- ]?\d{3,4}[- ]?\d{4}\b"
    ),
}
SCAN_PATHS = [
    SIDECAR_DIR,
    REPO_ROOT / "docs" / "logic" / "reservation-policy.md",
    REPO_ROOT / "docs" / "current-gaps.md",
    REPO_ROOT / "plans" / "clawops-voice-agent-sidecar-design-2026-05-26.md",
]


def main() -> int:
    run([
        sys.executable,
        "-m",
        "unittest",
        "discover",
        "sidecars/clawops-voice-agent/tests",
    ], cwd=REPO_ROOT)
    run([
        "bash",
        "./gradlew",
        "test",
        "--tests",
        "com.example.Capstone.e2e.ClawOpsAgentProviderEventE2ETest",
    ], cwd=CAPSTONE_DIR)
    scan_forbidden_values()
    return 0


def run(command: list[str], cwd: pathlib.Path) -> None:
    print("$ " + " ".join(command))
    subprocess.run(command, cwd=cwd, check=True)


def scan_forbidden_values() -> None:
    failures = []
    for path in iter_scan_files():
        text = path.read_text(encoding="utf-8")
        for name, pattern in FORBIDDEN_PATTERNS.items():
            if pattern.search(text):
                failures.append(f"{path.relative_to(REPO_ROOT)} matched {name}")
    if failures:
        joined = "\n".join(failures)
        raise SystemExit(f"contract leak scan failed:\n{joined}")
    print("contract leak scan passed")


def iter_scan_files():
    for path in SCAN_PATHS:
        if path.is_file():
            yield path
            continue
        if path.is_dir():
            for child in path.rglob("*"):
                if child.is_file() and child.suffix in {".json", ".md", ".py", ".txt"}:
                    yield child


if __name__ == "__main__":
    raise SystemExit(main())
