#Requires -Version 7
<#
.SYNOPSIS
  Fills references/local/ from the manifest, references/README.md.

.EXAMPLE
  ./scripts/fetch-references.ps1
  ./scripts/fetch-references.ps1 -Check

.NOTES
  The bash twin is scripts/fetch-references.sh; -Check is its --check.

  Every entry the manifest marks `local/<file>` is downloaded from the first
  link that follows it, unless the file is already there. Only a link to a PDF
  is fetched (an arXiv abstract page counts, through its /pdf/ twin); a
  PostScript original or a release page is reported with its link, to be
  fetched by hand. It also reports the files in local/ and open/ the manifest
  does not name, and the open/ files it names that are missing.

  Nothing is overwritten or deleted: a file already in local/ is left alone,
  and a download that is not a PDF is discarded and reported.
#>
[CmdletBinding()]
param(
  [switch]$Check
)

$ErrorActionPreference = 'Stop'
$refs = Join-Path (Split-Path -Parent $PSScriptRoot) 'references'
# Push-Location, not Set-Location: a script runs in the caller's session, and
# would leave them in references/.
Push-Location $refs
try {
  if (-not (Test-Path README.md)) { throw 'references/README.md not found' }
  New-Item -ItemType Directory -Force -Path local | Out-Null

  # An entry wraps across lines, so the manifest is read as one string: each
  # `local/<file>` is paired with the first ](url) after it.
  $manifest = Get-Content README.md -Raw
  $entries = [regex]::Matches($manifest, '(?s)`local/([^`]+)`.*?\]\((\S+?)\)')
  if ($entries.Count -eq 0) { throw 'no local/ entries found in README.md' }

  $present = 0; $fetched = 0; $manual = 0; $failed = 0
  foreach ($m in $entries) {
    $file = $m.Groups[1].Value
    $url  = $m.Groups[2].Value
    if (Test-Path -LiteralPath "local/$file") { $present++; continue }

    if ($url -like 'https://arxiv.org/abs/*') {
      $url = 'https://arxiv.org/pdf/' + $url.Substring('https://arxiv.org/abs/'.Length)
    }
    elseif ($url -notlike '*.pdf') {
      Write-Host "  by hand   $file  <- $url"
      $manual++; continue
    }
    if ($Check) {
      Write-Host "  missing   $file  <- $url"
      $failed++; continue
    }

    # A failed or non-PDF download (an HTML error page, say) never takes the
    # final name, so a rerun tries it again.
    $tmp = "local/.$file.part"
    try {
      Invoke-WebRequest -Uri $url -OutFile $tmp -MaximumRetryCount 2
      $head = [System.Text.Encoding]::ASCII.GetString([System.IO.File]::ReadAllBytes((Resolve-Path $tmp))[0..4])
      if ($head -ne '%PDF-') { throw 'not a PDF' }
      Move-Item -LiteralPath $tmp -Destination "local/$file"
      Write-Host "  fetched   $file"
      $fetched++
    }
    catch {
      Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
      Write-Host "  FAILED    $file  <- $url"
      $failed++
    }
  }

  # The other direction: files on disk the manifest does not account for.
  $unlisted = 0
  foreach ($dir in 'local', 'open') {
    if (-not (Test-Path $dir)) { continue }
    foreach ($pdf in Get-ChildItem $dir -File -Filter '*.pdf') {
      $path = "$dir/$($pdf.Name)"
      if (-not $manifest.Contains("``$path``")) {
        Write-Host "  unlisted  $path"
        $unlisted++
      }
    }
  }
  $openNamed = [regex]::Matches($manifest, '`(open/[^`]+)`') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
  foreach ($path in $openNamed) {
    if (-not (Test-Path -LiteralPath $path)) {
      Write-Host "  missing   $path  (committed files: check out the branch again)"
      $failed++
    }
  }

  Write-Host "local: $($entries.Count) listed, $present present, $fetched fetched, $manual by hand, $failed missing or failed; $unlisted unlisted"
  $code = if ($failed -or $manual -or $unlisted) { 1 } else { 0 }
}
finally {
  Pop-Location
}
exit $code
