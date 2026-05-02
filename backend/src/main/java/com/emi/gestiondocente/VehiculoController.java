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
public class VehiculoController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // =============================================
    // GET /api/vehiculos
    // =============================================
    @GetMapping("/vehiculos")
    public ResponseEntity<List<Map<String, Object>>> getVehiculos(@RequestParam(required = false) String role) {
        String sql = """
            SELECT v.vehiculo_id,
                   d.docente_id,
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
            """;

        if (role != null && !role.equalsIgnoreCase("ADM") && !role.equalsIgnoreCase("DIR") && !role.equalsIgnoreCase("SEM") && !role.equalsIgnoreCase("JSA")) {
            sql += " WHERE EXISTS (SELECT 1 FROM Asignacion a JOIN Materia m ON a.materia_id = m.materia_id JOIN Carrera c ON m.carrera_id = c.carrera_id WHERE a.docente_id = d.docente_id AND c.siglas = '" + role.replace("'", "''") + "') ";
        }

        sql += " ORDER BY v.vehiculo_id DESC";
        return ResponseEntity.ok(jdbcTemplate.queryForList(sql));
    }

    // =============================================
    // POST /api/vehiculos
    // =============================================
    @PostMapping("/vehiculos")
    public ResponseEntity<Map<String, Object>> createVehiculo(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            int docenteId = Integer.parseInt(body.get("docente_id").toString());
            String marca   = sanitize(body.get("marca"));
            String modelo  = sanitize(body.get("modelo"));
            String color   = sanitize(body.get("color"));
            String placas  = sanitize(body.get("placas")).toUpperCase();
            Integer anio   = body.get("anio") != null ? Integer.parseInt(body.get("anio").toString()) : null;

            if (marca.isBlank() || placas.isBlank()) {
                result.put("status", "error");
                result.put("message", "Marca y placas son obligatorias");
                return ResponseEntity.badRequest().body(result);
            }

            Integer newId = jdbcTemplate.queryForObject(
                "INSERT INTO Vehiculo(docente_id, marca, modelo, anio, color, placas) VALUES(?,?,?,?,?,?) RETURNING vehiculo_id",
                Integer.class,
                docenteId, marca, modelo, anio, color, placas
            );

            result.put("status", "ok");
            result.put("vehiculo_id", newId);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            result.put("status", "error");
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    // =============================================
    // PUT /api/vehiculos/{id}
    // =============================================
    @PutMapping("/vehiculos/{id}")
    public ResponseEntity<Map<String, Object>> updateVehiculo(
            @PathVariable int id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String marca  = sanitize(body.get("marca"));
            String modelo = sanitize(body.get("modelo"));
            String color  = sanitize(body.get("color"));
            String placas = sanitize(body.get("placas")).toUpperCase();
            Integer anio  = body.get("anio") != null ? Integer.parseInt(body.get("anio").toString()) : null;

            if (marca.isBlank() || placas.isBlank()) {
                result.put("status", "error");
                result.put("message", "Marca y placas son obligatorias");
                return ResponseEntity.badRequest().body(result);
            }

            int rows = jdbcTemplate.update(
                "UPDATE Vehiculo SET marca=?, modelo=?, anio=?, color=?, placas=? WHERE vehiculo_id=?",
                marca, modelo, anio, color, placas, id
            );

            if (rows == 0) {
                result.put("status", "error");
                result.put("message", "Vehículo no encontrado");
                return ResponseEntity.status(404).body(result);
            }

            result.put("status", "ok");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            result.put("status", "error");
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    // =============================================
    // DELETE /api/vehiculos/{id}
    // =============================================
    @DeleteMapping("/vehiculos/{id}")
    public ResponseEntity<Map<String, Object>> deleteVehiculo(@PathVariable int id) {
        Map<String, Object> result = new HashMap<>();
        try {
            int rows = jdbcTemplate.update("DELETE FROM Vehiculo WHERE vehiculo_id=?", id);
            if (rows == 0) {
                result.put("status", "error");
                result.put("message", "Vehículo no encontrado");
                return ResponseEntity.status(404).body(result);
            }
            result.put("status", "ok");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("status", "error");
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    private String sanitize(Object val) {
        return val != null ? val.toString().strip() : "";
    }
}
