package com.emi.gestiondocente;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class DocenteController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initExtraColumns() {
        String[] cols = {
            "ALTER TABLE docente ADD COLUMN IF NOT EXISTS natural_de VARCHAR(100) DEFAULT 'Ciudad de México'",
            "ALTER TABLE docente ADD COLUMN IF NOT EXISTS estado_natural VARCHAR(100)",
            "ALTER TABLE docente ADD COLUMN IF NOT EXISTS estado_civil VARCHAR(30)",
            "ALTER TABLE docente ADD COLUMN IF NOT EXISTS estudios_en VARCHAR(255)",
            "ALTER TABLE docente ADD COLUMN IF NOT EXISTS fecha_contratacion VARCHAR(60)",
            """
            CREATE TABLE IF NOT EXISTS audit_log (
                log_id SERIAL PRIMARY KEY,
                usuario VARCHAR(100),
                accion VARCHAR(30),
                entidad VARCHAR(50),
                entidad_id INT,
                detalle TEXT,
                fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """
        };
        for (String sql : cols) {
            try { jdbcTemplate.execute(sql); } catch (Exception ignored) {}
        }
    }

    private void audit(String usuario, String accion, String entidad, int entidadId, String detalle) {
        try {
            jdbcTemplate.update(
                "INSERT INTO audit_log(usuario, accion, entidad, entidad_id, detalle) VALUES(?,?,?,?,?)",
                usuario, accion, entidad, entidadId, detalle
            );
        } catch (Exception ignored) {}
    }

    // =============================================
    // GET /api/audit-log
    // =============================================
    @GetMapping("/audit-log")
    public ResponseEntity<List<Map<String, Object>>> getAuditLog() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT log_id, usuario, accion, entidad, entidad_id, detalle, to_char(fecha,'DD/MM/YYYY HH24:MI:SS') as fecha FROM audit_log ORDER BY log_id DESC LIMIT 200"
        );
        return ResponseEntity.ok(rows);
    }

    // =============================================
    // GET /api/docentes — Lista completa
    // =============================================
    @GetMapping("/docentes")
    public ResponseEntity<List<Map<String, Object>>> getDocentes() {
        String sql = """
            SELECT d.docente_id, d.nombre, d.grado_acad, d.grado_mil,
                   d.condicion, d.rfc, d.curp,
                   string_agg(DISTINCT c.siglas, ', ') as carrera,
                   string_agg(DISTINCT m.nombre || ' (' || c.siglas || ')', ', ') as materias_nombres
            FROM Docente d
            LEFT JOIN Asignacion a ON d.docente_id = a.docente_id
            LEFT JOIN Materia m ON a.materia_id = m.materia_id
            LEFT JOIN Carrera c ON m.carrera_id = c.carrera_id
            GROUP BY d.docente_id
            ORDER BY d.docente_id ASC
            """;
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
        return ResponseEntity.ok(result);
    }

    // =============================================
    // GET /api/docentes/{id} — Por ID
    // =============================================
    @GetMapping("/docentes/{id}")
    public ResponseEntity<Map<String, Object>> getDocenteById(@PathVariable int id) {
        try {
            Map<String, Object> docente = jdbcTemplate.queryForMap(
                "SELECT * FROM Docente WHERE docente_id = ?", id
            );
            return ResponseEntity.ok(docente);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // =============================================
    // GET /api/docentes-select — Para combo/select
    // Acepta ?carrera=ICI para filtrar por carrera
    // =============================================
    @GetMapping("/docentes-select")
    public ResponseEntity<List<Map<String, Object>>> getDocentesSelect(
            @RequestParam(required = false) String carrera) {
        List<Map<String, Object>> result;
        if (carrera != null && !carrera.isBlank()) {
            String sql = """
                SELECT DISTINCT d.docente_id, d.nombre, d.condicion
                FROM Docente d
                JOIN Asignacion a ON d.docente_id = a.docente_id
                JOIN Materia m ON a.materia_id = m.materia_id
                JOIN Carrera c ON m.carrera_id = c.carrera_id
                WHERE c.siglas = ?
                ORDER BY d.nombre ASC
                """;
            result = jdbcTemplate.queryForList(sql, carrera);
        } else {
            String sql = """
                SELECT docente_id, nombre, condicion
                FROM Docente
                ORDER BY nombre ASC
                """;
            result = jdbcTemplate.queryForList(sql);
        }
        return ResponseEntity.ok(result);
    }

    // =============================================
    // GET /api/docentes/stats?carrera=ICI — Para JSA
    // =============================================
    @GetMapping("/docentes/stats")
    public ResponseEntity<Map<String, Object>> getDocentesStats(
            @RequestParam(required = false) String carrera) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (carrera != null && !carrera.isBlank()) {
                Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT
                        COUNT(*) AS total,
                        SUM(CASE WHEN d.condicion = 'Personal Civil' THEN 1 ELSE 0 END) AS civiles,
                        SUM(CASE WHEN d.condicion = 'Personal Militar' THEN 1 ELSE 0 END) AS militares
                    FROM (
                        SELECT DISTINCT d.docente_id, d.condicion
                        FROM Docente d
                        JOIN Asignacion a ON d.docente_id = a.docente_id
                        JOIN Materia m ON a.materia_id = m.materia_id
                        JOIN Carrera c ON m.carrera_id = c.carrera_id
                        WHERE c.siglas = ?
                    ) d
                    """, carrera);
                result.put("carrera", carrera);
                result.put("total", row.get("total"));
                result.put("civiles", row.get("civiles"));
                result.put("militares", row.get("militares"));
            } else {
                Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT COUNT(*) AS total,
                        SUM(CASE WHEN condicion = 'Personal Civil' THEN 1 ELSE 0 END) AS civiles,
                        SUM(CASE WHEN condicion = 'Personal Militar' THEN 1 ELSE 0 END) AS militares
                    FROM Docente
                    """);
                result.put("carrera", "TODAS");
                result.put("total", row.get("total"));
                result.put("civiles", row.get("civiles"));
                result.put("militares", row.get("militares"));
            }
        } catch (Exception e) {
            result.put("total", 0); result.put("civiles", 0); result.put("militares", 0);
        }
        return ResponseEntity.ok(result);
    }

    // =============================================
    // GET /api/resumen-director — Para DIR
    // =============================================
    @GetMapping("/resumen-director")
    public ResponseEntity<List<Map<String, Object>>> getResumenDirector() {
        String sql = """
            SELECT c.siglas AS carrera, c.nombre AS nombre_carrera,
                COUNT(DISTINCT d.docente_id) AS total_docentes,
                COUNT(DISTINCT CASE WHEN d.condicion = 'Personal Civil' THEN d.docente_id END) AS civiles,
                COUNT(DISTINCT CASE WHEN d.condicion = 'Personal Militar' THEN d.docente_id END) AS militares,
                COALESCE(SUM(a.horas), 0) AS total_horas
            FROM Carrera c
            LEFT JOIN Materia m ON m.carrera_id = c.carrera_id
            LEFT JOIN Asignacion a ON a.materia_id = m.materia_id
            LEFT JOIN Docente d ON d.docente_id = a.docente_id
            GROUP BY c.carrera_id, c.siglas, c.nombre
            ORDER BY c.siglas ASC
            """;
        return ResponseEntity.ok(jdbcTemplate.queryForList(sql));
    }

    // =============================================
    // POST /api/docentes — Registrar nuevo docente
    // =============================================
    @PostMapping("/docentes")
    public ResponseEntity<Map<String, Object>> createDocente(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value="X-Usuario", defaultValue="sistema") String usuario) {
        String nombre     = str(payload.get("nombre"));
        String rfc        = str(payload.get("rfc"));
        String curp       = str(payload.get("curp"));
        String condicion  = str(payload.get("condicion"));
        String gradoAcad  = str(payload.get("grado_acad"));
        String gradoMil   = str(payload.get("grado_mil"));
        String matricula  = str(payload.get("matricula"));
        String genero     = str(payload.get("genero"));
        String sangre     = str(payload.get("tipo_sangre"));
        String ine        = str(payload.get("credencial_ine"));
        String domicilio  = str(payload.get("domicilio"));
        String regimenSat = str(payload.get("regimen_sat"));
        String materia    = str(payload.get("materia"));
        String carrera    = str(payload.get("carrera"));

        if (nombre == null || nombre.isEmpty() || rfc == null || rfc.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Nombre y RFC son obligatorios");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            Integer newId = jdbcTemplate.queryForObject(
                """
                INSERT INTO Docente(nombre, rfc, curp, condicion, grado_acad, grado_mil,
                    matricula, genero, tipo_sangre, credencial_ine, domicilio, regimen_sat)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING docente_id
                """,
                Integer.class,
                nombre, rfc, curp, condicion, gradoAcad, gradoMil,
                matricula, genero, sangre, ine, domicilio, regimenSat
            );

            if (newId != null && materia != null && !materia.isEmpty()) {
                final int docenteId = newId;

                // Buscar carrera por siglas (con parámetro)
                List<Map<String,Object>> carreraRows = carrera != null && !carrera.isEmpty()
                    ? jdbcTemplate.queryForList("SELECT carrera_id FROM carrera WHERE siglas = ? LIMIT 1", carrera)
                    : List.of();
                Integer carreraId;
                if (!carreraRows.isEmpty()) {
                    carreraId = ((Number) carreraRows.get(0).get("carrera_id")).intValue();
                } else {
                    carreraId = jdbcTemplate.queryForObject("SELECT carrera_id FROM carrera LIMIT 1", Integer.class);
                }

                // Buscar o crear materia (con parámetros)
                List<Map<String,Object>> materiaRows = jdbcTemplate.queryForList(
                    "SELECT materia_id FROM materia WHERE nombre = ? LIMIT 1", materia);
                Integer materiaId;
                if (!materiaRows.isEmpty()) {
                    materiaId = ((Number) materiaRows.get(0).get("materia_id")).intValue();
                } else {
                    materiaId = jdbcTemplate.queryForObject(
                        "INSERT INTO materia(nombre, carrera_id) VALUES(?, ?) RETURNING materia_id",
                        Integer.class, materia, carreraId);
                }

                jdbcTemplate.update(
                    "INSERT INTO asignacion(docente_id, materia_id, horas) VALUES(?, ?, 16)",
                    docenteId, materiaId);
            }

            audit(usuario, "AGREGAR", "DOCENTE", newId != null ? newId : 0, "Nombre: " + nombre);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "ok");
            result.put("docente_id", newId);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Error al registrar docente: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    // =============================================
    // PUT /api/docentes/{id} — Actualizar perfil
    // =============================================
    @PutMapping("/docentes/{id}")
    public ResponseEntity<Map<String, Object>> updateDocente(
            @PathVariable int id,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value="X-Usuario", defaultValue="sistema") String usuario) {
        try {
            jdbcTemplate.update(
                """
                UPDATE Docente SET
                    nombre = ?,
                    grado_acad = ?,
                    grado_mil = ?,
                    matricula = ?,
                    rfc = ?,
                    curp = ?,
                    condicion = ?,
                    genero = ?,
                    tipo_sangre = ?,
                    credencial_ine = ?,
                    domicilio = ?,
                    natural_de = ?,
                    estado_natural = ?,
                    estado_civil = ?,
                    estudios_en = ?,
                    fecha_contratacion = ?
                WHERE docente_id = ?
                """,
                str(payload.get("nombre")),
                str(payload.get("grado_acad")),
                str(payload.get("grado_mil")),
                str(payload.get("matricula")),
                str(payload.get("rfc")),
                str(payload.get("curp")),
                str(payload.get("condicion")),
                str(payload.get("genero")),
                str(payload.get("tipo_sangre")),
                str(payload.get("credencial_ine")),
                str(payload.get("domicilio")),
                str(payload.get("natural_de")),
                str(payload.get("estado_natural")),
                str(payload.get("estado_civil")),
                str(payload.get("estudios_en")),
                str(payload.get("fecha_contratacion")),
                id
            );
            audit(usuario, "MODIFICAR", "DOCENTE", id, "Nombre: " + str(payload.get("nombre")));

            Map<String, Object> result = new HashMap<>();
            result.put("status", "ok");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    // =============================================
    // DELETE /api/docentes/{id} — Eliminar docente
    // =============================================
    @DeleteMapping("/docentes/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocente(
            @PathVariable int id,
            @RequestHeader(value="X-Usuario", defaultValue="sistema") String usuario) {
        try {
            // Guardar nombre antes de eliminar
            String nombreDocente = "";
            try {
                Map<String,Object> d = jdbcTemplate.queryForMap("SELECT nombre FROM docente WHERE docente_id = ?", id);
                nombreDocente = d.get("nombre").toString();
            } catch (Exception ignored) {}

            jdbcTemplate.update("DELETE FROM expediente_documento WHERE docente_id = ?", id);
            jdbcTemplate.update("DELETE FROM Evaluacion WHERE docente_id = ?", id);
            jdbcTemplate.update("DELETE FROM Vehiculo WHERE docente_id = ?", id);
            jdbcTemplate.update("DELETE FROM contrato_emitido WHERE docente_id = ?", id);
            jdbcTemplate.update("DELETE FROM Asignacion WHERE docente_id = ?", id);
            int rows = jdbcTemplate.update("DELETE FROM Docente WHERE docente_id = ?", id);
            Map<String, Object> result = new HashMap<>();
            if (rows > 0) {
                audit(usuario, "ELIMINAR", "DOCENTE", id, "Nombre: " + nombreDocente);
                result.put("status", "ok");
                return ResponseEntity.ok(result);
            } else {
                result.put("status", "error");
                result.put("message", "Docente no encontrado");
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Error al eliminar: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    // Helper
    private String str(Object o) {
        return o != null ? o.toString() : "";
    }
}
