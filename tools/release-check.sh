#!/usr/bin/env bash
set -euo pipefail
python tools/test_release.py
./gradlew --no-daemon :core:test :app:assembleDebug
