#!/usr/bin/env bash
# Closes a finished chapter: verifies it, commits it and creates the chNN tag.
#
#   usage: ./scripts/finish-chapter.sh <number> [options]
#
#   --no-verify      skips the structural checks and `sbt verify` (not advised)
#   --no-push        commits and tags without pushing
#   --all            commits everything modified, not just this chapter
#   -t, --title TXT  chapter title (defaults to the theory document's heading)
#
# The tag is only born after the chapter is actually finished: documented,
# walked through, exercised on both sides, and green. A half-written chapter
# does not get one.
set -euo pipefail
set -o pipefail

die() { echo "error: $*" >&2; exit 1; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

NUMBER="${1:-}"
[[ -n "$NUMBER" ]] || die "usage: finish-chapter.sh <number> [--no-verify] [--no-push] [--all] [-t title]"
shift

VERIFY=1; PUSH=1; ALL=0; TITLE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-verify) VERIFY=0 ;;
    --no-push)   PUSH=0 ;;
    --all)       ALL=1 ;;
    -t|--title)  TITLE="${2:?-t needs a title}"; shift ;;
    *) die "unknown flag: $1" ;;
  esac
  shift
done

NN="$(printf '%02d' "$((10#$NUMBER))")"
TAG="ch$NN"

PKG_DIR="$(find modules/exercises/src/main/scala/advfp -maxdepth 1 -type d -name "ch${NN}*" 2>/dev/null | head -1 || true)"
[[ -n "$PKG_DIR" ]] || die "chapter $NN does not exist — start with ./scripts/new-chapter.sh $NN \"Title\""
PKG="$(basename "$PKG_DIR")"
DOC="$(find docs/theory -maxdepth 1 -name "ch${NN}-*.md" 2>/dev/null | head -1 || true)"

git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && die "tag $TAG already exists"

# The title comes from the document's heading: "# Chapter 07 — Match types"
if [[ -z "$TITLE" && -n "$DOC" ]]; then
  TITLE="$(sed -n '1s/^# Chapter [0-9]* — //p' "$DOC")"
fi
HEADLINE="chapter $NN"
[[ -n "$TITLE" ]] && HEADLINE="$HEADLINE — $(printf '%s' "$TITLE" | tr '[:upper:]' '[:lower:]')"
TAG_SUBJECT="Chapter $NN${TITLE:+ — $TITLE}"

# Where the staged tree gets built. Under target/, so it is git-ignored and
# `sbt clean` takes it away.
VERIFY_DIR="$ROOT/target/finish-chapter"

# Everything a finished chapter owes, checked against the tree that is about to
# be tagged rather than against the working directory.
#
# These are the checks `sbt verify` cannot make. A chapter whose document is
# still a template, or whose solution module is missing the file the exercise
# module ships, compiles and tests perfectly green — it is simply not finished.
check_structure() { # <tree>
  local tree="$1" ok=1 doc ex_main ex_test so_main so_test f ee

  doc="$(find "$tree/docs/theory" -maxdepth 1 -name "ch${NN}-*.md" 2>/dev/null | head -1 || true)"
  if [[ -z "$doc" ]]; then
    echo "  missing: docs/theory/ch${NN}-*.md"; ok=0
  elif grep -q '<!-- TODO' "$doc"; then
    echo "  unfinished: ${doc#"$tree/"} still has template TODO markers"; ok=0
  fi

  ex_main="$tree/modules/exercises/src/main/scala/advfp/$PKG"
  ex_test="$tree/modules/exercises/src/test/scala/advfp/$PKG"
  so_main="$tree/modules/solutions/src/main/scala/advfp/$PKG"
  so_test="$tree/modules/solutions/src/test/scala/advfp/$PKG"

  # A walkthrough left as the template passes `sbt verify` without trouble: it
  # compiles, and it says nothing. The TODO the template ships with is what
  # tells the two apart.
  if [[ ! -f "$ex_main/Walkthrough.scala" ]]; then
    echo "  missing: $PKG/Walkthrough.scala"; ok=0
  elif grep -q 'TODO' "$ex_main/Walkthrough.scala"; then
    echo "  unfinished: $PKG/Walkthrough.scala is still the template"; ok=0
  fi

  shopt -s nullglob
  local exercises=("$ex_main"/Exercise*.scala)
  shopt -u nullglob
  if [[ ${#exercises[@]} -eq 0 ]]; then
    echo "  missing: the chapter has no exercises"; ok=0
  fi

  for f in "${exercises[@]}"; do
    ee="$(basename "$f" .scala)"; ee="${ee#Exercise}"
    [[ -f "$ex_test/Exercise${ee}Spec.scala" ]] || { echo "  missing: exercises/…/$PKG/Exercise${ee}Spec.scala"; ok=0; }
    [[ -f "$so_main/Exercise${ee}.scala" ]]     || { echo "  missing: solutions/…/$PKG/Exercise${ee}.scala"; ok=0; }
    [[ -f "$so_test/Exercise${ee}Spec.scala" ]] || { echo "  missing: solutions/…/$PKG/Exercise${ee}Spec.scala"; ok=0; }
  done

  # An answer key that still says `Unsolved`, or still says TODO, is not an
  # answer key. `sbt verify` would not notice: a stub whose spec was never
  # written passes by having nothing to fail.
  if [[ -d "$so_main" ]] && grep -rlq -e 'Unsolved' -e 'unsolved' -e 'TODO' "$so_main"; then
    echo "  unfinished: the solutions for $PKG still contain Unsolved or TODO:"
    grep -rl -e 'Unsolved' -e 'unsolved' -e 'TODO' "$so_main" | sed "s|$tree/|    |"
    ok=0
  fi

  [[ $ok -eq 1 ]]
}

# Checks out the *index* into a scratch directory and runs the gate in there.
#
# Running the gate against the working tree instead passes on files that are not
# going into the commit: a source that was never `git add`ed is present on disk
# and absent from the tag, which is how a green run still publishes a tree that
# does not compile. This copy holds exactly what the commit will hold.
verify_staged_tree() {
  rm -rf "$VERIFY_DIR"
  mkdir -p "$VERIFY_DIR"
  git checkout-index --all --prefix="target/finish-chapter/" || return 1

  echo "  structure ..."
  check_structure "$VERIFY_DIR" || return 1

  echo "  sbt verify ..."
  (
    cd "$VERIFY_DIR" || exit 1
    # `verify` is scalafmtCheckAll + scalafmtSbtCheck + core/testFull +
    # solutions/testFull + exercises/Test/compile; see build.sbt for why it is testFull and not test.
    #
    # munit colours its stack traces, and a colour code in front of `at` hides
    # the frame from the filter — so the colours go first.
    esc=$'\033'
    sbt -batch verify 2>&1 \
      | sed -e "s/$esc\[[0-9;]*m//g" \
            -e '/[[:space:]]at [A-Za-z_$][A-Za-z0-9_.$]*[.(]/d' \
            -e '/sbt server disconnected/d'
  )
}

# Staging comes first, so that what gets verified is what gets committed. If the
# gate then fails, the index goes back to exactly how it was found.
INDEX_BEFORE="$(git write-tree)"

if [[ $ALL -eq 1 ]]; then
  git add -A
else
  git add -- "$PKG_DIR"
  for d in modules/exercises/src/test modules/solutions/src/main modules/solutions/src/test; do
    [[ -d "$d/scala/advfp/$PKG" ]] && git add -- "$d/scala/advfp/$PKG"
  done
  [[ -n "$DOC" ]] && git add -- "$DOC"
  # Anything modified that is not part of this chapter. Filtered on the path
  # rather than with a `:(exclude)` pathspec: an exclude pathspec that names a
  # directory does not exclude the files *inside* it, so every staged file of
  # this very chapter came back reported as left out of its own commit.
  others="$(git status --porcelain --untracked-files=all |
    { grep -v -e "/advfp/$PKG/" ${DOC:+-e "$DOC"} || true; } | head -5)"
  if [[ -n "$others" ]]; then
    echo
    echo "heads up, left out of the commit (use --all to include):"
    echo "$others" | sed 's/^/  /'
    echo
  fi
fi

if [[ $VERIFY -eq 1 ]]; then
  echo "verifying $HEADLINE as it will be committed ..."
  if verify_staged_tree; then
    rm -rf "$VERIFY_DIR"
  else
    git read-tree "$INDEX_BEFORE"
    echo "the tree that failed is still at ${VERIFY_DIR#"$ROOT/"}" >&2
    die "verification failed — nothing was committed or tagged"
  fi
else
  echo "skipping verification (--no-verify)"
fi

if git diff --cached --quiet; then
  echo "nothing new to commit — tagging HEAD instead"
else
  git commit -q -m "$HEADLINE" -m "Verified with: sbt verify"
  echo "commit: $(git log -1 --oneline)"
fi

git tag -a "$TAG" -m "$TAG_SUBJECT"
echo "tag: $TAG"

if [[ $PUSH -eq 1 ]]; then
  if git remote get-url origin >/dev/null 2>&1; then
    branch="$(git branch --show-current)"
    git push --follow-tags origin "$branch"
    echo "pushed: origin/$branch (with tag $TAG)"
  else
    echo "no remote configured — the commit and the tag stayed local."
    echo "  git remote add origin git@github.com:<you>/scala-advanced-fp.git"
    echo "  git push -u --follow-tags origin $(git branch --show-current)"
  fi
fi
