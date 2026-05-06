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
public class AsignacionController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // =============================================
    // PUT /api/asignacion/{id}/horas
    // =============================================
    @PutMapping("/asignacion/{id}/horas")
    public ResponseEntity<Map<String, Object>> updateHoras(
            @PathVariable int id,
            @RequestBody Map<String, Object> payload) {

        try {
            String materia = payload.get("materia") != null ? payload.get("materia").toString() : "";
            String nivelPago = payload.get("nivel_pago") != null ? payload.get("nivel_pago").toString() : "Licenciatura";

            // Determinar si vienen horas por mes o solo el total
            boolean tieneMeses = payload.containsKey("horas_m1") || payload.containsKey("horas_m2")
                               || payload.containsKey("horas_m3") || payload.containsKey("horas_m4");

            BigDecimal horasM1, horasM2, horasM3, horasM4, totalHoras;

            if (tieneMeses) {
                // Modo detallado: el usuario proporcionó cada mes por separado
                horasM1 = new BigDecimal(payload.getOrDefault("horas_m1", "0").toString());
                horasM2 = new BigDecimal(payload.getOrDefault("horas_m2", "0").toString());
                horasM3 = new BigDecimal(payload.getOrDefault("horas_m3", "0").toString());
                horasM4 = new BigDecimal(payload.getOrDefault("horas_m4", "0").toString());
                if (horasM1.compareTo(BigDecimal.ZERO) < 0) horasM1 = BigDecimal.ZERO;
                if (horasM2.compareTo(BigDecimal.ZERO) < 0) horasM2 = BigDecimal.ZERO;
                if (horasM3.compareTo(BigDecimal.ZERO) < 0) horasM3 = BigDecimal.ZERO;
                if (horasM4.compareTo(BigDecimal.ZERO) < 0) horasM4 = BigDecimal.ZERO;
                totalHoras = horasM1.add(horasM2).add(horasM3).add(horasM4);
            } else {
                // Modo simple: distribución equitativa entre los 4 meses
                totalHoras = new BigDecimal(payload.getOrDefault("horas", "0").toString());
                if (totalHoras.compareTo(BigDecimal.ZERO) < 0) totalHoras = BigDecimal.ZERO;
                // Distribuir: cada mes recibe el mismo total (son horas TOTALES del semestre por mes)
                horasM1 = totalHoras;
                horasM2 = totalHoras;
                horasM3 = totalHoras;
                horasM4 = totalHoras;
            }

            jdbcTemplate.update(
                """
                UPDATE asignacion SET horas = ?, horas_m1 = ?, horas_m2 = ?, horas_m3 = ?, horas_m4 = ?, nivel_pago = ?
                WHERE docente_id = ?
                  AND materia_id = (SELECT materia_id FROM materia WHERE nombre = ? LIMIT 1)
                """,
                totalHoras, horasM1, horasM2, horasM3, horasM4, nivelPago, id, materia
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

    // =============================================
    // POST /api/asignacion/{id}/nueva
    // =============================================
    @PostMapping("/asignacion/{id}/nueva")
    public ResponseEntity<Map<String, Object>> addMateriaDinamica(
            @PathVariable int id,
            @RequestBody Map<String, Object> payload) {
        
        try {
            String materia = payload.get("materia").toString();
            String carrera = payload.get("carrera") != null ? payload.get("carrera").toString() : "ICI";
            String nivelPago = payload.get("nivel_pago") != null ? payload.get("nivel_pago").toString() : "Licenciatura";

            boolean tieneMeses = payload.containsKey("horas_m1") || payload.containsKey("horas_m2")
                               || payload.containsKey("horas_m3") || payload.containsKey("horas_m4");

            BigDecimal horasM1, horasM2, horasM3, horasM4, totalHoras;

            if (tieneMeses) {
                horasM1 = new BigDecimal(payload.getOrDefault("horas_m1", "0").toString());
                horasM2 = new BigDecimal(payload.getOrDefault("horas_m2", "0").toString());
                horasM3 = new BigDecimal(payload.getOrDefault("horas_m3", "0").toString());
                horasM4 = new BigDecimal(payload.getOrDefault("horas_m4", "0").toString());
                if (horasM1.compareTo(BigDecimal.ZERO) < 0) horasM1 = BigDecimal.ZERO;
                if (horasM2.compareTo(BigDecimal.ZERO) < 0) horasM2 = BigDecimal.ZERO;
                if (horasM3.compareTo(BigDecimal.ZERO) < 0) horasM3 = BigDecimal.ZERO;
                if (horasM4.compareTo(BigDecimal.ZERO) < 0) horasM4 = BigDecimal.ZERO;
                totalHoras = horasM1.add(horasM2).add(horasM3).add(horasM4);
            } else {
                // Modo simple: horas por mes (iguales en cada uno)
                totalHoras = new BigDecimal(payload.getOrDefault("horas", "0").toString());
                if (totalHoras.compareTo(BigDecimal.ZERO) < 0) totalHoras = BigDecimal.ZERO;
                horasM1 = totalHoras;
                horasM2 = totalHoras;
                horasM3 = totalHoras;
                horasM4 = totalHoras;
            }

            // Buscar carrera (con parámetro)
            List<Map<String,Object>> carreraRows = jdbcTemplate.queryForList(
                "SELECT carrera_id FROM carrera WHERE siglas = ? LIMIT 1", carrera);
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

            // Upsert asignación
            List<Map<String,Object>> existeRows = jdbcTemplate.queryForList(
                "SELECT 1 FROM asignacion WHERE docente_id = ? AND materia_id = ?", id, materiaId);
            if (existeRows.isEmpty()) {
                // Verificar límite de 2 materias por docente
                Integer countActual = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT materia_id) FROM asignacion WHERE docente_id = ?",
                    Integer.class, id);
                if (countActual != null && countActual >= 2) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("status", "error");
                    error.put("message", "Límite alcanzado: un docente puede tener máximo 2 materias.");
                    return ResponseEntity.badRequest().body(error);
                }
                jdbcTemplate.update(
                    "INSERT INTO asignacion(docente_id, materia_id, horas, horas_m1, horas_m2, horas_m3, horas_m4, nivel_pago) VALUES(?, ?, ?, ?, ?, ?, ?, ?)",
                    id, materiaId, totalHoras, horasM1, horasM2, horasM3, horasM4, nivelPago);
            } else {
                jdbcTemplate.update(
                    "UPDATE asignacion SET horas = ?, horas_m1 = ?, horas_m2 = ?, horas_m3 = ?, horas_m4 = ?, nivel_pago = ? WHERE docente_id = ? AND materia_id = ?",
                    totalHoras, horasM1, horasM2, horasM3, horasM4, nivelPago, id, materiaId);
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
}
