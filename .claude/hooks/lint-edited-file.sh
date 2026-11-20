#!/bin/bash
command -v jq >/dev/null || { echo "Hook ${0##*/} cannot run: jq is not installed (brew install jq)." >&2; exit 2; }
command -v realpath >/dev/null || { echo "Hook ${0##*/} cannot run: realpath is not available (macOS 13 or later)." >&2; exit 2; }
file=$(jq -r '.tool_input.file_path // empty')
[ -f "$file" ] || exit 0

# realpath restores the on-disk letter case, which a case-insensitive APFS volume would otherwise let differ.
file=$(realpath "$file")
root=$(git -C "$(dirname "$file")" rev-parse --show-toplevel 2>/dev/null) || exit 0
rel="${file#"$root"/}"
err=$(mktemp)
trap 'rm -f "$err"' EXIT

case "$rel" in
  iOS/*.swift)
    command -v swiftlint >/dev/null || { echo "Hook ${0##*/} cannot lint $rel: swiftlint is not installed (brew install swiftlint)." >&2; exit 2; }
    out=$(cd "$root/iOS" && swiftlint lint --quiet --config .swiftlint.yml "$file" 2>"$err")
    rc=$?
    ;;
  Android/*.kt|Android/*.kts)
    command -v ktlint >/dev/null || { echo "Hook ${0##*/} cannot lint $rel: ktlint is not installed (brew install ktlint)." >&2; exit 2; }
    pinned=$(sed -n 's/^ *version\.set("\(.*\)")/\1/p' "$root/Android/app/build.gradle.kts")
    installed=$(ktlint --version 2>/dev/null | awk '{print $NF}')
    if [ "$installed" != "$pinned" ]; then
      echo "Hook ${0##*/} cannot lint $rel: ktlint CLI is ${installed:-unknown}, but Android/app/build.gradle.kts pins ${pinned:-nothing}." >&2
      exit 2
    fi
    out=$(cd "$root/Android" && ktlint --log-level=error "$file" 2>"$err")
    rc=$?
    ;;
  *)
    exit 0
    ;;
esac

if [ -z "$out" ] && [ "$rc" -ne 0 ]; then
  printf 'Hook %s: the linter failed on %s (exit %d):\n%s\n' "${0##*/}" "$rel" "$rc" "$(cat "$err")" >&2
  exit 2
fi

if [ -n "$out" ]; then
  printf 'Lint violations in %s:\n%s\n' "$rel" "$out" >&2
  exit 2
fi

exit 0
