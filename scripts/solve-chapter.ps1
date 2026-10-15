#Requires -Version 7
<#
.SYNOPSIS
  Hands in a solved chapter: checks it, commits it and pushes it to a branch.

.EXAMPLE
  ./scripts/solve-chapter.ps1 2 my-solutions
  ./scripts/solve-chapter.ps1 2 my-solutions -NoPush

.NOTES
  The bash twin is scripts/solve-chapter.sh. The learner's side of
  finish-chapter: what gets committed is your work on the chapter's exercises —
  modules/exercises/src/main/…/chNN* — and your notes on it, notes/chNN.md, if
  there are any; nothing else. It only gets committed once every spec of the
  chapter is green against exactly that tree. There is no tag: tags mark
  chapters written, not solved.
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)][int]$Number,
  [Parameter(Mandatory)][string]$Branch,
  [switch]$NoVerify,
  [switch]$NoPush
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$nn = '{0:d2}' -f $Number

$pkgDir = Get-ChildItem 'modules/exercises/src/main/scala/advfp' -Directory -Filter "ch$nn*" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $pkgDir) { throw "chapter $nn does not exist" }
$pkg = $pkgDir.Name
$pkgPath = "modules/exercises/src/main/scala/advfp/$pkg"

# `main` is the course itself. Solutions pushed there would hand every other
# learner the answers along with the next chapter.
if ($Branch -eq 'main') { throw 'solutions do not go on main — pick a branch of your own' }
git check-ref-format --branch $Branch 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) { throw "not a valid branch name: $Branch" }

$headline = "solutions — chapter $nn"
$suite    = "advfp.$pkg.*"

# Where the staged tree gets built. Under target/, so it is git-ignored and
# `sbt clean` takes it away.
$verifyDir = Join-Path $root 'target/solve-chapter'

# Onto the branch first, so the commit lands there. `git switch` carries
# uncommitted work along, and refuses — touching nothing — when it cannot.
$current = (git branch --show-current).Trim()
if ($current -ne $Branch) {
  git show-ref -q --verify "refs/heads/$Branch"
  if ($LASTEXITCODE -eq 0) {
    git switch -q $Branch
    if ($LASTEXITCODE -ne 0) { throw "could not switch to $Branch — see git's message above" }
  }
  else {
    git switch -q -c $Branch
    if ($LASTEXITCODE -ne 0) { throw "could not create $Branch" }
    Write-Host "created branch $Branch from $current"
  }
}

# Runs the chapter's specs against the *index*, checked out into a scratch
# directory. The working tree would also count files that are not going into
# the commit; this copy holds exactly what the commit will hold.
function Invoke-StagedVerification {
  if (Test-Path $verifyDir) { Remove-Item $verifyDir -Recurse -Force }
  New-Item -ItemType Directory -Force -Path $verifyDir | Out-Null
  git checkout-index --all --prefix='target/solve-chapter/'
  if ($LASTEXITCODE -ne 0) { return $false }

  Write-Host "  sbt ""scalafmtCheckAll; exercises/testOnly $suite"" ..."
  Push-Location $verifyDir
  try {
    # One argument, the commands joined by `;`: under sbt 2 the launcher runs
    # the thin client, which joins separate arguments into a single command
    # line (`scalafmtCheckAll exercises/testOnly ...`, rejected with
    # `Expected '/'`).
    $out = @(sbt -batch "scalafmtCheckAll; exercises/testOnly $suite" 2>&1 |
      # munit colours its stack traces, and a colour code in front of `at`
      # hides the frame from the filter below — so the colours go first.
      ForEach-Object { "$_" -replace "`e\[[0-9;]*m", '' } |
      Where-Object { $_ -notmatch '^\s+at [A-Za-z_$][A-Za-z0-9_.$]*[.(]' -and $_ -notmatch 'sbt server disconnected' })
    $status = $LASTEXITCODE
  }
  finally { Pop-Location }
  $out | Out-Host
  if ($status -ne 0) { return $false }

  # A filter that matches no suite is a green run of nothing.
  if (-not ($out | Select-String -Pattern 'Passed: Total [1-9]' -Quiet)) {
    Write-Host "  no spec ran for $suite"
    return $false
  }
  return $true
}

# Staging comes first, so that what gets verified is what gets committed. If the
# check then fails, the index goes back to exactly how it was found.
$indexBefore = (git write-tree).Trim()
git add -- $pkgPath
$notes = "notes/ch$nn.md"
if (Test-Path $notes) { git add -- $notes }

$others = @(git status --porcelain --untracked-files=all | Where-Object {
    $_ -notmatch "^[AM]  $([regex]::Escape($pkgPath))/" -and $_ -notmatch "^[AM]  $([regex]::Escape($notes))$"
  } | Select-Object -First 5)
if ($others.Count -gt 0) {
  Write-Host ''
  Write-Host "heads up, left out of the commit (only $pkgPath/ and $notes go in):"
  $others | ForEach-Object { Write-Host "  $_" }
  Write-Host ''
}

if (-not $NoVerify) {
  Write-Host "verifying chapter $nn as it will be committed ..."
  if (Invoke-StagedVerification) {
    Remove-Item $verifyDir -Recurse -Force
  }
  else {
    git read-tree $indexBefore
    Write-Error 'the tree that failed is still at target/solve-chapter' -ErrorAction Continue
    throw "chapter $nn is not solved yet — nothing was committed"
  }
}
else {
  Write-Host 'skipping verification (-NoVerify)'
}

git diff --cached --quiet
if ($LASTEXITCODE -eq 0) {
  Write-Host "nothing new to commit on $Branch"
}
else {
  if (-not $NoVerify) {
    git commit -q -m $headline -m "Verified with: sbt ""exercises/testOnly $suite"""
  }
  else {
    git commit -q -m $headline
  }
  Write-Host "commit: $(git log -1 --oneline)"
}

if (-not $NoPush) {
  git remote get-url origin 2>$null | Out-Null
  if ($LASTEXITCODE -eq 0) {
    git push -u origin $Branch
    Write-Host "pushed: origin/$Branch"
  }
  else {
    Write-Host 'no remote configured — the commit stayed local.'
    Write-Host '  git remote add origin git@github.com:<you>/scala-advanced-fp.git'
    Write-Host "  git push -u origin $Branch"
  }
}
