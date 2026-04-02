# ═══════════════════════════════════════════════════════════
# Script PowerShell - Push parapente-tracking vers GitHub
# Lancez ce script depuis le dossier parapente-android/
# ═══════════════════════════════════════════════════════════

$repo = "https://github.com/jannfantail/parapente-tracking.git"

Write-Host "🪂 Parapente Tracker - Push GitHub" -ForegroundColor Cyan
Write-Host "Repo : $repo" -ForegroundColor Gray

# Vérifier qu'on est dans le bon dossier
if (-not (Test-Path "app/build.gradle")) {
    Write-Host "ERREUR : Lancez ce script depuis le dossier parapente-android/" -ForegroundColor Red
    exit 1
}

Write-Host "`nStructure détectée :" -ForegroundColor Green
Get-ChildItem -Recurse -File | Select-Object -ExpandProperty FullName | ForEach-Object {
    Write-Host "  $_" -ForegroundColor DarkGray
}

# Git init et push
git init
git add .
git status

Write-Host "`nFichiers à commiter ci-dessus. Continuer ? (O/N)" -ForegroundColor Yellow
$confirm = Read-Host
if ($confirm -ne "O" -and $confirm -ne "o") { exit 0 }

git commit -m "v1.0.004 - GPS + Accelerometre + UI live"
git branch -M main
git remote remove origin 2>$null
git remote add origin $repo
git push --force origin main

Write-Host "`n✅ Push terminé ! GitHub Actions va compiler l'APK." -ForegroundColor Green
Write-Host "Vérifiez : https://github.com/jannfantail/parapente-tracking/actions" -ForegroundColor Cyan
