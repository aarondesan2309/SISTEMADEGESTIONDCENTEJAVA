# GUÍA DE ARRANQUE — Sistema de Gestión Docente (S.E.M.)

## Requisitos Previos

Antes de iniciar la aplicación, asegúrate de tener instalado:

1. **Java JDK 17** o superior
   - Verifica con: `java -version`
2. **PostgreSQL 16** (o compatible)
   - La base de datos `gestion_docente_emi` debe existir
   - Usuario: `postgres` / Contraseña: `1234`
3. **Apache Maven 3.9.x** (ya incluido en `C:\temp\gestion-docente-web\apache-maven-3.9.6`)

---

## Pasos para Encender la Aplicación

### Paso 1: Abrir PowerShell

Abre una ventana de **PowerShell** (o Terminal de Windows).

### Paso 2: Navegar a la carpeta del proyecto

```powershell
cd C:\temp\gestion-docente-web\backend
```

### Paso 3: Iniciar el servidor

```powershell
..\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
```

### Paso 4: Esperar a que el servidor arranque

Verás varias líneas de texto. Espera hasta que aparezca el mensaje:

```
Started GestionDocenteApplication in X.X seconds
Migracion de DB exitosa: nivel_pago, folio y pgcrypto admin reset
```

### Paso 5: Abrir el navegador

Abre tu navegador (Chrome, Edge, etc.) y ve a:

```
http://localhost:8091/
```

---

## Cómo Apagar la Aplicación

En la misma ventana de PowerShell donde está corriendo, presiona:

```
Ctrl + C
```

Esto detendrá el servidor de forma segura.

---

## Solución de Problemas Comunes

| Problema | Solución |
|---|---|
| "Port 8091 already in use" | Ejecuta `Stop-Process -Name java -Force` y vuelve a intentar |
| "Failed to configure DataSource" | Verifica que PostgreSQL esté corriendo (`services.msc` → postgresql-x64-16) |
| La página no carga | Asegúrate de que el mensaje "Started" apareció en la consola |
| Error al compilar | Ejecuta `mvn clean` primero: `..\apache-maven-3.9.6\bin\mvn.cmd clean spring-boot:run` |

---

## Credenciales del Sistema

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `admin` | Administrador General |
| `dir` | (configurar) | Director |
| `ici` | (configurar) | Jefe Carrera ICI |
| `ice` | (configurar) | Jefe Carrera ICE |

---

## Archivo de Plantilla de Contratos

El sistema usa la plantilla de Word ubicada en:

```
C:\temp\gestion-docente-web\MAR-AGO26 (2).docx
```

> **IMPORTANTE:** Si Word muestra un mensaje de "SELECT * FROM CONTRATO$" al abrir contratos generados, abre el archivo original, ve a la pestaña **Correspondencia** → **Iniciar combinar correspondencia** → selecciona **Documento normal de Word** y guarda. Esto limpia la configuración vieja de Excel.
