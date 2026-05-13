package com.emi.gestiondocente;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CursoController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${sgdc.storage.expedientes}")
    private String expedientesDir;

    private void ensureTable() {
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS curso_docente (" +
                "  curso_id SERIAL PRIMARY KEY," +
                "  docente_id INT," +
                "  nombre VARCHAR(200) NOT NULL," +
                "  institucion VARCHAR(200)," +
                "  tipo VARCHAR(50) DEFAULT 'Curso'," +
                "  horas INT," +
                "  estatus_curso VARCHAR(30) DEFAULT 'Completado'," +
                "  observaciones TEXT" +
                ")"
            );
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.execute("ALTER TABLE curso_docente ADD COLUMN IF NOT EXISTS constancia_path VARCHAR(255)");
        } catch (Exception ignored) {}
    }

    private String s(Object o) { return o != null ? o.toString().strip() : ""; }

    // GET /api/docentes/{id}/cursos
    @GetMapping("/docentes/{id}/cursos")
    public ResponseEntity<List<Map<String, Object>>> getCursos(@PathVariable int id) {
        ensureTable();
        try {
            return ResponseEntity.ok(jdbcTemplate.queryForList(
                "SELECT * FROM curso_docente WHERE docente_id = ? ORDER BY curso_id DESC", id));
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    // POST /api/docentes/{id}/cursos
    @PostMapping("/docentes/{id}/cursos")
    public ResponseEntity<Map<String, Object>> addCurso(
            @PathVariable int id,
            @RequestBody Map<String, Object> body) {
        ensureTable();
        Map<String, Object> result = new HashMap<>();
        try {
            Integer horas = null;
            Object horasRaw = body.get("horas");
            if (horasRaw != null && !horasRaw.toString().isBlank()) {
                try {
                    int h = Integer.parseInt(horasRaw.toString());
                    horas = Math.max(1, Math.min(h, 9999));
                } catch (Exception ignored) {}
            }

            String nombre     = s(body.get("nombre"));
            String inst       = s(body.get("institucion"));
            String tipo       = s(body.get("tipo")).isEmpty() ? "Curso" : s(body.get("tipo"));
            String estatusCur = s(body.get("estatus_curso")).isEmpty() ? "Completado" : s(body.get("estatus_curso"));
            String obs        = s(body.get("observaciones"));

            if (nombre.isEmpty()) {
                result.put("status", "error");
                result.put("message", "El nombre del curso es obligatorio");
                return ResponseEntity.badRequest().body(result);
            }

            Integer newId = jdbcTemplate.queryForObject(
                "INSERT INTO curso_docente(docente_id,nombre,institucion,tipo,horas,estatus_curso,observaciones) " +
                "VALUES(?,?,?,?,?,?,?) RETURNING curso_id",
                Integer.class,
                id, nombre, inst, tipo, horas, estatusCur, obs
            );

            result.put("status", "ok");
            result.put("curso_id", newId);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            result.put("status", "error");
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    // DELETE /api/cursos/{id}
    @DeleteMapping("/cursos/{id}")
    public ResponseEntity<Map<String, Object>> deleteCurso(@PathVariable int id) {
        Map<String, Object> result = new HashMap<>();
        try {
            int rows = jdbcTemplate.update("DELETE FROM curso_docente WHERE curso_id = ?", id);
            result.put("status", rows > 0 ? "ok" : "error");
            if (rows == 0) result.put("message", "Curso no encontrado");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    // POST /api/cursos/{id}/constancia
    @PostMapping("/cursos/{id}/constancia")
    public ResponseEntity<Map<String, Object>> uploadConstancia(
            @PathVariable int id,
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            String ext = getExt(file.getOriginalFilename());
            String savedName = "constancia_" + id + ext;
            Path dest = Paths.get(expedientesDir + savedName);
            Files.createDirectories(dest.getParent());
            file.transferTo(dest.toFile());
            ensureTable();
            jdbcTemplate.update("UPDATE curso_docente SET constancia_path = ? WHERE curso_id = ?", savedName, id);
            result.put("status", "ok");
            result.put("constancia_path", savedName);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("status", "error");
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    // DELETE /api/cursos/{id}/constancia
    @DeleteMapping("/cursos/{id}/constancia")
    public ResponseEntity<Map<String, Object>> deleteConstancia(@PathVariable int id) {
        Map<String, Object> result = new HashMap<>();
        try {
            ensureTable();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT constancia_path FROM curso_docente WHERE curso_id = ?", id);
            if (!rows.isEmpty() && rows.get(0).get("constancia_path") != null) {
                try { Files.deleteIfExists(Paths.get(expedientesDir + rows.get(0).get("constancia_path").toString())); }
                catch (Exception ignored) {}
            }
            jdbcTemplate.update("UPDATE curso_docente SET constancia_path = NULL WHERE curso_id = ?", id);
            result.put("status", "ok");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    private String getExt(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }
}
