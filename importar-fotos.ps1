# importar-fotos.ps1
# Empareja fotos de docentes por nombre y las sube via API

param(
    [string]$SourceDir  = "C:\Users\aaron\Downloads\FOTOGRAFIAS DOCENTES (2) (1)\FOTOGRAFIAS DOCENTES",
    [string]$BaseUrl    = "http://localhost:8091/sgdc-udefa",
    [string]$Tenant     = "gestion_docente_emi",
    [string]$Usuario    = "",
    [string]$Password   = ""
)

# ─── CREDENCIALES ───────────────────────────────────────────────────────────────
if (-not $Usuario) { $Usuario  = Read-Host "Usuario del sistema (ej: admin)" }
if (-not $Password){ $Password = Read-Host "Contraseña" }

# ─── FUNCIONES ─────────────────────────────────────────────────────────────────

function Normalizar([string]$texto) {
    # Quitar acentos usando descomposición Unicode
    $form = [System.Text.NormalizationForm]::FormD
    $sb   = [System.Text.StringBuilder]::new()
    foreach ($c in $texto.Normalize($form).ToCharArray()) {
        $cat = [System.Globalization.CharUnicodeInfo]::GetUnicodeCategory($c)
        if ($cat -ne [System.Globalization.UnicodeCategory]::NonSpacingMark) {
            $sb.Append($c) | Out-Null
        }
    }
    # Minúsculas, sin puntuación ni guiones
    return ($sb.ToString().ToLower() -replace '[^a-z0-9\s]', ' ' -replace '\s+', ' ').Trim()
}

function PalabrasClave([string]$texto) {
    # Ignora palabras muy cortas y siglas militares comunes
    $excluir = @('de','del','la','el','los','las','y','e','a','al','myr','ici','ret','gral','cor','tcor','cap','tte','sgto','mto')
    return ($texto -split '\s+') | Where-Object { $_.Length -ge 3 -and $_ -notin $excluir }
}

function Score([string]$nombreFoto, [string]$nombreDocente) {
    $palabrasFoto = PalabrasClave (Normalizar $nombreFoto)
    $palabrasDoc  = PalabrasClave (Normalizar $nombreDocente)
    if ($palabrasFoto.Count -eq 0) { return 0 }
    $coinciden = ($palabrasFoto | Where-Object { $palabrasDoc -contains $_ }).Count
    return [math]::Round($coinciden / $palabrasFoto.Count * 100)
}

# ─── LOGIN ──────────────────────────────────────────────────────────────────────
Write-Host "`n[1/4] Autenticando..." -ForegroundColor Cyan

$loginBody = @{ username = $Usuario; password = $Password } | ConvertTo-Json
try {
    $loginResp = Invoke-RestMethod -Uri "$BaseUrl/api/login" `
        -Method POST -ContentType "application/json" `
        -Headers @{ "X-Tenant-ID" = $Tenant } `
        -Body $loginBody
} catch {
    Write-Host "ERROR: No se pudo autenticar. Verifica usuario/password y que el servidor esté corriendo." -ForegroundColor Red
    exit 1
}
$jwt = $loginResp.token
if (-not $jwt) {
    Write-Host "ERROR: Login fallido — $($loginResp.message)" -ForegroundColor Red
    exit 1
}
Write-Host "OK — conectado como $Usuario" -ForegroundColor Green

# ─── OBTENER DOCENTES ───────────────────────────────────────────────────────────
Write-Host "[2/4] Obteniendo docentes de la BD..." -ForegroundColor Cyan

$headers = @{
    "Authorization" = "Bearer $jwt"
    "X-Tenant-ID"   = $Tenant
}
try {
    $docentes = Invoke-RestMethod -Uri "$BaseUrl/api/docentes" -Headers $headers
} catch {
    Write-Host "ERROR al obtener docentes: $_" -ForegroundColor Red
    exit 1
}
Write-Host "OK — $($docentes.Count) docentes encontrados" -ForegroundColor Green

# ─── MATCHING ──────────────────────────────────────────────────────────────────
Write-Host "[3/4] Emparejando fotos con docentes..." -ForegroundColor Cyan

$extensiones = @("*.jpg","*.jpeg","*.png","*.bmp","*.gif","*.webp")
$fotos = Get-ChildItem $SourceDir -Include $extensiones -Recurse

$resultados = @()
foreach ($foto in $fotos) {
    $nombreSinExt = [System.IO.Path]::GetFileNameWithoutExtension($foto.Name)
    $mejor = $null
    $mejorScore = 0

    foreach ($doc in $docentes) {
        $s = Score $nombreSinExt $doc.nombre
        if ($s -gt $mejorScore) {
            $mejorScore = $s
            $mejor = $doc
        }
    }

    $resultados += [PSCustomObject]@{
        Foto           = $foto.Name
        FotoPath       = $foto.FullName
        DocenteNombre  = if ($mejor -and $mejorScore -ge 40) { $mejor.nombre } else { "— SIN MATCH —" }
        DocenteId      = if ($mejor -and $mejorScore -ge 40) { $mejor.docente_id } else { 0 }
        Score          = $mejorScore
        Ext            = $foto.Extension.ToLower()
    }
}

# Mostrar tabla de resultados
Write-Host ""
$resultados | Sort-Object Score -Descending | Format-Table @{L="Score%";E={$_.Score}}, @{L="Foto";E={$_.Foto}}, @{L="Docente en BD";E={$_.DocenteNombre}} -AutoSize

$conMatch    = @($resultados | Where-Object { $_.DocenteId -gt 0 })
$sinMatch    = @($resultados | Where-Object { $_.DocenteId -eq 0 })

Write-Host "Fotos con match:    $($conMatch.Count)" -ForegroundColor Green
Write-Host "Fotos sin match:    $($sinMatch.Count)" -ForegroundColor Yellow
if ($sinMatch.Count -gt 0) {
    Write-Host "Sin match: $($sinMatch.Foto -join ', ')" -ForegroundColor Yellow
}

# ─── CONFIRMACIÓN Y SUBIDA ──────────────────────────────────────────────────────
if ($conMatch.Count -eq 0) {
    Write-Host "`nNo hay fotos para subir." -ForegroundColor Yellow
    exit 0
}

Write-Host ""
$resp = Read-Host "[4/4] ¿Subir las $($conMatch.Count) fotos con match? (s/n)"
if ($resp -notmatch '^[sS]') {
    Write-Host "Cancelado." -ForegroundColor Yellow
    exit 0
}

$ok = 0; $fail = 0
foreach ($r in $conMatch) {
    Write-Host "  Subiendo $($r.Foto) → Docente $($r.DocenteId) ($($r.DocenteNombre))..." -NoNewline

    # Construir multipart form
    $boundary  = [System.Guid]::NewGuid().ToString()
    $LF        = "`r`n"
    $fileBytes = [System.IO.File]::ReadAllBytes($r.FotoPath)
    $mimeType  = switch ($r.Ext) {
        ".png"  { "image/png" }
        ".gif"  { "image/gif" }
        ".bmp"  { "image/bmp" }
        ".webp" { "image/webp" }
        default { "image/jpeg" }
    }
    $bodyParts = [System.Text.Encoding]::UTF8.GetBytes(
        "--$boundary$LF" +
        "Content-Disposition: form-data; name=`"foto`"; filename=`"$($r.Foto)`"$LF" +
        "Content-Type: $mimeType$LF$LF"
    )
    $bodyEnd = [System.Text.Encoding]::UTF8.GetBytes(
        "$LF--$boundary$LF" +
        "Content-Disposition: form-data; name=`"docente_id`"$LF$LF" +
        "$($r.DocenteId)$LF" +
        "--$boundary--$LF"
    )
    $body = $bodyParts + $fileBytes + $bodyEnd

    try {
        $uploadResp = Invoke-RestMethod -Uri "$BaseUrl/api/fotos" -Method POST `
            -Headers @{ "Authorization" = "Bearer $jwt"; "X-Tenant-ID" = $Tenant } `
            -ContentType "multipart/form-data; boundary=$boundary" `
            -Body $body
        Write-Host " OK" -ForegroundColor Green
        $ok++
    } catch {
        Write-Host " ERROR: $_" -ForegroundColor Red
        $fail++
    }
}

Write-Host ""
Write-Host "Listo: $ok fotos subidas, $fail errores." -ForegroundColor Cyan
