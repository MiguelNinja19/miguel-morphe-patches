#!/usr/bin/env bash
#
# build.sh — Build the diozz-cubex-patches Morphe patch bundle (.mpp)
#
# Usage:
#   GITHUB_ACTOR=<your-github-username> \
#   GITHUB_TOKEN=<your-github-pat-with-read-packages-scope> \
#   ./build.sh
#
# The GITHUB_TOKEN must have `read:packages` scope so it can download
# the Morphe Patches Gradle plugin from
# https://maven.pkg.github.com/MorpheApp/registry
#
# Create a PAT at: https://github.com/settings/tokens

set -euo pipefail

if [[ -z "${GITHUB_ACTOR:-}" || -z "${GITHUB_TOKEN:-}" ]]; then
  echo "ERROR: GITHUB_ACTOR and GITHUB_TOKEN environment variables must be set."
  echo ""
  echo "Get a GitHub PAT with 'read:packages' scope at:"
  echo "  https://github.com/settings/tokens"
  echo ""
  echo "Then run:"
  echo "  GITHUB_ACTOR=<user> GITHUB_TOKEN=<pat> ./build.sh"
  exit 1
fi

cd "$(dirname "$0")"

echo "==> Building diozz-cubex-patches..."
./gradlew buildAndroid --no-daemon

echo ""
echo "==> Build successful!"
echo "Patch bundle location:"
ls -lh patches/build/libs/patches-*.mpp
echo ""
echo "To patch the APK using Morphe CLI:"
echo "  java -jar morphe-cli.jar patch \\"
echo "    -a diozz.cubex_4.1.0.apk \\"
echo "    -p patches/build/libs/patches-1.0.0.mpp \\"
echo "    -o diozz.cubex_4.1.0_patched.apk"
