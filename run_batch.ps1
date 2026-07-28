<#
  run_batch.ps1 - Esegue MantiMetrics UNA volta: scarica i dati una sola volta e genera
  automaticamente tutte le 16 varianti di dataset in output/batch/.

  Uso:
    .\run_batch.ps1
    .\run_batch.ps1 -RepoUrl "https://github.com/apache/avro.git" -JiraKey "AVRO" -SonarKey "StitchMl_avro"
#>
param(
  [string]$RepoUrl  = "https://github.com/apache/avro.git",
  [string]$JiraKey  = "AVRO",
  [string]$SonarKey = "StitchMl_avro"
)
$ErrorActionPreference = "Stop"
Write-Host "Compilazione..." -ForegroundColor Cyan
& .\mvnw.cmd -q -DskipTests compile
$cliArgs = "--repo-url=$RepoUrl --jira-key=$JiraKey"
if ($SonarKey -and $SonarKey.Trim()) { $cliArgs += " --sonar-key=$SonarKey" }
Write-Host "Esecuzione (raccolta unica + generazione di tutte le varianti)..." -ForegroundColor Cyan
& .\mvnw.cmd -q exec:java "-Dexec.args=$cliArgs"
Write-Host "Fatto: le varianti sono in output/batch/" -ForegroundColor Green
