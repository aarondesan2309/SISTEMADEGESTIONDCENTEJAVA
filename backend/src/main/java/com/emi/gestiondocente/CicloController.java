package com.emi.gestiondocente;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/ciclos")
@CrossOrigin(origins = "*")
public class CicloController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar() {
        return ResponseEntity.ok(jdbcTemplate.queryForList(
                "SELECT * FROM ciclo_academico ORDER BY fecha_inicio DESC"));
    }

    @GetMapping("/activo")
    public ResponseEntity<Map<String, Object>> activo() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ciclo_academico WHERE activo = true LIMIT 1");
        if (rows.isEmpty()) return ResponseEntity.noContent().build();
        Map<String, Object> ciclo = rows.get(0);
        ciclo.put("tabuladores", jdbcTemplate.queryForList(
                "SELECT nivel, monto_por_hora FROM tabulador_pago WHERE ciclo_id = ? ORDER BY nivel",
                ciclo.get("id")));
        return ResponseEntity.ok(ciclo);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> obtener(@PathVariable int id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ciclo_academico WHERE id = ?", id);
        if (rows.isEmpty()) return ResponseEntity.notFound().build();
        Map<String, Object> ciclo = rows.get(0);
        ciclo.put("tabuladores", jdbcTemplate.queryForList(
                "SELECT nivel, monto_por_hora FROM tabulador_pago WHERE ciclo_id = ? ORDER BY nivel",
                id));
        return ResponseEntity.ok(ciclo);
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> payload) {
        try {
            Integer id = jdbcTemplate.queryForObject(
                    "INSERT INTO ciclo_academico (nombre, nombre_corto, fecha_inicio, fecha_fin, " +
                            "fecha_contrato, mes1_nombre, mes2_nombre, mes3_nombre, mes4_nombre, " +
                            "periodo_txt, duracion_txt, activo) " +
                            "VALUES (?, ?, ?::date, ?::date, ?::date, ?, ?, ?, ?, ?, ?, false) RETURNING id",
                    Integer.class,
                    payload.get("nombre"),
                    payload.get("nombre_corto"),
                    payload.get("fecha_inicio"),
                    payload.get("fecha_fin"),
                    payload.get("fecha_contrato"),
                    payload.get("mes1_nombre"),
                    payload.get("mes2_nombre"),
                    payload.get("mes3_nombre"),
                    payload.get("mes4_nombre"),
                    payload.get("periodo_txt"),
                    payload.getOrDefault("duracion_txt", "4 meses"));
            return ResponseEntity.ok(Map.of("id", id, "message", "Ciclo creado"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Error: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable int id, @RequestBody Map<String, Object> payload) {
        try {
            jdbcTemplate.update(
                    "UPDATE ciclo_academico SET nombre=?, nombre_corto=?, fecha_inicio=?::date, " +
                            "fecha_fin=?::date, fecha_contrato=?::date, mes1_nombre=?, mes2_nombre=?, " +
                            "mes3_nombre=?, mes4_nombre=?, periodo_txt=?, duracion_txt=? WHERE id=?",
                    payload.get("nombre"), payload.get("nombre_corto"),
                    payload.get("fecha_inicio"), payload.get("fecha_fin"), payload.get("fecha_contrato"),
                    payload.get("mes1_nombre"), payload.get("mes2_nombre"),
                    payload.get("mes3_nombre"), payload.get("mes4_nombre"),
                    payload.get("periodo_txt"),
                    payload.getOrDefault("duracion_txt", "4 meses"),
                    id);
            return ResponseEntity.ok(Map.of("message", "Ciclo actualizado"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Error: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/activar")
    public ResponseEntity<?> activar(@PathVariable int id) {
        try {
            jdbcTemplate.update("UPDATE ciclo_academico SET activo = false WHERE activo = true");
            int rows = jdbcTemplate.update("UPDATE ciclo_academico SET activo = true WHERE id = ?", id);
            if (rows == 0) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(Map.of("message", "Ciclo activado"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Error: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/tabuladores")
    public ResponseEntity<?> actualizarTabuladores(
            @PathVariable int id,
            @RequestBody Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tabs = (List<Map<String, Object>>) payload.get("tabuladores");
            if (tabs == null) return ResponseEntity.badRequest().body(Map.of("message", "Falta lista de tabuladores"));

            for (Map<String, Object> t : tabs) {
                String nivel = t.get("nivel").toString();
                BigDecimal monto = new BigDecimal(t.get("monto_por_hora").toString());
                jdbcTemplate.update(
                        "INSERT INTO tabulador_pago (ciclo_id, nivel, monto_por_hora) VALUES (?, ?, ?) " +
                                "ON CONFLICT (ciclo_id, nivel) DO UPDATE SET monto_por_hora = EXCLUDED.monto_por_hora",
                        id, nivel, monto);
            }
            return ResponseEntity.ok(Map.of("message", "Tabuladores actualizados"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Error: " + e.getMessage()));
        }
    }
}
