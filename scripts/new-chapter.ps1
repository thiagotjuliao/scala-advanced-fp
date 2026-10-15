#Requires -Version 7
<#
.SYNOPSIS
  Scaffolds a chapter: the theory document, the walkthrough, and a matching
  exercise / solution / spec set on both sides of the repository.

.EXAMPLE
  ./scripts/new-chapter.ps1 7 "Match types"
  ./scripts/new-chapter.ps1 7 -Add -Exercises 2

.NOTES
  The bash twin is scripts/new-chapter.sh; the two take the same arguments and
  only the flags change shape. Nothing here is ever overwritten: a file that
  already exists is reported and left alone.
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory, Position = 0)][int]$Number,
  [Parameter(Position = 1)][string]$Title,
  [int]$Exercises = 3,
  [switch]$Add,
  [switch]$NoDoc
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$nn = '{0:d2}' -f $Number
$templates = 'docs/templates'

# A chapter is identified by its number alone. The slug and package name are
# derived from the title the first time and read back off disk afterwards, so
# -Add never has to be told the title again.
$existingPkg = Get-ChildItem 'modules/exercises/src/main/scala/advfp' -Directory -Filter "ch$nn*" -ErrorAction SilentlyContinue | Select-Object -First 1
$existingDoc = Get-ChildItem 'docs/theory' -File -Filter "ch$nn-*.md" -ErrorAction SilentlyContinue | Select-Object -First 1

if ($existingPkg) {
  $pkg  = $existingPkg.Name
  $slug = if ($existingDoc) { $existingDoc.BaseName -replace "^ch$nn-", '' } else { $pkg -replace "^ch$nn", '' }
  if (-not $Add) { Write-Host "chapter $nn already exists ($pkg) — adding to it" }
}
else {
  if (-not $Title) { throw "chapter $nn does not exist yet, so it needs a title" }
  $slug = ($Title.ToLowerInvariant() -replace '[^a-z0-9]+', '-').Trim('-')
  $pkg  = "ch$nn" + ($slug -replace '-', '')
}

if (-not $Title -and $existingDoc) {
  $Title = (Get-Content $existingDoc.FullName -First 1) -replace '^# Chapter \d+ — ', ''
}
if (-not $Title) { $Title = $slug }

$exMain = "modules/exercises/src/main/scala/advfp/$pkg"
$exTest = "modules/exercises/src/test/scala/advfp/$pkg"
$soMain = "modules/solutions/src/main/scala/advfp/$pkg"
$soTest = "modules/solutions/src/test/scala/advfp/$pkg"
foreach ($d in @($exMain, $exTest, $soMain, $soTest, 'docs/theory')) {
  New-Item -ItemType Directory -Force -Path $d | Out-Null
}

function Render([string]$Template, [string]$Out, [string]$Ee = '') {
  if (Test-Path $Out) { Write-Host "  kept:    $Out"; return }
  (Get-Content $Template -Raw).
    Replace('{{NN}}', $nn).
    Replace('{{TITLE}}', $Title).
    Replace('{{SLUG}}', $slug).
    Replace('{{PACKAGE}}', $pkg).
    Replace('{{EE}}', $Ee) | Set-Content -Path $Out -NoNewline
  Write-Host "  created: $Out"
}

Write-Host "chapter $nn — $Title  (package advfp.$pkg)"

if (-not $NoDoc) { Render "$templates/chapter.md" "docs/theory/ch$nn-$slug.md" }
Render "$templates/Walkthrough.scala.tmpl" "$exMain/Walkthrough.scala"

# Exercise numbering continues after whatever is already there, so -Add never
# collides with an exercise that has been written.
$highest = 0
Get-ChildItem $exMain -Filter 'Exercise*.scala' -ErrorAction SilentlyContinue | ForEach-Object {
  $n = [int]($_.BaseName -replace '^Exercise', '')
  if ($n -gt $highest) { $highest = $n }
}

for ($i = 1; $i -le $Exercises; $i++) {
  $ee = '{0:d2}' -f ($highest + $i)
  Render "$templates/Exercise.scala.tmpl"         "$exMain/Exercise$ee.scala"     $ee
  Render "$templates/ExerciseSpec.scala.tmpl"     "$exTest/Exercise${ee}Spec.scala" $ee
  Render "$templates/ExerciseSolution.scala.tmpl" "$soMain/Exercise$ee.scala"     $ee
  Render "$templates/ExerciseSpec.scala.tmpl"     "$soTest/Exercise${ee}Spec.scala" $ee
}

Write-Host @"

The spec is the contract: write it first, from the theory document, and let it
fail. Then the stub, then the solution.

  sbt "exercises/testOnly advfp.$pkg.*"
      what is left to solve
  sbt verify
      what the tag will be held to
  ./scripts/finish-chapter.ps1 $nn
      close it
"@
