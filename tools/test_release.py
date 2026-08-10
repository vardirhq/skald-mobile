#!/usr/bin/env python3
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def run(*args: str) -> str:
    return subprocess.check_output([sys.executable, "tools/release.py", *args], cwd=ROOT, text=True).strip()


version = run("version")
if not version:
    raise SystemExit("release.py version returned nothing")

run("check", "--tag", f"v{version}")
notes = run("notes", "--version", version)
if not notes:
    raise SystemExit(f"CHANGELOG.md has no notes for {version}")

print(f"Release tooling OK for {version}.")
