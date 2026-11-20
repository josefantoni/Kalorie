#!/bin/bash
command -v jq >/dev/null || { echo "Hook ${0##*/} cannot run: jq is not installed (brew install jq)." >&2; exit 2; }
command -v realpath >/dev/null || { echo "Hook ${0##*/} cannot run: realpath is not available (macOS 13 or later)." >&2; exit 2; }
input=$(cat)
file=$(jq -r '.tool_input.file_path // empty' <<<"$input")
[ -z "$file" ] && exit 0

# realpath restores the on-disk letter case, which a case-insensitive APFS volume would otherwise let differ.
existing=$file
while [ ! -e "$existing" ]; do existing=$(dirname "$existing"); done
file="$(realpath "$existing")${file#"$existing"}"
dir=$file
while [ ! -d "$dir" ]; do dir=$(dirname "$dir"); done
# Resolved from the file, not $CLAUDE_PROJECT_DIR, so edits in another worktree are matched too.
root=$(git -C "$dir" rev-parse --show-toplevel 2>/dev/null) || exit 0
rel="${file#"$root"/}"

# Design docs are deliberately not blocked: docs/README.md allows shipped ones a Status change, the Outcome section
# and dated iOS-scoped updates, which no path or line rule can tell apart from a forbidden edit.
case "$rel" in
  iOS/Kalorie/Resources/Localizable.xcstrings|Android/app/src/main/res/values*/strings.xml)
    echo "Blocked: $rel is generated from localisation/strings.json. Edit that file, then run 'npm run generate-strings' in scripts/." >&2
    exit 2
    ;;
  docs/adr/*.md)
    if git -C "$root" cat-file -e "main:$rel" 2>/dev/null; then
      status_only=$(jq -r '
        .tool_name == "Edit"
        and ([.tool_input.old_string, .tool_input.new_string]
             | all(type == "string" and startswith("- **Status:** ") and (contains("\n") | not)))
      ' <<<"$input")
      [ "$status_only" = true ] && exit 0
      echo "Blocked: $rel is already on main, so it is immutable. Supersede it with a new ADR instead; only its '- **Status:**' line may change, in a single-line Edit (CLAUDE.md, Documentation)." >&2
      exit 2
    fi
    ;;
esac

exit 0
