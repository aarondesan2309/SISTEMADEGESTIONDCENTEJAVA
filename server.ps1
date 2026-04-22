# =========================================
# CONFIGURACIÓN UTF-8 (CLAVE)
# =========================================
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
chcp 65001 > $null

# =========================================
# SERVIDOR
# =========================================
$port = 8091
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://localhost:$port/")
$listener.Start()

Write-Host "Servidor Web activo en http://localhost:$port/"
Write-Host "Presione CTRL+C para detener."

# =========================================
# CONFIG
# =========================================
$wwwroot = "C:\temp\gestion-docente-web"
$env:PGPASSWORD = "1234"
$psqlPath = "C:\Program Files\PostgreSQL\16\bin\psql.exe"
$dbParams = @("-U", "postgres", "-d", "gestion_docente_emi", "-q", "-t", "-A")

# =========================================
# RESPUESTA JSON UTF8
# =========================================
function Send-JsonResponse($response, $jsonString) {
    if (-not $jsonString) { $jsonString = "[]" }
    $jsonString = $jsonString.Trim()
    $buffer = [System.Text.Encoding]::UTF8.GetBytes($jsonString)
    $response.ContentType = "application/json; charset=utf-8"
    $response.ContentLength64 = $buffer.Length
    $response.OutputStream.Write($buffer, 0, $buffer.Length)
    $response.OutputStream.Flush()
    $response.Close()
}

# =========================================
# ESCAPE SQL
# =========================================
function Esc($v) { if ($v) { return $v.Replace("'", "''") } return "" }

# =========================================
# PARSEAR MULTIPART FORM DATA
# =========================================
function Parse-MultipartFormData($request) {
    $result = @{}
    $contentType = $request.ContentType
    if (-not $contentType -or -not $contentType.Contains("multipart/form-data")) { return $result }

    $boundaryMatch = [regex]::Match($contentType, 'boundary=(.+)')
    if (-not $boundaryMatch.Success) { return $result }
    $boundary = $boundaryMatch.Groups[1].Value.Trim()

    $ms = New-Object System.IO.MemoryStream
    $request.InputStream.CopyTo($ms)
    $bodyBytes = $ms.ToArray()
    $ms.Close()

    $bodyStr = [System.Text.Encoding]::UTF8.GetString($bodyBytes)
    $parts = $bodyStr -split "--$boundary"

    foreach ($part in $parts) {
        if ($part.Trim() -eq "" -or $part.Trim() -eq "--") { continue }

        $headerEnd = $part.IndexOf("`r`n`r`n")
        if ($headerEnd -lt 0) { $headerEnd = $part.IndexOf("`n`n") }
        if ($headerEnd -lt 0) { continue }

        $headers = $part.Substring(0, $headerEnd)
        $nameMatch = [regex]::Match($headers, 'name="([^"]+)"')
        if (-not $nameMatch.Success) { continue }
        $fieldName = $nameMatch.Groups[1].Value

        $filenameMatch = [regex]::Match($headers, 'filename="([^"]+)"')
        if ($filenameMatch.Success) {
            $result[$fieldName + "_filename"] = $filenameMatch.Groups[1].Value
            # For file content, store raw bytes
            $headerBytes = [System.Text.Encoding]::UTF8.GetBytes($part.Substring(0, $headerEnd) + "`r`n`r`n")
            $contentStart = $headerEnd + 4
            $content = $part.Substring($contentStart).TrimEnd("`r", "`n", "-")
            $result[$fieldName + "_content"] = $content
            # Also store the raw bytes from the original body
            $result[$fieldName + "_bytes"] = $bodyBytes
        } else {
            $content = $part.Substring($headerEnd + 4).Trim("`r", "`n", " ", "-")
            $result[$fieldName] = $content
        }
    }
    return $result
}

# =========================================
# GUARDAR ARCHIVO SUBIDO (binario seguro)
# =========================================
function Save-UploadedFile($request, $destDir, $prefix) {
    $contentType = $request.ContentType
    if (-not $contentType -or -not $contentType.Contains("multipart/form-data")) { return $null }

    $boundaryMatch = [regex]::Match($contentType, 'boundary=(.+)')
    if (-not $boundaryMatch.Success) { return $null }
    $boundary = "--" + $boundaryMatch.Groups[1].Value.Trim()

    # Leer todo el body como bytes
    $ms = New-Object System.IO.MemoryStream
    $request.InputStream.CopyTo($ms)
    $allBytes = $ms.ToArray()
    $ms.Close()

    # Encontrar filename y el inicio del contenido binario
    $bodyStr = [System.Text.Encoding]::UTF8.GetString($allBytes)
    $filenameMatch = [regex]::Match($bodyStr, 'filename="([^"]+)"')
    if (-not $filenameMatch.Success) { return $null }
    $origFilename = $filenameMatch.Groups[1].Value
    $ext = [System.IO.Path]::GetExtension($origFilename).ToLower()

    # Buscar docente_id en el body
    $idMatch = [regex]::Match($bodyStr, 'name="docente_id"\r?\n\r?\n(\d+)')
    $docId = if ($idMatch.Success) { $idMatch.Groups[1].Value } else { "0" }

    $savedFilename = "${prefix}_${docId}${ext}"
    $destPath = Join-Path $destDir $savedFilename

    # Encontrar los bytes del archivo: buscar doble CRLF después del header del archivo
    $headerPattern = "Content-Type:"
    $headerIdx = $bodyStr.IndexOf($headerPattern)
    if ($headerIdx -lt 0) { return $null }
    $afterHeader = $bodyStr.IndexOf("`r`n`r`n", $headerIdx)
    if ($afterHeader -lt 0) { return $null }
    $dataStart = $afterHeader + 4

    # Encontrar el boundary final
    $endBoundary = $bodyStr.IndexOf($boundary, $dataStart)
    if ($endBoundary -lt 0) { $endBoundary = $allBytes.Length }

    # Calcular posiciones en bytes (con cuidado por UTF-8)
    $preData = $bodyStr.Substring(0, $dataStart)
    $startBytePos = [System.Text.Encoding]::UTF8.GetByteCount($preData)
    $preEnd = $bodyStr.Substring(0, $endBoundary)
    $endBytePos = [System.Text.Encoding]::UTF8.GetByteCount($preEnd)

    # Quitar posibles CRLF antes del boundary
    while ($endBytePos -gt $startBytePos -and ($allBytes[$endBytePos - 1] -eq 10 -or $allBytes[$endBytePos - 1] -eq 13)) {
        $endBytePos--
    }

    $fileLength = $endBytePos - $startBytePos
    if ($fileLength -le 0) { return $null }

    $fileBytes = New-Object byte[] $fileLength
    [Array]::Copy($allBytes, $startBytePos, $fileBytes, 0, $fileLength)
    [System.IO.File]::WriteAllBytes($destPath, $fileBytes)

    return $savedFilename
}


# =========================================
# LOOP SERVIDOR
# =========================================
while ($listener.IsListening) {

    $context = $listener.GetContext()
    $request = $context.Request
    $response = $context.Response

    # CORS
    $response.AppendHeader("Access-Control-Allow-Origin", "*")
    $response.AppendHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
    $response.AppendHeader("Access-Control-Allow-Headers", "Content-Type")

    if ($request.HttpMethod -eq "OPTIONS") {
        $response.StatusCode = 200
        $response.Close()
        continue
    }

    $path = $request.Url.LocalPath
    Write-Host "Recepción: $($request.HttpMethod) $path"

    # =========================================
    # API
    # =========================================
    if ($path.StartsWith("/api/")) {

        try {

            # =============================
            # POST LOGIN (Autenticación real)
            # =============================
            if ($path -eq "/api/login" -and $request.HttpMethod -eq "POST") {
                $reader = New-Object System.IO.StreamReader($request.InputStream, [System.Text.Encoding]::UTF8)
                $body = $reader.ReadToEnd()
                $json = $body | ConvertFrom-Json

                $username = Esc $json.username
                $password = Esc $json.password

                $q = @"
SET client_encoding='UTF8';
SELECT role FROM usuario WHERE username='$username' AND password_hash = crypt('$password', password_hash);
"@
                $role = (& $psqlPath $dbParams -c $q | Out-String).Trim()
                
                if ($role) {
                    Send-JsonResponse $response "{`"status`":`"ok`",`"role`":`"$role`"}"
                } else {
                    $response.StatusCode = 401
                    Send-JsonResponse $response '{"status":"error","message":"Credenciales inválidas"}'
                }
                continue
            }

            # =============================
            # PUT HORAS MATERIA (Actualizar en presupuesto)
            # =============================
            elseif ($path -match "^/api/asignacion/(\d+)/horas$" -and $request.HttpMethod -eq "PUT") {
                $docId = $matches[1]
                $reader = New-Object System.IO.StreamReader($request.InputStream, [System.Text.Encoding]::UTF8)
                $body = $reader.ReadToEnd()
                $json = $body | ConvertFrom-Json
                
                $horas = [decimal]$json.horas
                if ($horas -lt 0) { $horas = 0 }
                $materia = Esc $json.materia

                $q = @"
SET client_encoding='UTF8';
UPDATE asignacion SET horas = $horas 
WHERE docente_id = $docId AND materia_id = (SELECT materia_id FROM materia WHERE nombre = '$materia' LIMIT 1);
"@
                & $psqlPath $dbParams -c $q
                Send-JsonResponse $response '{"status":"ok"}'
                continue
            }

            # =============================
            # GET DOCENTES (lista completa)
            # =============================
            elseif ($path -eq "/api/docentes" -and $request.HttpMethod -eq "GET") {

                $q = @"
SET client_encoding='UTF8';
SELECT COALESCE(json_agg(res), '[]'::json)
FROM (
    SELECT d.docente_id, d.nombre, d.grado_acad, d.grado_mil,
           d.condicion, d.rfc, d.curp,
           string_agg(DISTINCT c.siglas, ', ') as carrera
    FROM Docente d
    LEFT JOIN Asignacion a ON d.docente_id = a.docente_id
    LEFT JOIN Materia m ON a.materia_id = m.materia_id
    LEFT JOIN Carrera c ON m.carrera_id = c.carrera_id
    GROUP BY d.docente_id
    ORDER BY d.docente_id ASC
) res;
"@

                $dbResult = & $psqlPath $dbParams -c $q
                Send-JsonResponse $response $dbResult
            }

            # =============================
            # GET DOCENTE BY ID
            # =============================
            elseif ($path -match "^/api/docentes/(\d+)$" -and $request.HttpMethod -eq "GET") {

                $docId = $matches[1]

                $q = @"
SET client_encoding='UTF8';
SELECT row_to_json(d)
FROM (
    SELECT *
    FROM Docente
    WHERE docente_id = $docId
) d;
"@

                $dbResult = & $psqlPath $dbParams -c $q
                Send-JsonResponse $response $dbResult
            }

            # =============================
            # PUT DOCENTE (actualizar perfil)
            # =============================
            elseif ($path -match "^/api/docentes/(\d+)$" -and $request.HttpMethod -eq "PUT") {

                $docId = $matches[1]
                $reader = New-Object System.IO.StreamReader($request.InputStream, [System.Text.Encoding]::UTF8)
                $body = $reader.ReadToEnd()
                $json = $body | ConvertFrom-Json

                $nombre     = Esc $json.nombre
                $gradoAcad  = Esc $json.grado_acad
                $gradoMil   = Esc $json.grado_mil
                $matricula  = Esc $json.matricula
                $rfc        = Esc $json.rfc
                $curp       = Esc $json.curp
                $condicion  = Esc $json.condicion
                $genero     = Esc $json.genero
                $sangre     = Esc $json.tipo_sangre
                $ine        = Esc $json.credencial_ine
                $domicilio  = Esc $json.domicilio

                $q = @"
SET client_encoding='UTF8';
UPDATE Docente SET
    nombre = '$nombre',
    grado_acad = '$gradoAcad',
    grado_mil = '$gradoMil',
    matricula = '$matricula',
    rfc = '$rfc',
    curp = '$curp',
    condicion = '$condicion',
    genero = '$genero',
    tipo_sangre = '$sangre',
    credencial_ine = '$ine',
    domicilio = '$domicilio'
WHERE docente_id = $docId;
"@

                & $psqlPath $dbParams -c $q
                Send-JsonResponse $response '{"status":"ok"}'
            }

            # =============================
            # GET MATERIAS POR DOCENTE
            # =============================
            # GET MATERIAS POR DOCENTE
            # =============================
            elseif ($path -match "^/api/materias/(\d+)$" -and $request.HttpMethod -eq "GET") {

                $docId = $matches[1]

                $q = @"
SET client_encoding='UTF8';
SELECT COALESCE(json_agg(res), '[]'::json)
FROM (
    SELECT m.nombre as materia, COALESCE(c.siglas, 'N/A') as carrera, MAX(COALESCE(a.horas, 0)) as horas, MAX(d.grado_acad) as grado
    FROM Asignacion a
    JOIN Materia m ON a.materia_id = m.materia_id
    LEFT JOIN Carrera c ON m.carrera_id = c.carrera_id
    JOIN Docente d ON a.docente_id = d.docente_id
    WHERE a.docente_id = $docId
    GROUP BY m.nombre, c.siglas
    ORDER BY m.nombre
) res;
"@

                $dbResult = & $psqlPath $dbParams -c $q
                Send-JsonResponse $response $dbResult
            }

            # =============================
            # GET VEHICULOS
            # =============================
            elseif ($path -eq "/api/vehiculos" -and $request.HttpMethod -eq "GET") {

                $q = @"
SET client_encoding='UTF8';
SELECT COALESCE(json_agg(res), '[]'::json)
FROM (
    SELECT v.vehiculo_id,
           d.nombre as docente,
           (SELECT string_agg(DISTINCT c.siglas, ', ')
            FROM Asignacion a
            JOIN Materia m ON a.materia_id = m.materia_id
            JOIN Carrera c ON m.carrera_id = c.carrera_id
            WHERE a.docente_id = d.docente_id) as carrera,
           v.marca,
           v.modelo,
           v.anio,
           v.color,
           v.placas
    FROM Vehiculo v
    JOIN Docente d ON v.docente_id = d.docente_id
    ORDER BY v.vehiculo_id DESC
) res;
"@

                $dbResult = & $psqlPath $dbParams -c $q
                Send-JsonResponse $response $dbResult
            }

            # =============================
            # POST DOCENTE (registrar nuevo - COMPLETO)
            # =============================
            elseif ($path -eq "/api/docentes" -and $request.HttpMethod -eq "POST") {

                $reader = New-Object System.IO.StreamReader($request.InputStream, [System.Text.Encoding]::UTF8)
                $body = $reader.ReadToEnd()
                $json = $body | ConvertFrom-Json

                $nombre     = Esc $json.nombre
                $rfc        = Esc $json.rfc
                $curp       = Esc $json.curp
                $condicion  = Esc $json.condicion
                $gradoAcad  = Esc $json.grado_acad
                $gradoMil   = Esc $json.grado_mil
                $matricula  = Esc $json.matricula
                $genero     = Esc $json.genero
                $sangre     = Esc $json.tipo_sangre
                $ine        = Esc $json.credencial_ine
                $domicilio  = Esc $json.domicilio
                $regimen_sat = Esc $json.regimen_sat
                
                $materiaNueva = Esc $json.materia
                $carreraRol = Esc $json.carrera

                if (-not $nombre -or -not $rfc) {
                    $response.StatusCode = 400
                    Send-JsonResponse $response '{"status":"error","message":"Nombre y RFC son obligatorios"}'
                    continue
                }

                $q = @"
SET client_encoding='UTF8';
INSERT INTO Docente(nombre, rfc, curp, condicion, grado_acad, grado_mil, matricula, genero, tipo_sangre, credencial_ine, domicilio, regimen_sat)
VALUES('$nombre', '$rfc', '$curp', '$condicion', '$gradoAcad', '$gradoMil', '$matricula', '$genero', '$sangre', '$ine', '$domicilio', '$regimen_sat')
RETURNING docente_id;
"@
                $dbResult = & $psqlPath $dbParams -c $q
                $newId = ($dbResult | Out-String).Trim()

                if ($newId -and $materiaNueva) {
                    $qMat = @"
SET client_encoding='UTF8';
DO `$`$
DECLARE
    v_carrera_id INT;
    v_materia_id INT;
BEGIN
    SELECT carrera_id INTO v_carrera_id FROM carrera WHERE siglas = '$carreraRol' LIMIT 1;
    IF NOT FOUND THEN
        SELECT carrera_id INTO v_carrera_id FROM carrera LIMIT 1;
    END IF;

    SELECT materia_id INTO v_materia_id FROM materia WHERE nombre = '$materiaNueva' LIMIT 1;
    IF NOT FOUND THEN
        INSERT INTO materia(nombre, carrera_id) VALUES('$materiaNueva', v_carrera_id) RETURNING materia_id INTO v_materia_id;
    END IF;

    INSERT INTO asignacion(docente_id, materia_id, horas) VALUES($newId, v_materia_id, 16);
END `$`$;
"@
                    & $psqlPath $dbParams -c $qMat
                }

                Send-JsonResponse $response "{`"status`":`"ok`",`"docente_id`":$newId}"
            }

            # =============================
            # POST FOTO (subir fotografía)
            # =============================
            elseif ($path -eq "/api/fotos" -and $request.HttpMethod -eq "POST") {

                $fotosDir = Join-Path $wwwroot "fotos"
                $savedName = Save-UploadedFile $request $fotosDir "foto"

                if ($savedName) {
                    # Extraer docente_id del nombre del archivo
                    $idMatch = [regex]::Match($savedName, 'foto_(\d+)')
                    if ($idMatch.Success) {
                        $docId = $idMatch.Groups[1].Value
                        $q = @"
SET client_encoding='UTF8';
UPDATE Docente SET foto_path = '$savedName' WHERE docente_id = $docId;
"@
                        & $psqlPath $dbParams -c $q
                    }
                    Send-JsonResponse $response "{`"status`":`"ok`",`"filename`":`"$savedName`"}"
                } else {
                    $response.StatusCode = 400
                    Send-JsonResponse $response '{"status":"error","message":"No se pudo guardar la foto"}'
                }
            }

            # =============================
            # POST CEDULA (subir cédula profesional)
            # =============================
            elseif ($path -eq "/api/cedulas" -and $request.HttpMethod -eq "POST") {

                $cedulasDir = Join-Path $wwwroot "cedulas"
                $savedName = Save-UploadedFile $request $cedulasDir "cedula"

                if ($savedName) {
                    $idMatch = [regex]::Match($savedName, 'cedula_(\d+)')
                    if ($idMatch.Success) {
                        $docId = $idMatch.Groups[1].Value
                        $q = @"
SET client_encoding='UTF8';
UPDATE Docente SET cedula_path = '$savedName' WHERE docente_id = $docId;
"@
                        & $psqlPath $dbParams -c $q
                    }
                    Send-JsonResponse $response "{`"status`":`"ok`",`"filename`":`"$savedName`"}"
                } else {
                    $response.StatusCode = 400
                    Send-JsonResponse $response '{"status":"error","message":"No se pudo guardar la cédula"}'
                }
            }

            # =============================
            # GET EXPEDIENTE POR DOCENTE
            # =============================
            elseif ($path -match "^/api/expediente/(\d+)$" -and $request.HttpMethod -eq "GET") {

                $docId = $matches[1]

                $q = @"
SET client_encoding='UTF8';
SELECT COALESCE(json_agg(res), '[]'::json)
FROM (
    SELECT documento_id, tipo_documento, nombre_archivo, archivo_path,
           to_char(fecha_subida, 'YYYY-MM-DD HH24:MI') as fecha_subida,
           estado, observaciones
    FROM expediente_documento
    WHERE docente_id = $docId
    ORDER BY tipo_documento
) res;
"@

                $dbResult = & $psqlPath $dbParams -c $q
                Send-JsonResponse $response $dbResult
            }

            # =============================
            # POST EXPEDIENTE (subir documento)
            # =============================
            elseif ($path -eq "/api/expediente" -and $request.HttpMethod -eq "POST") {

                $expDir = Join-Path $wwwroot "expedientes"
                $savedName = Save-UploadedFile $request $expDir "exp"

                if ($savedName) {
                    # Extraer docente_id y tipo_documento del body
                    $ms2 = New-Object System.IO.MemoryStream
                    # Ya se consumió el stream, leer del nombre
                    $idMatch = [regex]::Match($savedName, 'exp_(\d+)')
                    $docId = if ($idMatch.Success) { $idMatch.Groups[1].Value } else { "0" }

                    # Necesitamos recuperar tipo_documento — lo incluimos en el query string
                    $qs = $request.QueryString
                    $tipoDoc = $qs["tipo"]
                    if (-not $tipoDoc) { $tipoDoc = "otro" }
                    $tipoDocEsc = Esc $tipoDoc
                    $savedNameEsc = Esc $savedName

                    $q = @"
SET client_encoding='UTF8';
INSERT INTO expediente_documento(docente_id, tipo_documento, nombre_archivo, archivo_path)
VALUES($docId, '$tipoDocEsc', '$savedNameEsc', '$savedNameEsc')
RETURNING documento_id;
"@

                    $dbResult = & $psqlPath $dbParams -c $q
                    $newId = ($dbResult | Out-String).Trim()
                    Send-JsonResponse $response "{`"status`":`"ok`",`"documento_id`":$newId,`"filename`":`"$savedName`"}"
                } else {
                    $response.StatusCode = 400
                    Send-JsonResponse $response '{"status":"error","message":"No se pudo guardar el documento"}'
                }
            }

            # =============================
            # DELETE EXPEDIENTE DOCUMENTO
            # =============================
            elseif ($path -match "^/api/expediente/(\d+)$" -and $request.HttpMethod -eq "DELETE") {

                $docuId = $matches[1]

                # Obtener el path del archivo antes de borrar
                $qPath = @"
SET client_encoding='UTF8';
SELECT archivo_path FROM expediente_documento WHERE documento_id = $docuId;
"@
                $filePath = (& $psqlPath $dbParams -c $qPath | Out-String).Trim()

                $q = @"
SET client_encoding='UTF8';
DELETE FROM expediente_documento WHERE documento_id = $docuId;
"@
                & $psqlPath $dbParams -c $q

                # Intentar borrar archivo físico
                if ($filePath) {
                    $fullPath = Join-Path (Join-Path $wwwroot "expedientes") $filePath
                    if (Test-Path $fullPath) { Remove-Item $fullPath -Force }
                }

                Send-JsonResponse $response '{"status":"ok"}'
            }

            # =============================
            # GET EVALUACIONES (todas o por periodo)
            # =============================
            elseif ($path -eq "/api/evaluaciones" -and $request.HttpMethod -eq "GET") {

                $qs = $request.QueryString
                $periodo = $qs["periodo"]
                $whereClause = ""
                if ($periodo) {
                    $periodoEsc = Esc $periodo
                    $whereClause = "WHERE e.periodo = '$periodoEsc'"
                }

                $q = @"
SET client_encoding='UTF8';
SELECT COALESCE(json_agg(res), '[]'::json)
FROM (
    SELECT e.evaluacion_id, e.docente_id, d.nombre as docente_nombre,
           d.condicion, e.evaluador, e.periodo,
           to_char(e.fecha_evaluacion, 'YYYY-MM-DD') as fecha_evaluacion,
           e.puntaje_desempeno, e.puntaje_pedagogia,
           e.puntaje_perfil, e.puntaje_responsabilidad,
           e.puntaje_final, e.resultado, e.observaciones,
           string_agg(DISTINCT c.siglas, ', ') as carrera
    FROM evaluacion e
    JOIN docente d ON e.docente_id = d.docente_id
    LEFT JOIN asignacion a ON d.docente_id = a.docente_id
    LEFT JOIN materia m ON a.materia_id = m.materia_id
    LEFT JOIN carrera c ON m.carrera_id = c.carrera_id
    $whereClause
    GROUP BY e.evaluacion_id, d.nombre, d.condicion
    ORDER BY e.evaluacion_id DESC
) res;
"@

                $dbResult = & $psqlPath $dbParams -c $q
                Send-JsonResponse $response $dbResult
            }

            # =============================
            # GET EVALUACIONES POR DOCENTE
            # =============================
            elseif ($path -match "^/api/evaluaciones/(\d+)$" -and $request.HttpMethod -eq "GET") {

                $docId = $matches[1]

                $q = @"
SET client_encoding='UTF8';
SELECT COALESCE(json_agg(res), '[]'::json)
FROM (
    SELECT evaluacion_id, evaluador, periodo,
           to_char(fecha_evaluacion, 'YYYY-MM-DD') as fecha_evaluacion,
           puntaje_desempeno, puntaje_pedagogia,
           puntaje_perfil, puntaje_responsabilidad,
           puntaje_final, resultado, observaciones
    FROM evaluacion
    WHERE docente_id = $docId
    ORDER BY fecha_evaluacion DESC
) res;
"@

                $dbResult = & $psqlPath $dbParams -c $q
                Send-JsonResponse $response $dbResult
            }

            # =============================
            # POST EVALUACION (crear nueva)
            # =============================
            elseif ($path -eq "/api/evaluaciones" -and $request.HttpMethod -eq "POST") {

                $reader = New-Object System.IO.StreamReader($request.InputStream, [System.Text.Encoding]::UTF8)
                $body = $reader.ReadToEnd()
                $json = $body | ConvertFrom-Json

                $docId      = [int]$json.docente_id
                $evaluador  = Esc $json.evaluador
                $periodo    = Esc $json.periodo
                $pDesemp    = [decimal]$json.puntaje_desempeno
                $pPedag     = [decimal]$json.puntaje_pedagogia
                $pPerfil    = [decimal]$json.puntaje_perfil
                $pResp      = [decimal]$json.puntaje_responsabilidad
                $pFinal     = [decimal]$json.puntaje_final
                $resultado  = Esc $json.resultado
                $obs        = Esc $json.observaciones

                $q = @"
SET client_encoding='UTF8';
INSERT INTO evaluacion(docente_id, evaluador, periodo, puntaje_desempeno, puntaje_pedagogia, puntaje_perfil, puntaje_responsabilidad, puntaje_final, resultado, observaciones)
VALUES($docId, '$evaluador', '$periodo', $pDesemp, $pPedag, $pPerfil, $pResp, $pFinal, '$resultado', '$obs')
RETURNING evaluacion_id;
"@

                $dbResult = & $psqlPath $dbParams -c $q
                $newId = ($dbResult | Out-String).Trim()

                # Actualizar estado_evaluacion del docente
                $qUpdate = @"
SET client_encoding='UTF8';
UPDATE docente SET estado_evaluacion = '$resultado' WHERE docente_id = $docId;
"@
                & $psqlPath $dbParams -c $qUpdate

                Send-JsonResponse $response "{`"status`":`"ok`",`"evaluacion_id`":$newId}"
            }

            # =============================
            # PUT EVALUACION (actualizar)
            # =============================
            elseif ($path -match "^/api/evaluaciones/(\d+)$" -and $request.HttpMethod -eq "PUT") {

                $evalId = $matches[1]
                $reader = New-Object System.IO.StreamReader($request.InputStream, [System.Text.Encoding]::UTF8)
                $body = $reader.ReadToEnd()
                $json = $body | ConvertFrom-Json

                $pDesemp    = [decimal]$json.puntaje_desempeno
                $pPedag     = [decimal]$json.puntaje_pedagogia
                $pPerfil    = [decimal]$json.puntaje_perfil
                $pResp      = [decimal]$json.puntaje_responsabilidad
                $pFinal     = [decimal]$json.puntaje_final
                $resultado  = Esc $json.resultado
                $obs        = Esc $json.observaciones

                $q = @"
SET client_encoding='UTF8';
UPDATE evaluacion SET
    puntaje_desempeno = $pDesemp,
    puntaje_pedagogia = $pPedag,
    puntaje_perfil = $pPerfil,
    puntaje_responsabilidad = $pResp,
    puntaje_final = $pFinal,
    resultado = '$resultado',
    observaciones = '$obs'
WHERE evaluacion_id = $evalId;
"@

                & $psqlPath $dbParams -c $q

                # Actualizar estado del docente
                $qDocId = @"
SET client_encoding='UTF8';
SELECT docente_id FROM evaluacion WHERE evaluacion_id = $evalId;
"@
                $docId = (& $psqlPath $dbParams -c $qDocId | Out-String).Trim()
                if ($docId) {
                    $qUpdate = @"
SET client_encoding='UTF8';
UPDATE docente SET estado_evaluacion = '$resultado' WHERE docente_id = $docId;
"@
                    & $psqlPath $dbParams -c $qUpdate
                }

                Send-JsonResponse $response '{"status":"ok"}'
            }

            # =============================
            # GET AUDITORIA DE CONTRATOS
            # =============================
            elseif ($path -eq "/api/contratos" -and $request.HttpMethod -eq "GET") {
                $q = @"
SET client_encoding='UTF8';
SELECT COALESCE(json_agg(res), '[]'::json)
FROM (
    SELECT d.docente_id, d.nombre, d.condicion, d.rfc, d.curp, d.regimen_sat,
           c.emitido_por,
           to_char(c.fecha_emision, 'YYYY-MM-DD HH24:MI:SS') as fecha_emision,
           string_agg(DISTINCT car.siglas, ', ') as carrera
    FROM docente d
    LEFT JOIN (
        SELECT DISTINCT ON (docente_id) docente_id, emitido_por, fecha_emision
        FROM contrato_emitido
        ORDER BY docente_id, fecha_emision DESC
    ) c ON d.docente_id = c.docente_id
    LEFT JOIN asignacion a ON d.docente_id = a.docente_id
    LEFT JOIN materia m ON a.materia_id = m.materia_id
    LEFT JOIN carrera car ON m.carrera_id = car.carrera_id
    GROUP BY d.docente_id, c.emitido_por, c.fecha_emision
    ORDER BY d.nombre ASC
) res;
"@
                $dbResult = & $psqlPath $dbParams -c $q
                Send-JsonResponse $response $dbResult
            }

            # =============================
            # POST GENERAR CONTRATO
            # =============================
            elseif ($path -eq "/api/generarContrato" -and $request.HttpMethod -eq "POST") {
                $reader = New-Object System.IO.StreamReader($request.InputStream, [System.Text.Encoding]::UTF8)
                $body = $reader.ReadToEnd()
                $json = $body | ConvertFrom-Json

                $docId = [int]$json.docente_id
                $emitidoPor = Esc $json.emitido_por

                # 1. Registrar Auditoría
                $qInsert = @"
SET client_encoding='UTF8';
INSERT INTO contrato_emitido (docente_id, emitido_por) VALUES ($docId, '$emitidoPor');
"@
                & $psqlPath $dbParams -c $qInsert

                # 2. Plantilla DOCX (envío binario)
                $sourceTemplate = "C:\temp\gestion-docente-web\MAR-AGO26 (2).docx"
                if (Test-Path $sourceTemplate) {
                    $bytes = [System.IO.File]::ReadAllBytes($sourceTemplate)
                    $response.ContentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    $response.ContentLength64 = $bytes.Length
                    $response.OutputStream.Write($bytes, 0, $bytes.Length)
                    $response.OutputStream.Close()
                } else {
                    $response.StatusCode = 404
                    Send-JsonResponse $response '{"status":"error","message":"Plantilla Docx no encontrada"}'
                }
            }

            # =============================
            # GET DOCENTES PARA SELECT (lista simple para combo evaluaciones)
            # =============================
            elseif ($path -eq "/api/docentes-select" -and $request.HttpMethod -eq "GET") {

                $q = @"
SET client_encoding='UTF8';
SELECT COALESCE(json_agg(res), '[]'::json)
FROM (
    SELECT docente_id, nombre, condicion
    FROM Docente
    ORDER BY nombre ASC
) res;
"@

                $dbResult = & $psqlPath $dbParams -c $q
                Send-JsonResponse $response $dbResult
            }

            else {
                $response.StatusCode = 404
                Send-JsonResponse $response '{"status":"error","message":"Endpoint no encontrado"}'
            }

        } catch {
            Write-Host "ERROR API: $_"
            $response.StatusCode = 500
            try { $response.Close() } catch {}
        }
    }

    # =========================================
    # ARCHIVOS ESTÁTICOS
    # =========================================
    else {

        if ($path -eq "/") { $path = "/index.html" }

        $filePath = Join-Path $wwwroot $path.Replace('/', '\')

        if (Test-Path $filePath) {

            $ext = [System.IO.Path]::GetExtension($filePath).ToLower()

            switch ($ext) {
                ".html" { $mime = "text/html; charset=utf-8" }
                ".css"  { $mime = "text/css; charset=utf-8" }
                ".js"   { $mime = "application/javascript; charset=utf-8" }
                ".png"  { $mime = "image/png" }
                ".jpg"  { $mime = "image/jpeg" }
                ".jpeg" { $mime = "image/jpeg" }
                ".gif"  { $mime = "image/gif" }
                ".webp" { $mime = "image/webp" }
                ".svg"  { $mime = "image/svg+xml" }
                ".pdf"  { $mime = "application/pdf" }
                ".ico"  { $mime = "image/x-icon" }
                default { $mime = "application/octet-stream" }
            }

            $buffer = [System.IO.File]::ReadAllBytes($filePath)

            $response.ContentType = $mime
            $response.ContentLength64 = $buffer.Length
            $response.OutputStream.Write($buffer, 0, $buffer.Length)
            $response.Close()

        } else {
            $response.StatusCode = 404
            $response.Close()
        }
    }
}