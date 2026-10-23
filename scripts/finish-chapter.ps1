#Requires -Version 7
<#
.SYNOPSIS
  Closes a finished chapter: verifies it, commits it and creates the chNN tag.

.EXAMPLE
  ./scripts/finish-chapter.ps1 7
  ./scripts/finish-chapter.ps1 7 -NoPush -Title "Match types"

.NOTES
  The bash twin is scripts/finish-chapter.sh. The tag is only born after the
  chapter is actually finished: documented, walked through, exercised on both
  sides, and green. A half-written chapter does not get one.
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)][int]$Number,
  [switch]$NoVerify,
  [switch]$NoPush,
  [switch]$All,
  [string]$Title
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$nn  = '{0:d2}' -f $Number
$tag = "ch$nn"

$pkgDir = Get-ChildItem 'modules/exercises/src/main/scala/advfp' -Directory -Filter "ch$nn*" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $pkgDir) { throw "chapter $nn does not exist — start with ./scripts/new-chapter.ps1 $nn ""Title""" }
$pkg = $pkgDir.Name
$doc = Get-ChildItem 'docs/theory' -File -Filter "ch$nn-*.md" -ErrorAction SilentlyContinue | Select-Object -First 1

git rev-parse -q --verify "refs/tags/$tag" 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) { throw "tag $tag already exists" }

if (-not $Title -and $doc) {
  $Title = (Get-Content $doc.FullName -First 1) -replace '^# Chapter \d+ — ', ''
}
$headline   = "chapter $nn"
if ($Title) { $headline += " — " + $Title.ToLowerInvariant() }
$tagSubject = "Chapter $nn" + $(if ($Title) { " — $Title" } else { '' })

# Where the staged tree gets built. Under target/, so it is git-ignored and
# `sbt clean` takes it away.
$verifyDir = Join-Path $root 'target/finish-chapter'

# Everything a finished chapter owes, checked against the tree that is about to
# be tagged. These are the checks `sbt verify` cannot make: a chapter whose
# document is still a template compiles and tests perfectly green — it is simply
# not finished.
function Test-Structure([string]$Tree) {
  $ok = $true

  $d = Get-ChildItem (Join-Path $Tree 'docs/theory') -File -Filter "ch$nn-*.md" -ErrorAction SilentlyContinue | Select-Object -First 1
  if (-not $d) {
    Write-Host "  missing: docs/theory/ch$nn-*.md"; $ok = $false
  }
  elseif (Select-String -Path $d.FullName -Pattern '<!-- TODO' -Quiet) {
    Write-Host "  unfinished: $($d.Name) still has template TODO markers"; $ok = $false
  }

  $exMain = Join-Path $Tree "modules/exercises/src/main/scala/advfp/$pkg"
  $exTest = Join-Path $Tree "modules/exercises/src/test/scala/advfp/$pkg"
  $soMain = Join-Path $Tree "modules/solutions/src/main/scala/advfp/$pkg"
  $soTest = Join-Path $Tree "modules/solutions/src/test/scala/advfp/$pkg"

  # A walkthrough left as the template passes `sbt verify` without trouble: it
  # compiles, and it says nothing. The TODO the template ships with is what
  # tells the two apart.
  $walkthrough = Join-Path $exMain 'Walkthrough.scala'
  if (-not (Test-Path $walkthrough)) {
    Write-Host "  missing: $pkg/Walkthrough.scala"; $ok = $false
  }
  elseif (Select-String -Path $walkthrough -Pattern 'TODO' -Quiet) {
    Write-Host "  unfinished: $pkg/Walkthrough.scala is still the template"; $ok = $false
  }

  $exercises = @(Get-ChildItem $exMain -Filter 'Exercise*.scala' -ErrorAction SilentlyContinue)
  if ($exercises.Count -eq 0) { Write-Host '  missing: the chapter has no exercises'; $ok = $false }

  foreach ($f in $exercises) {
    $ee = $f.BaseName -replace '^Exercise', ''
    foreach ($needed in @(
        @{ Path = Join-Path $exTest "Exercise${ee}Spec.scala"; Label = "exercises/…/$pkg/Exercise${ee}Spec.scala" },
        @{ Path = Join-Path $soMain "Exercise$ee.scala";       Label = "solutions/…/$pkg/Exercise$ee.scala" },
        @{ Path = Join-Path $soTest "Exercise${ee}Spec.scala"; Label = "solutions/…/$pkg/Exercise${ee}Spec.scala" })) {
      if (-not (Test-Path $needed.Path)) { Write-Host "  missing: $($needed.Label)"; $ok = $false }
    }
  }

  # An answer key that still says `Unsolved`, or still says TODO, is not an
  # answer key — and `sbt verify` would not notice.
  if (Test-Path $soMain) {
    $dirty = @(Get-ChildItem $soMain -File -Recurse | Select-String -Pattern 'Unsolved', 'TODO' -List)
    if ($dirty.Count -gt 0) {
      Write-Host "  unfinished: the solutions for $pkg still contain Unsolved or TODO:"
      $dirty | ForEach-Object { Write-Host "    $($_.Path)" }
      $ok = $false
    }
  }

  return $ok
}

# Checks out the *index* into a scratch directory and runs the gate in there.
# Running it against the working tree would pass on files that are not going
# into the commit — which is how a green run publishes a tree that does not
# compile. This copy holds exactly what the commit will hold.
function Invoke-StagedVerification {
  if (Test-Path $verifyDir) { Remove-Item $verifyDir -Recurse -Force }
  New-Item -ItemType Directory -Force -Path $verifyDir | Out-Null
  git checkout-index --all --prefix='target/finish-chapter/'
  if ($LASTEXITCODE -ne 0) { return $false }

  Write-Host '  structure ...'
  if (-not (Test-Structure $verifyDir)) { return $false }

  Write-Host '  sbt verify ...'
  Push-Location $verifyDir
  try {
    # `verify` is scalafmtCheckAll + scalafmtSbtCheck + core/testFull +
    # solutions/testFull + exercises/Test/compile; see build.sbt for why it is testFull and not test.
    #
    # munit colours its stack traces, and a colour code in front of `at` hides
    # the frame from the filter — so the colours go first.
    # `--server` runs sbt in the foreground instead of through the thin client,
    # which under sbt 2 starts a server in the background that outlives the
    # script — an orphan left in the scratch directory, holding memory.
    sbt --server -batch verify 2>&1 |
      ForEach-Object { "$_" -replace "`e\[[0-9;]*m", '' } |
      Where-Object { $_ -notmatch '^\s+at [A-Za-z_$][A-Za-z0-9_.$]*[.(]' -and $_ -notmatch 'sbt server disconnected' } |
      Out-Host
    return ($LASTEXITCODE -eq 0)
  }
  finally { Pop-Location }
}

# Staging comes first, so that what gets verified is what gets committed. If the
# gate then fails, the index goes back to exactly how it was found.
$indexBefore = (git write-tree).Trim()

if ($All) {
  git add -A
}
else {
  git add -- $pkgDir.FullName
  foreach ($d in @('modules/exercises/src/test', 'modules/solutions/src/main', 'modules/solutions/src/test')) {
    $p = "$d/scala/advfp/$pkg"
    if (Test-Path $p) { git add -- $p }
  }
  if ($doc) { git add -- $doc.FullName }

  $others = @(git status --porcelain --untracked-files=all | Where-Object { $_ -notmatch [regex]::Escape($pkg) -and ($null -eq $doc -or $_ -notmatch [regex]::Escape($doc.Name)) } | Select-Object -First 5)
  if ($others.Count -gt 0) {
    Write-Host ''
    Write-Host 'heads up, left out of the commit (use -All to include):'
    $others | ForEach-Object { Write-Host "  $_" }
    Write-Host ''
  }
}

if (-not $NoVerify) {
  Write-Host "verifying $headline as it will be committed ..."
  if (Invoke-StagedVerification) {
    Remove-Item $verifyDir -Recurse -Force
  }
  else {
    git read-tree $indexBefore
    Write-Error "the tree that failed is still at target/finish-chapter"
    throw 'verification failed — nothing was committed or tagged'
  }
}
else {
  Write-Host 'skipping verification (-NoVerify)'
}

git diff --cached --quiet
if ($LASTEXITCODE -eq 0) {
  Write-Host 'nothing new to commit — tagging HEAD instead'
}
else {
  git commit -q -m $headline -m 'Verified with: sbt verify'
  Write-Host "commit: $(git log -1 --oneline)"
}

git tag -a $tag -m $tagSubject
Write-Host "tag: $tag"

if (-not $NoPush) {
  git remote get-url origin 2>$null | Out-Null
  if ($LASTEXITCODE -eq 0) {
    $branch = (git branch --show-current).Trim()
    git push --follow-tags origin $branch
    Write-Host "pushed: origin/$branch (with tag $tag)"
  }
  else {
    Write-Host 'no remote configured — the commit and the tag stayed local.'
    Write-Host '  git remote add origin git@github.com:<you>/scala-advanced-fp.git'
    Write-Host "  git push -u --follow-tags origin $(git branch --show-current)"
  }
}
