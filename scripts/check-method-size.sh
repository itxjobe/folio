#!/bin/sh
# Fails when any Folio method in the debug APK is near ART's per-method limits. A LauncherScreen lambda past ~24k
# code units logged "Method exceeds compiler instruction limit" and, at 25.5k, failed verification on launch
# (VerifyError, 10 Oct 2026). Usage: scripts/check-method-size.sh [apk] [limit]
APK=${1:-app/build/outputs/apk/debug/app-debug.apk}; LIMIT=${2:-20000}
DEXDUMP=$(ls "${ANDROID_HOME:-$HOME/android-sdk}"/build-tools/*/dexdump | tail -1)
TMP=$(mktemp -d); unzip -q -o "$APK" 'classes*.dex' -d "$TMP"
for f in "$TMP"/classes*.dex; do "$DEXDUMP" -d "$f" 2>/dev/null; done | awk -v lim="$LIMIT" '
  /^  Class descriptor/ { c = $4 } /name *:/ { n = $3 } /insns size/ { if (c ~ /mccal\/folio/ && $4 > lim) { print $4, c, n; bad = 1 } }
  END { exit bad }'
rc=$?; rm -rf "$TMP"; [ $rc -eq 0 ] && echo "OK: no Folio method over $LIMIT code units" || echo "FAIL: methods above over $LIMIT code units"; exit $rc
