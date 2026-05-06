package com.emi.gestiondocente;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class EvaluacionController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initRubricColumns() {
        String[] cols = {
            "ALTER TABLE evaluacion ADD COLUMN IF NOT EXISTS rubrica_habilidades VARCHAR(20)",
            "ALTER TABLE evaluacion ADD COLUMN IF NOT EXISTS rubrica_dominio VARCHAR(20)",
            "ALTER TABLE evaluacion ADD COLUMN IF NOT EXISTS rubrica_tics VARCHAR(20)",
            "ALTER TABLE evaluacion ADD COLUMN IF NOT EXISTS rubrica_vinculacion VARCHAR(20)",
            "ALTER TABLE evaluacion ADD COLUMN IF NOT EXISTS concepto_personal TEXT",
            "ALTER TABLE evaluacion ADD COLUMN IF NOT EXISTS situacion VARCHAR(30) DEFAULT 'CONTRATADO'"
        };
        for (String sql : cols) {
            try { jdbcTemplate.execute(sql); } catch (Exception ignored) {}
        }
    }

    // =============================================
    // GET /api/evaluaciones?periodo=...
    // =============================================
    @GetMapping("/evaluaciones")
    public ResponseEntity<List<Map<String, Object>>> getEvaluaciones(
            @RequestParam(required = false) String periodo) {

        String whereClause = periodo != null && !periodo.isEmpty()
            ? "WHERE e.periodo = '" + periodo.replace("'", "''") + "'"
            : "";

        String sql = String.format("""
            SELECT e.evaluacion_id, e.docente_id, d.nombre as docente_nombre,
                   d.condicion, d.rfc, d.curp, d.grado_acad, d.grado_mil, d.foto_path,
                   e.evaluador, e.periodo,
                   to_char(e.fecha_evaluacion, 'YYYY-MM-DD') as fecha_evaluacion,
                   e.puntaje_desempeno, e.puntaje_pedagogia,
                   e.puntaje_perfil, e.puntaje_responsabilidad,
                   e.puntaje_final, e.resultado, e.observaciones,
                   COALESCE(e.rubrica_habilidades,'') as rubrica_habilidades,
                   COALESCE(e.rubrica_dominio,'') as rubrica_dominio,
                   COALESCE(e.rubrica_tics,'') as rubrica_tics,
                   COALESCE(e.rubrica_vinculacion,'') as rubrica_vinculacion,
                   COALESCE(e.concepto_personal,'') as concepto_personal,
                   COALESCE(e.situacion,'CONTRATADO') as situacion,
                   string_agg(DISTINCT c.siglas, ', ') as carrera,
                   string_agg(DISTINCT m.nombre, ', ') as materias_nombres
            FROM evaluacion e
            JOIN docente d ON e.docente_id = d.docente_id
            LEFT JOIN asignacion a ON d.docente_id = a.docente_id
            LEFT JOIN materia m ON a.materia_id = m.materia_id
            LEFT JOIN carrera c ON m.carrera_id = c.carrera_id
            %s
            GROUP BY e.evaluacion_id, d.nombre, d.condicion, d.rfc, d.curp,
                     d.grado_acad, d.grado_mil, d.foto_path
            ORDER BY e.evaluacion_id DESC
            """, whereClause);

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
        return ResponseEntity.ok(result);
    }

    // =============================================
    // GET /api/evaluaciones/{id} — Por docente
    // =============================================
    @GetMapping("/evaluaciones/{id}")
    public ResponseEntity<List<Map<String, Object>>> getEvaluacionesByDocente(@PathVariable int id) {
        String sql = """
            SELECT evaluacion_id, evaluador, periodo,
                   to_char(fecha_evaluacion, 'YYYY-MM-DD') as fecha_evaluacion,
                   puntaje_desempeno, puntaje_pedagogia,
                   puntaje_perfil, puntaje_responsabilidad,
                   puntaje_final, resultado, observaciones
            FROM evaluacion
            WHERE docente_id = ?
            ORDER BY fecha_evaluacion DESC
            """;
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, id);
        return ResponseEntity.ok(result);
    }

    // =============================================
    // POST /api/evaluaciones — Crear nueva
    // =============================================
    @PostMapping("/evaluaciones")
    public ResponseEntity<Map<String, Object>> createEvaluacion(@RequestBody Map<String, Object> payload) {
        try {
            int docId         = Integer.parseInt(payload.get("docente_id").toString());
            String evaluador  = str(payload.get("evaluador"));
            String periodo    = str(payload.get("periodo"));
            BigDecimal pDesemp = dec(payload.get("puntaje_desempeno"));
            BigDecimal pPedag  = dec(payload.get("puntaje_pedagogia"));
            BigDecimal pPerfil = dec(payload.get("puntaje_perfil"));
            BigDecimal pResp   = dec(payload.get("puntaje_responsabilidad"));
            BigDecimal pFinal  = dec(payload.get("puntaje_final"));
            String resultado   = str(payload.get("resultado"));
            String obs         = str(payload.get("observaciones"));
            String rubH        = str(payload.get("rubrica_habilidades"));
            String rubD        = str(payload.get("rubrica_dominio"));
            String rubT        = str(payload.get("rubrica_tics"));
            String rubV        = str(payload.get("rubrica_vinculacion"));
            String concPers    = str(payload.get("concepto_personal"));
            String situacion   = str(payload.get("situacion"));
            if (situacion.isEmpty()) situacion = "CONTRATADO";

            Integer newId = jdbcTemplate.queryForObject(
                """
                INSERT INTO evaluacion(docente_id, evaluador, periodo,
                    puntaje_desempeno, puntaje_pedagogia, puntaje_perfil,
                    puntaje_responsabilidad, puntaje_final, resultado, observaciones,
                    rubrica_habilidades, rubrica_dominio, rubrica_tics, rubrica_vinculacion,
                    concepto_personal, situacion)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING evaluacion_id
                """,
                Integer.class,
                docId, evaluador, periodo, pDesemp, pPedag, pPerfil, pResp, pFinal, resultado, obs,
                rubH, rubD, rubT, rubV, concPers, situacion
            );

            // Actualizar estado_evaluacion del docente
            jdbcTemplate.update(
                "UPDATE docente SET estado_evaluacion = ? WHERE docente_id = ?",
                resultado, docId
            );

            Map<String, Object> result = new HashMap<>();
            result.put("status", "ok");
            result.put("evaluacion_id", newId);
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
    // PUT /api/evaluaciones/{id} — Actualizar
    // =============================================
    @PutMapping("/evaluaciones/{id}")
    public ResponseEntity<Map<String, Object>> updateEvaluacion(
            @PathVariable int id,
            @RequestBody Map<String, Object> payload) {
        try {
            BigDecimal pDesemp = dec(payload.get("puntaje_desempeno"));
            BigDecimal pPedag  = dec(payload.get("puntaje_pedagogia"));
            BigDecimal pPerfil = dec(payload.get("puntaje_perfil"));
            BigDecimal pResp   = dec(payload.get("puntaje_responsabilidad"));
            BigDecimal pFinal  = dec(payload.get("puntaje_final"));
            String resultado   = str(payload.get("resultado"));
            String obs         = str(payload.get("observaciones"));

            String rubH2     = str(payload.get("rubrica_habilidades"));
            String rubD2     = str(payload.get("rubrica_dominio"));
            String rubT2     = str(payload.get("rubrica_tics"));
            String rubV2     = str(payload.get("rubrica_vinculacion"));
            String concPers2 = str(payload.get("concepto_personal"));

            jdbcTemplate.update(
                """
                UPDATE evaluacion SET
                    puntaje_desempeno = ?,
                    puntaje_pedagogia = ?,
                    puntaje_perfil = ?,
                    puntaje_responsabilidad = ?,
                    puntaje_final = ?,
                    resultado = ?,
                    observaciones = ?,
                    rubrica_habilidades = ?,
                    rubrica_dominio = ?,
                    rubrica_tics = ?,
                    rubrica_vinculacion = ?,
                    concepto_personal = ?
                WHERE evaluacion_id = ?
                """,
                pDesemp, pPedag, pPerfil, pResp, pFinal, resultado, obs,
                rubH2, rubD2, rubT2, rubV2, concPers2, id
            );

            // Actualizar estado del docente
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT docente_id FROM evaluacion WHERE evaluacion_id = ?", id
            );
            if (!rows.isEmpty()) {
                int docId = Integer.parseInt(rows.get(0).get("docente_id").toString());
                jdbcTemplate.update(
                    "UPDATE docente SET estado_evaluacion = ? WHERE docente_id = ?",
                    resultado, docId
                );
            }

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

    // Helpers
    private String str(Object o) { return o != null ? o.toString() : ""; }
    private BigDecimal dec(Object o) {
        if (o == null) return BigDecimal.ZERO;
        try { return new BigDecimal(o.toString()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }
}
