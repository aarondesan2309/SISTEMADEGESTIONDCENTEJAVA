# =============================================
# INICIAR Sistema de Gestion Docente + ngrok
# =============================================

# 1. Matar procesos anteriores si existen
Write-Host "Deteniendo procesos anteriores..." -ForegroundColor Yellow
Get-Process ngrok -ErrorAction SilentlyContinue | Stop-Process -Force
$pid8091 = (netstat -ano | findstr ":8091 ") -replace '.*\s+(\d+)$','$1' | Select-Object -First 1
if ($pid8091) { Stop-Process -Id $pid8091 -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 2

# 2. Iniciar Spring Boot
Write-Host "Iniciando Spring Boot (puerto 8091)..." -ForegroundColor Cyan
$appLog = "C:\temp\gestion-docente-web\app.log"
Start-Process -FilePath "C:\tools\apache-maven-3.9.9\bin\mvn.cmd" `
    -ArgumentList "spring-boot:run" `
    -WorkingDirectory "C:\temp\gestion-docente-web\backend" `
    -WindowStyle Hidden `
    -RedirectStandardOutput $appLog `
    -RedirectStandardError "C:\temp\gestion-docente-web\app-err.log"

# 3. Esperar que Spring Boot arranque
Write-Host "Esperando que la app arranque..." -ForegroundColor Yellow
$intentos = 0
do {
    Start-Sleep -Seconds 3
    $intentos++
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8091/sgdc-udefa/api/contratos" -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
        $appReady = $true
    } catch { $appReady = $false }
} while (-not $appReady -and $intentos -lt 15)

if (-not $appReady) {
    Write-Host "ERROR: La app no arranco. Revisa app.log" -ForegroundColor Red
    exit 1
}
Write-Host "Spring Boot listo!" -ForegroundColor Green

# 4. Iniciar ngrok
Write-Host "Iniciando ngrok..." -ForegroundColor Cyan
Start-Process -FilePath "C:\tools\ngrok.exe" `
    -ArgumentList "http 8091 --log stdout" `
    -WindowStyle Hidden `
    -RedirectStandardOutput "C:\temp\ngrok.log" `
    -RedirectStandardError "C:\temp\ngrok-err.log"
Start-Sleep -Seconds 4

# 5. Leer URL publica
$url = (Get-Content "C:\temp\ngrok.log" | Select-String "url=https://") -replace '.*url=(https://\S+)','$1'

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  SISTEMA LISTO" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  Local : http://localhost:8091/sgdc-udefa/" -ForegroundColor White
Write-Host "  Publico: $url/sgdc-udefa/" -ForegroundColor Yellow
Write-Host "  Panel ngrok: http://localhost:4040" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "Presiona cualquier tecla para cerrar este script (ngrok y la app siguen corriendo)..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
