#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TOOLS_DIR="$SCRIPT_DIR/.tools"

if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "ANDROID_HOME must point to the installed Android SDK" >&2
  exit 1
fi
export PATH="/usr/local/go/bin:$TOOLS_DIR/bin:$PATH"
export GOBIN="$TOOLS_DIR/bin"

mkdir -p "$GOBIN" "$PROJECT_DIR/app/libs"

go install golang.org/x/mobile/cmd/gomobile@v0.0.0-20260908204917-8b95e45f8d3e
go install golang.org/x/mobile/cmd/gobind@v0.0.0-20260908204917-8b95e45f8d3e
gomobile init

cd "$SCRIPT_DIR"
gomobile bind \
  -target=android/arm64,android/amd64 \
  -androidapi=26 \
  -javapkg=dev.polisms.libgm \
  -trimpath \
  -o "$PROJECT_DIR/app/libs/libgmbridge.aar" \
  ./mobilebridge
