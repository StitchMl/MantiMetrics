<#
  run_batch.ps1 - Esegue MantiMetrics UNA volta: scarica i dati una sola volta e genera
  automaticamente tutte le 16 varianti di dataset in output/batch/.
  (Il vecchio loop a 16 esecuzioni non serve piu': la generazione delle varianti e' interna.)

  Uso:
    .\run_batch.ps1
    .\run_batch.ps1 -RepoUrl "https://github.com/apache/avro.git" -JiraKey "AVRO"
#>
param(
  [string]$RepoUrl = "https://github.com/apache/avro.git",
  [string]$JiraKey = "AVRO"
)
$ErrorActionPreference = "Stop"
Write-Host "Compilazione..." -ForegroundColor Cyan
& .\mvnw.cmd -q -DskipTests compile
Write-Host "Esecuzione (raccolta unica + generazione di tutte le varianti)..." -ForegroundColor Cyan
& .\mvnw.cmd -q exec:java "-Dexec.args=--repo-url=$RepoUrl --jira-key=$JiraKey"
Write-Host "Fatto: le varianti sono in output/batch/" -ForegroundColor Green
