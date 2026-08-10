#!/usr/bin/env python3
"""Release metadata tooling for Skald Mobile.

Commands:
  python tools/release.py version
  python tools/release.py check [--tag v0.2.0]
  python tools/release.py prepare <major|minor|patch|X.Y.Z> [--date YYYY-MM-DD]
  python tools/release.py notes [--version X.Y.Z] [--out release-notes.md]
"""

from __future__ import annotations

import argparse
import datetime as dt
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSION_FILE = ROOT / "version.properties"
CHANGELOG = ROOT / "CHANGELOG.md"
REPO = "https://github.com/vardirhq/skald-mobile"
SEMVER = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$")


def read_version() -> tuple[str, int]:
    values = {}
    for line in VERSION_FILE.read_text().splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    version = values.get("VERSION_NAME", "")
    code = int(values.get("VERSION_CODE", "0"))
    if not SEMVER.match(version) or code < 1:
        raise ValueError("version.properties must contain a valid VERSION_NAME and positive VERSION_CODE")
    return version, code


def bump(current: str, spec: str) -> str:
    if SEMVER.match(spec):
        return spec
    match = SEMVER.match(current)
    if not match or spec not in {"major", "minor", "patch"}:
        raise ValueError("Version must be major, minor, patch, or an explicit SemVer version")
    major, minor, patch = map(int, match.groups()[:3])
    if spec == "major":
        return f"{major + 1}.0.0"
    if spec == "minor":
        return f"{major}.{minor + 1}.0"
    return f"{major}.{minor}.{patch + 1}"


def release_section(text: str, version: str) -> str:
    pattern = re.compile(rf"^## \[{re.escape(version)}\].*?\n(.*?)(?=^## \[|\Z)", re.M | re.S)
    match = pattern.search(text)
    if not match:
        raise ValueError(f"CHANGELOG.md has no [{version}] release section")
    return match.group(1).strip()


def check(tag: str | None = None) -> tuple[str, int]:
    version, code = read_version()
    text = CHANGELOG.read_text()
    problems = []
    if "## [Unreleased]" not in text:
        problems.append("CHANGELOG.md is missing [Unreleased]")
    try:
        release_section(text, version)
    except ValueError as error:
        problems.append(str(error))
    if tag and tag != f"v{version}":
        problems.append(f"tag {tag} does not match VERSION_NAME {version}")
    if problems:
        raise ValueError("Release metadata is inconsistent:\n  - " + "\n  - ".join(problems))
    return version, code


def prepare(spec: str, date: str) -> tuple[str, int, str]:
    current, code = read_version()
    version = bump(current, spec)
    if version == current:
        raise ValueError("New release version must differ from the current version")
    text = CHANGELOG.read_text()
    marker = "## [Unreleased]"
    if marker not in text:
        raise ValueError("CHANGELOG.md is missing [Unreleased]")
    unreleased_start = text.index(marker) + len(marker)
    next_release = text.find("\n## [", unreleased_start)
    if next_release == -1:
        next_release = len(text)
    body = text[unreleased_start:next_release].strip()
    if not body or not re.search(r"^- ", body, re.M):
        raise ValueError("[Unreleased] has no changelog entries to release")
    new_head = f"## [Unreleased]\n\n### Added\n\n### Changed\n\n### Fixed\n\n## [{version}] - {date}\n\n{body}\n"
    text = text[:text.index(marker)] + new_head + text[next_release:].lstrip("\n")
    text = re.sub(r"\n\[Unreleased\]:.*(?:\n|$)", "\n", text)
    text = re.sub(rf"\n\[{re.escape(version)}\]:.*(?:\n|$)", "\n", text)
    text = text.rstrip() + f"\n\n[Unreleased]: {REPO}/compare/v{version}...HEAD\n[{version}]: {REPO}/releases/tag/v{version}\n"
    VERSION_FILE.write_text(f"VERSION_NAME={version}\nVERSION_CODE={code + 1}\n")
    CHANGELOG.write_text(text)
    check(f"v{version}")
    return version, code + 1, current


def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("version")
    check_parser = sub.add_parser("check")
    check_parser.add_argument("--tag")
    prepare_parser = sub.add_parser("prepare")
    prepare_parser.add_argument("bump")
    prepare_parser.add_argument("--date", default=dt.date.today().isoformat())
    notes_parser = sub.add_parser("notes")
    notes_parser.add_argument("--version")
    notes_parser.add_argument("--out")
    args = parser.parse_args()

    if args.command == "version":
        print(read_version()[0])
    elif args.command == "check":
        version, code = check(args.tag)
        print(f"Release metadata is consistent at {version} (versionCode {code}).")
    elif args.command == "prepare":
        version, code, previous = prepare(args.bump, args.date)
        print(f"Prepared {previous} -> {version} (versionCode {code})")
    elif args.command == "notes":
        version = args.version or read_version()[0]
        notes = release_section(CHANGELOG.read_text(), version)
        if args.out:
            (ROOT / args.out).write_text(notes + "\n")
        else:
            print(notes)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as error:
        print(error, file=sys.stderr)
        sys.exit(1)
