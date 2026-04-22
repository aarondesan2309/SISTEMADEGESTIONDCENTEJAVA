# build_war.ps1
$projectDir = "c:\temp\gestion-docente-web"
$outputWar = "c:\temp\GestionDocente.war"
$tempDir = "$projectDir\temp_war_build"

Write-Host "Iniciando empaquetado WAR para despliegue en Tomcat..."

# 1. Limpiar directorio temporal si existe
if (Test-Path $tempDir) {
    Remove-Item -Recurse -Force $tempDir
}
if (Test-Path $outputWar) {
    Remove-Item -Force $outputWar
}

# 2. Crear estructura temporal
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null
New-Item -ItemType Directory -Force -Path "$tempDir\WEB-INF" | Out-Null

# 3. Copiar archivos del prototipo
Copy-Item "$projectDir\index.html" -Destination $tempDir
Copy-Item "$projectDir\index.css" -Destination $tempDir
Copy-Item "$projectDir\app.js" -Destination $tempDir

# 4. Crear web.xml básico
$webXmlContent = @"
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee 
                             http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd"
         version="3.1">
    <display-name>PrototipoGestionDocente</display-name>
</web-app>
"@

Set-Content -Path "$tempDir\WEB-INF\web.xml" -Value $webXmlContent -Encoding UTF8

# 5. Comprimir a formato ZIP y luego renombrar a WAR
$tempZip = "c:\temp\GestionDocente.zip"
if (Test-Path $tempZip) { Remove-Item -Force $tempZip }
Compress-Archive -Path "$tempDir\*" -DestinationPath $tempZip -Force
Rename-Item -Path $tempZip -NewName "GestionDocente.war" -Force
# 6. Limpieza
Remove-Item -Recurse -Force $tempDir

Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "¡Empaquetado completado exitosamente!" -ForegroundColor Green
Write-Host "Archivo WAR generado en:" -ForegroundColor White
Write-Host " -> $outputWar" -ForegroundColor Yellow
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "Puedes subir este archivo directamente a tu servidor Tomcat."
