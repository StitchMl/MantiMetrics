<#
  run_batch.ps1 - Genera le varianti di dataset MantiMetrics per lo studio di ablation.

  Assi sperimentali (modifica gli array per ridurre la matrice):
    --percentage        34 (snoring ~66%) / 20 (snoring 80%)
    --proportion        total / incremental
    --github-issues     off / on
    --exclude-churn-zero off / on
  Combinazioni totali = 2^4 = 16.

  Ogni run scrive output/<repo>_dataset_class.csv (nome fisso): lo script lo rinomina
  in output/batch/<repo>_<variante>.csv per non sovrascrivere.

  Uso:
    .\run_batch.ps1
    .\run_batch.ps1 -RepoUrl "https://github.com/apache/avro.git" -JiraKey "AVRO" -RepoName "avro"
#>
param(
  [string]$RepoUrl  = "https://github.com/apache/avro.git",
  [string]$JiraKey  = "AVRO",
  [string]$RepoName = "avro"
)

$ErrorActionPreference = "Stop"

# --- Assi (riduci qui per fare meno run) ---
$percentages = @(34, 20)
$proportions = @("total", "incremental")
$githubOpts  = @($false, $true)
$churnOpts   = @($false, $true)

$outDir   = "output"
$batchDir = Join-Path $outDir "batch"
New-Item -ItemType Directory -Force -Path $batchDir | Out-Null

$rawCsv       = Join-Path $outDir "${RepoName}_dataset_class.csv"
$rawArtifacts = Join-Path $outDir "${RepoName}_dataset_class_artifacts"

Write-Host "Compilazione..." -ForegroundColor Cyan
& .\mvnw.cmd -q -DskipTests compile

$total = $percentages.Count * $proportions.Count * $githubOpts.Count * $churnOpts.Count
$run = 0
foreach ($p in $percentages) {
  foreach ($prop in $proportions) {
    foreach ($gh in $githubOpts) {
      foreach ($ch in $churnOpts) {
        $run++
        $mvnArgs = "--repo-url=$RepoUrl --jira-key=$JiraKey --percentage=$p --proportion=$prop"
        $ghTag = "gh0"; if ($gh) { $mvnArgs += " --github-issues";      $ghTag = "gh1" }
        $chTag = "churn0"; if ($ch) { $mvnArgs += " --exclude-churn-zero"; $chTag = "churn1" }
        $tag = "pct${p}_${prop}_${ghTag}_${chTag}"

        Write-Host "=== [$run/$total] $tag ===" -ForegroundColor Cyan
        & .\mvnw.cmd -q exec:java "-Dexec.args=$mvnArgs"

        if (Test-Path $rawCsv) {
          Move-Item -Force $rawCsv (Join-Path $batchDir "${RepoName}_${tag}.csv")
        } else {
          Write-Host "  ATTENZIONE: nessun CSV prodotto per $tag" -ForegroundColor Yellow
        }
        if (Test-Path $rawArtifacts) {
          $dest = Join-Path $batchDir "${RepoName}_${tag}_artifacts"
          if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
          Move-Item -Force $rawArtifacts $dest
        }
      }
    }
  }
}
Write-Host "Fatto: $run varianti in $batchDir" -ForegroundColor Green
