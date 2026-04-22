package com.emi.gestiondocente;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
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
            BigDecimal horas = new BigDecimal(payload.get("horas").toString());
            if (horas.compareTo(BigDecimal.ZERO) < 0) {
                horas = BigDecimal.ZERO;
            }
            String materia = payload.get("materia") != null ? payload.get("materia").toString() : "";
            String nivelPago = payload.get("nivel_pago") != null ? payload.get("nivel_pago").toString() : "Licenciatura";

            jdbcTemplate.update(
                """
                UPDATE asignacion SET horas = ?, nivel_pago = ?
                WHERE docente_id = ?
                  AND materia_id = (SELECT materia_id FROM materia WHERE nombre = ? LIMIT 1)
                """,
                horas, nivelPago, id, materia
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
            BigDecimal horas = new BigDecimal(payload.get("horas").toString());

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

                        IF NOT EXISTS (SELECT 1 FROM asignacion WHERE docente_id = %d AND materia_id = v_materia_id) THEN
                            INSERT INTO asignacion(docente_id, materia_id, horas, nivel_pago) VALUES(%d, v_materia_id, %f, '%s');
                        ELSE
                            UPDATE asignacion SET horas = %f, nivel_pago = '%s' WHERE docente_id = %d AND materia_id = v_materia_id;
                        END IF;

                    END $$;
                    """,
                    carrera.replace("'", "''"),
                    materia.replace("'", "''"),
                    materia.replace("'", "''"),
                    id, id, horas.floatValue(), nivelPago.replace("'", "''"),
                    horas.floatValue(), nivelPago.replace("'", "''"), id
                )
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
}
