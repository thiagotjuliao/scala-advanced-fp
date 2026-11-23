#!/usr/bin/env bash
# Fills references/local/ from the manifest, references/README.md.
#
#   usage: ./scripts/fetch-references.sh [--check]
#
#   --check   reports what is missing or unlisted, and downloads nothing
#
# Every entry the manifest marks `local/<file>` is downloaded from the first
# link that follows it, unless the file is already there. Only a link to a PDF
# is fetched (an arXiv abstract page counts, through its /pdf/ twin); a
# PostScript original or a release page is reported with its link, to be
# fetched by hand. It also reports the files in local/ and open/ the manifest
# does not name, and the open/ files it names that are missing.
#
# Nothing is overwritten or deleted: a file already in local/ is left alone,
# and a download that is not a PDF is discarded and reported.
set -euo pipefail

die() { echo "error: $*" >&2; exit 1; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/references"

CHECK=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --check) CHECK=1 ;;
    *) die "unknown flag: $1 (usage: fetch-references.sh [--check])" ;;
  esac
  shift
done

[[ -f README.md ]] || die "references/README.md not found"
mkdir -p local

# An entry wraps across lines, so the manifest is read as one string: each
# `local/<file>` is paired with the first ](url) after it. Perl rather than
# sed, because the pairing spans newlines.
mapfile -t ENTRIES < <(perl -0777 -ne \
  'while (/`local\/([^`]+)`.*?\]\((\S+?)\)/gs) { print "$1\t$2\n" }' README.md)
[[ ${#ENTRIES[@]} -gt 0 ]] || die "no local/ entries found in README.md"

present=0; fetched=0; manual=0; failed=0
for entry in "${ENTRIES[@]}"; do
  file="${entry%%$'\t'*}"
  url="${entry#*$'\t'}"
  if [[ -f "local/$file" ]]; then
    present=$((present + 1))
    continue
  fi
  case "$url" in
    https://arxiv.org/abs/*) url="https://arxiv.org/pdf/${url#https://arxiv.org/abs/}" ;;
    *.pdf) ;;
    *)
      echo "  by hand   $file  <- $url"
      manual=$((manual + 1))
      continue ;;
  esac
  if [[ $CHECK -eq 1 ]]; then
    echo "  missing   $file  <- $url"
    failed=$((failed + 1))
    continue
  fi
  tmp="local/.$file.part"
  # A failed or non-PDF download (an HTML error page, say) never takes the
  # final name, so a rerun tries it again.
  if curl -fsSL --retry 2 -o "$tmp" "$url" && [[ "$(head -c 5 "$tmp")" == "%PDF-" ]]; then
    mv "$tmp" "local/$file"
    echo "  fetched   $file"
    fetched=$((fetched + 1))
  else
    rm -f "$tmp"
    echo "  FAILED    $file  <- $url"
    failed=$((failed + 1))
  fi
done

# The other direction: files on disk the manifest does not account for.
unlisted=0
for dir in local open; do
  [[ -d "$dir" ]] || continue
  for path in "$dir"/*.pdf; do
    [[ -e "$path" ]] || continue
    if ! grep -qF "\`$path\`" README.md; then
      echo "  unlisted  $path"
      unlisted=$((unlisted + 1))
    fi
  done
done
while IFS= read -r path; do
  if [[ ! -f "$path" ]]; then
    echo "  missing   $path  (committed files: check out the branch again)"
    failed=$((failed + 1))
  fi
done < <(grep -oE '`open/[^`]+`' README.md | tr -d '`' | sort -u)

echo "local: ${#ENTRIES[@]} listed, $present present, $fetched fetched, $manual by hand, $failed missing or failed; $unlisted unlisted"
[[ $failed -eq 0 && $manual -eq 0 && $unlisted -eq 0 ]]
