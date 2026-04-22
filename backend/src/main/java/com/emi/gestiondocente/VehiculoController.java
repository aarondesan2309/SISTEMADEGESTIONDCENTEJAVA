package com.emi.gestiondocente;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<List<Map<String, Object>>> getVehiculos() {
        String sql = """
            SELECT v.vehiculo_id,
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
            ORDER BY v.vehiculo_id DESC
            """;
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
        return ResponseEntity.ok(result);
    }
}
