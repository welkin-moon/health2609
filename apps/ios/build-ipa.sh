#!/usr/bin/env bash
set -euo pipefail
IOS_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$IOS_DIR/../.." && pwd)"
OUTPUT_DIR="${IOS_OUTPUT_DIR:-$REPO_DIR/dist/ios}"
DERIVED_DIR="${IOS_DERIVED_DIR:-$IOS_DIR/.build-device}"
command -v xcodebuild >/dev/null || { echo 'Xcode on macOS is required.' >&2; exit 1; }
mkdir -p "$OUTPUT_DIR"
EXTRA_SETTINGS=("CODE_SIGN_ENTITLEMENTS=")
VARIANT=resign
if [[ "${ENABLE_HEALTHKIT:-0}" == 1 ]]; then
  VARIANT=healthkit
  EXTRA_SETTINGS+=("SWIFT_ACTIVE_COMPILATION_CONDITIONS=HEALTHKIT_ENABLED" "CODE_SIGN_ENTITLEMENTS=$IOS_DIR/Resources/Health2609.entitlements")
fi
xcodebuild -project "$IOS_DIR/Health2609.xcodeproj" -scheme Health2609 \
  -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' \
  -derivedDataPath "$DERIVED_DIR" CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO \
  "CURRENT_PROJECT_VERSION=${IOS_BUILD_NUMBER:-17}" "MARKETING_VERSION=${IOS_VERSION:-0.4.0}" \
  "${EXTRA_SETTINGS[@]}" build
STAGING_DIR="$(mktemp -d)"
trap 'rm -rf "$STAGING_DIR"' EXIT
mkdir -p "$STAGING_DIR/Payload"
cp -R "$DERIVED_DIR/Build/Products/Release-iphoneos/Health2609.app" "$STAGING_DIR/Payload/"
IPA_PATH="$OUTPUT_DIR/yicanyidong-ios-$VARIANT-unsigned.ipa"
rm -f "$IPA_PATH"
(cd "$STAGING_DIR" && /usr/bin/zip -qry "$IPA_PATH" Payload)
printf 'Unsigned device IPA: %s\n' "$IPA_PATH"
