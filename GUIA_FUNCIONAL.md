# GUÍA FUNCIONAL — Sistema de Gestión Docente (S.E.M.)

## ¿Qué es el S.E.M.?

El **Sistema Educativo Militar (S.E.M.)** es una aplicación web que automatiza el **Procedimiento Sistemático de Operar (P.S.O.)** de contratación docente de la UDEFA. Permite gestionar docentes, evaluar su desempeño, generar contratos en Word y calcular presupuestos globales.

---

## Pantalla de Inicio: Hub del SEM

Al abrir la aplicación verás las tarjetas de los planteles militares. Solo **EMI** está habilitado actualmente. Haz clic en la tarjeta de la EMI para pasar a la pantalla de login.

---

## Módulos del Sistema

Al ingresar, verás 5 módulos principales en el menú:

### 1. 🏫 GESTIÓN DE DOCENTES

Aquí podrás:

- **Ver el directorio completo** de docentes con sus datos (RFC, CURP, carrera, condición militar/civil).
- **Buscar docentes** en tiempo real escribiendo en la barra de búsqueda.
- **Agregar nuevos docentes** con el botón "+ AGREGAR DOCENTE" (solo disponible para Jefes de Carrera y Administradores).
- **Ver/Editar perfil** de cada docente haciendo clic en su nombre. El perfil tiene 3 pestañas:
  - **Datos Personales:** Nombre, RFC, CURP, grado académico, domicilio, fotografía, cédula profesional.
  - **Expediente PSO:** Checklist de los 16 documentos requeridos por la SEDENA para la contratación.
  - **Evaluaciones:** Historial de evaluaciones académicas del docente.

### 2. 📝 EVALUACIONES ACADÉMICAS

Permite evaluar el desempeño docente con los 4 criterios de la SEDENA:

| Criterio | Peso |
|---|---|
| Desempeño Académico | 30% |
| Calidad Pedagógica | 30% |
| Cumplimiento de Perfil | 20% |
| Responsabilidad Institucional | 20% |

Cada evaluación calcula automáticamente el puntaje final ponderado y asigna un resultado (Excelente, Bueno, Regular, etc.).

### 3. 📋 FORMATOS Y CONTRATOS

Este es el módulo central del P.S.O. Aquí puedes:

1. **Ver la lista de todos los docentes** y el estado de su contrato.
2. **Hacer clic en "Generar Contrato"** para abrir la **Vista Previa Financiera**.
3. En la vista previa puedes:
   - **Agregar materias** libremente usando el formulario verde.
   - **Cambiar el nivel de pago** (Técnico, Licenciatura, Maestría) por cada materia.
   - **Modificar las horas** asignadas.
   - **Ver el indicador de horas** que te muestra cuántas horas de las 80 máximas permitidas se han utilizado.
4. **Confirmar y Descargar** genera un archivo `.docx` listo para imprimir que incluye:
   - Los datos personales del docente.
   - Las materias asignadas.
   - El desglose financiero mensual (4 meses).
   - Los cálculos de IVA, retención de ISR y retención de IVA según el régimen fiscal (SP, RESICO, RS).
   - El folio oficial del contrato.

#### Tabulador de Honorarios (UDEFA)

| Nivel | Tarifa por Hora |
|---|---|
| Técnico | $231.19 |
| Licenciatura | $486.71 |
| Maestría | $851.75 |

#### Restricción de Horas

- **Máximo 80 horas/mes por docente.** El sistema no permitirá generar el contrato si se excede este límite.
- Una barra de color indica el estado:
  - 🟢 **Verde:** Dentro del rango seguro.
  - 🟡 **Amarillo:** Más de 60 hrs (cerca del límite).
  - 🔴 **Rojo:** Más de 80 hrs (bloqueado).

### 4. 💰 PRESUPUESTO P.S.O.

Genera el **reporte concentrado global** que se envía a la UDEFA para solicitar el presupuesto. Muestra:

- Cada docente con su régimen fiscal.
- Materia, horas, tarifa por hora.
- Desglose de: Subtotal, IVA, Retención ISR, Retención IVA.
- **Total Neto** por docente.
- **Gran Total del Plantel** al final de la tabla.

Puedes **imprimir** la tabla directamente desde el botón "IMPRIMIR / EXPORTAR".

### 5. 🚗 CONTROL VEHICULAR

Módulo para registrar los vehículos autorizados de los docentes para acceso al plantel.

---

## Régimen Fiscal y Cálculos de Impuestos

El sistema maneja 3 tipos de régimen fiscal:

| Régimen | IVA (16%) | Ret. IVA (10.67%) | Ret. ISR |
|---|---|---|---|
| **SP** (Servicios Profesionales) | ✅ Sí | ✅ Sí | 10% |
| **RESICO** (Simplificado de Confianza) | ✅ Sí | ✅ Sí | 1.25% |
| **RS** (Sueldos y Salarios / Asimilados) | ❌ No | ❌ No | 10% |

---

## Roles de Usuario

| Rol | Permisos |
|---|---|
| **ADM** (Administrador) | Acceso total a todos los módulos |
| **DIR** (Director) | Acceso total de lectura y contratos |
| **ICI, ICE, II, IC, TC** (Jefes de Carrera) | Pueden agregar docentes y generar contratos de su carrera |

---

## Flujo de Trabajo Típico (P.S.O.)

```
1. Registrar al docente ──→ 2. Subir expediente ──→ 3. Asignar materias y horas
       │                                                        │
       │                                                        ▼
       │                                              4. Verificar 80 hrs máx
       │                                                        │
       ▼                                                        ▼
5. Evaluar docente ◄────────────────────────── 6. Generar contrato (.docx)
                                                        │
                                                        ▼
                                              7. Presupuesto global (UDEFA)
```

---

## Archivos Importantes del Sistema

| Archivo | Ubicación | Propósito |
|---|---|---|
| Plantilla de contrato | `C:\temp\gestion-docente-web\MAR-AGO26 (2).docx` | Machote para los contratos |
| Código del servidor | `C:\temp\gestion-docente-web\backend\` | Código fuente Java |
| Interfaz web | `backend\src\main\resources\static\` | HTML, CSS, JavaScript |
| Configuración BD | `backend\src\main\resources\application.properties` | Conexión a PostgreSQL |
