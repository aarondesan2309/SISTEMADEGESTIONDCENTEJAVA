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
public class MateriaController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // =============================================
    // GET /api/materias/{id} — Materias por docente
    // =============================================
    @GetMapping("/materias/{id}")
    public ResponseEntity<List<Map<String, Object>>> getMateriasByDocente(@PathVariable int id) {
        String sql = """
            SELECT m.nombre as materia,
                   COALESCE(c.siglas, 'N/A') as carrera,
                   MAX(COALESCE(a.horas, 0)) as horas,
                   MAX(a.nivel_pago) as nivel_pago,
                   MAX(d.grado_acad) as grado
            FROM Asignacion a
            JOIN Materia m ON a.materia_id = m.materia_id
            LEFT JOIN Carrera c ON m.carrera_id = c.carrera_id
            JOIN Docente d ON a.docente_id = d.docente_id
            WHERE a.docente_id = ?
            GROUP BY m.nombre, c.siglas
            ORDER BY m.nombre
            """;
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, id);
        return ResponseEntity.ok(result);
    }
}
