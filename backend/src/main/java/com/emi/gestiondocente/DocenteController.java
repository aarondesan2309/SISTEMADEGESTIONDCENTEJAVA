package com.emi.gestiondocente;

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

    // =============================================
    // GET /api/docentes — Lista completa
    // =============================================
    @GetMapping("/docentes")
    public ResponseEntity<List<Map<String, Object>>> getDocentes() {
        String sql = """
            SELECT d.docente_id, d.nombre, d.grado_acad, d.grado_mil,
                   d.condicion, d.rfc, d.curp,
                   string_agg(DISTINCT c.siglas, ', ') as carrera
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
    // =============================================
    @GetMapping("/docentes-select")
    public ResponseEntity<List<Map<String, Object>>> getDocentesSelect() {
        String sql = """
            SELECT docente_id, nombre, condicion
            FROM Docente
            ORDER BY nombre ASC
            """;
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
        return ResponseEntity.ok(result);
    }

    // =============================================
    // POST /api/docentes — Registrar nuevo docente
    // =============================================
    @PostMapping("/docentes")
    public ResponseEntity<Map<String, Object>> createDocente(@RequestBody Map<String, Object> payload) {
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
                final String carreraFinal = (carrera != null && !carrera.isEmpty()) ? carrera : "";
                final String materiaFinal = materia;
                final int docenteId = newId;

                jdbcTemplate.execute(
                    String.format("""
                        DO $$
                        DECLARE
                            v_carrera_id INT;
                            v_materia_id INT;
                        BEGIN
                            SELECT carrera_id INTO v_carrera_id FROM carrera WHERE siglas = '%s' LIMIT 1;
                            IF NOT FOUND THEN
                                SELECT carrera_id INTO v_carrera_id FROM carrera LIMIT 1;
                            END IF;
                            SELECT materia_id INTO v_materia_id FROM materia WHERE nombre = '%s' LIMIT 1;
                            IF NOT FOUND THEN
                                INSERT INTO materia(nombre, carrera_id) VALUES('%s', v_carrera_id) RETURNING materia_id INTO v_materia_id;
                            END IF;
                            INSERT INTO asignacion(docente_id, materia_id, horas) VALUES(%d, v_materia_id, 16);
                        END $$;
                        """,
                        carreraFinal.replace("'", "''"),
                        materiaFinal.replace("'", "''"),
                        materiaFinal.replace("'", "''"),
                        docenteId
                    )
                );
            }

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
            @RequestBody Map<String, Object> payload) {
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
                    domicilio = ?
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
                id
            );
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

    // Helper
    private String str(Object o) {
        return o != null ? o.toString() : "";
    }
}
