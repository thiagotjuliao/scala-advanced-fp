#!/usr/bin/env bash
# Hands in a solved chapter: checks it, commits it and pushes it to a branch.
#
#   usage: ./scripts/solve-chapter.sh <number> <branch> [options]
#
#   --no-verify   skips running the chapter's specs (not advised)
#   --no-push     commits without pushing
#
# The learner's side of finish-chapter. What gets committed is your work on the
# chapter's exercises — modules/exercises/src/main/…/chNN* — and your notes on
# it, notes/chNN.md, if there are any; nothing else. It only gets committed once
# every spec of the chapter is green against exactly that tree. There is no
# tag: tags mark chapters written, not solved.
set -euo pipefail

die() { echo "error: $*" >&2; exit 1; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

USAGE="usage: solve-chapter.sh <number> <branch> [--no-verify] [--no-push]"
NUMBER="${1:-}"
BRANCH="${2:-}"
[[ -n "$NUMBER" && -n "$BRANCH" ]] || die "$USAGE"
shift 2

VERIFY=1; PUSH=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-verify) VERIFY=0 ;;
    --no-push)   PUSH=0 ;;
    *) die "unknown flag: $1" ;;
  esac
  shift
done

NN="$(printf '%02d' "$((10#$NUMBER))")"

PKG_DIR="$(find modules/exercises/src/main/scala/advfp -maxdepth 1 -type d -name "ch${NN}*" 2>/dev/null | head -1 || true)"
[[ -n "$PKG_DIR" ]] || die "chapter $NN does not exist"
PKG="$(basename "$PKG_DIR")"

# `main` is the course itself. Solutions pushed there would hand every other
# learner the answers along with the next chapter.
[[ "$BRANCH" != "main" ]] || die "solutions do not go on main — pick a branch of your own"
git check-ref-format --branch "$BRANCH" >/dev/null 2>&1 || die "not a valid branch name: $BRANCH"

HEADLINE="solutions — chapter $NN"
SUITE="advfp.$PKG.*"

# Where the staged tree gets built. Under target/, so it is git-ignored and
# `sbt clean` takes it away.
VERIFY_DIR="$ROOT/target/solve-chapter"

# Onto the branch first, so the commit lands there. `git switch` carries
# uncommitted work along, and refuses — touching nothing — when it cannot.
current="$(git branch --show-current)"
if [[ "$current" != "$BRANCH" ]]; then
  if git show-ref -q --verify "refs/heads/$BRANCH"; then
    git switch -q "$BRANCH" || die "could not switch to $BRANCH — see git's message above"
  else
    git switch -q -c "$BRANCH"
    echo "created branch $BRANCH from $current"
  fi
fi

# Runs the chapter's specs against the *index*, checked out into a scratch
# directory. The working tree would also count files that are not going into
# the commit; this copy holds exactly what the commit will hold.
verify_staged_tree() {
  local out status
  # munit colours its stack traces, and a colour code in front of `at` hides
  # the frame from the filter below — so the colours go first.
  local esc=$'\033'
  rm -rf "$VERIFY_DIR"
  mkdir -p "$VERIFY_DIR"
  git checkout-index --all --prefix="target/solve-chapter/" || return 1

  echo "  sbt \"scalafmtCheckAll; exercises/testOnly $SUITE\" ..."
  set +e
  # One argument, the commands joined by `;`: under sbt 2 the launcher runs the
  # thin client, which joins separate arguments into a single command line
  # (`scalafmtCheckAll exercises/testOnly ...`, rejected with `Expected '/'`).
  out="$(
    cd "$VERIFY_DIR" &&
      sbt -batch "scalafmtCheckAll; exercises/testOnly $SUITE" 2>&1 |
      sed -e "s/$esc\[[0-9;]*m//g" \
          -e '/[[:space:]]at [A-Za-z_$][A-Za-z0-9_.$]*[.(]/d' \
          -e '/sbt server disconnected/d'
  )"
  status=$?
  set -e
  echo "$out"
  [[ $status -eq 0 ]] || return 1

  # A filter that matches no suite is a green run of nothing.
  echo "$out" | grep -Eq 'Passed: Total [1-9]' || {
    echo "  no spec ran for $SUITE"
    return 1
  }
}

# Staging comes first, so that what gets verified is what gets committed. If the
# check then fails, the index goes back to exactly how it was found.
INDEX_BEFORE="$(git write-tree)"
git add -- "$PKG_DIR"
NOTES="notes/ch$NN.md"
if [[ -f "$NOTES" ]]; then git add -- "$NOTES"; fi

others="$(git status --porcelain --untracked-files=all |
  { grep -v -e "^[AM]  $PKG_DIR/" -e "^[AM]  $NOTES\$" || true; } | head -5)"
if [[ -n "$others" ]]; then
  echo
  echo "heads up, left out of the commit (only $PKG_DIR/ and $NOTES go in):"
  echo "$others" | sed 's/^/  /'
  echo
fi

if [[ $VERIFY -eq 1 ]]; then
  echo "verifying chapter $NN as it will be committed ..."
  if verify_staged_tree; then
    rm -rf "$VERIFY_DIR"
  else
    git read-tree "$INDEX_BEFORE"
    echo "the tree that failed is still at ${VERIFY_DIR#"$ROOT/"}" >&2
    die "chapter $NN is not solved yet — nothing was committed"
  fi
else
  echo "skipping verification (--no-verify)"
fi

if git diff --cached --quiet; then
  echo "nothing new to commit on $BRANCH"
else
  if [[ $VERIFY -eq 1 ]]; then
    git commit -q -m "$HEADLINE" -m "Verified with: sbt \"exercises/testOnly $SUITE\""
  else
    git commit -q -m "$HEADLINE"
  fi
  echo "commit: $(git log -1 --oneline)"
fi

if [[ $PUSH -eq 1 ]]; then
  if git remote get-url origin >/dev/null 2>&1; then
    git push -u origin "$BRANCH"
    echo "pushed: origin/$BRANCH"
  else
    echo "no remote configured — the commit stayed local."
    echo "  git remote add origin git@github.com:<you>/scala-advanced-fp.git"
    echo "  git push -u origin $BRANCH"
  fi
fi
