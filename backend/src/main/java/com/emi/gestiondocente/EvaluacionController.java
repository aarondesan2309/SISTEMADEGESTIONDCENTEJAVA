package com.emi.gestiondocente;

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
            %s
            GROUP BY e.evaluacion_id, d.nombre, d.condicion
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

            Integer newId = jdbcTemplate.queryForObject(
                """
                INSERT INTO evaluacion(docente_id, evaluador, periodo,
                    puntaje_desempeno, puntaje_pedagogia, puntaje_perfil,
                    puntaje_responsabilidad, puntaje_final, resultado, observaciones)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING evaluacion_id
                """,
                Integer.class,
                docId, evaluador, periodo, pDesemp, pPedag, pPerfil, pResp, pFinal, resultado, obs
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

            jdbcTemplate.update(
                """
                UPDATE evaluacion SET
                    puntaje_desempeno = ?,
                    puntaje_pedagogia = ?,
                    puntaje_perfil = ?,
                    puntaje_responsabilidad = ?,
                    puntaje_final = ?,
                    resultado = ?,
                    observaciones = ?
                WHERE evaluacion_id = ?
                """,
                pDesemp, pPedag, pPerfil, pResp, pFinal, resultado, obs, id
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
