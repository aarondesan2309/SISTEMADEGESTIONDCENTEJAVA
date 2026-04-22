package com.emi.gestiondocente;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ExpedienteController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String EXPEDIENTES_DIR = "C:\\temp\\gestion-docente-web\\expedientes\\";

    // =============================================
    // GET /api/expediente/{id}
    // =============================================
    @GetMapping("/expediente/{id}")
    public ResponseEntity<List<Map<String, Object>>> getExpediente(@PathVariable int id) {
        String sql = """
            SELECT documento_id, tipo_documento, nombre_archivo, archivo_path,
                   to_char(fecha_subida, 'YYYY-MM-DD HH24:MI') as fecha_subida,
                   estado, observaciones
            FROM expediente_documento
            WHERE docente_id = ?
            ORDER BY tipo_documento
            """;
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, id);
        return ResponseEntity.ok(result);
    }

    // =============================================
    // POST /api/expediente?tipo=...
    // =============================================
    @PostMapping("/expediente")
    public ResponseEntity<Map<String, Object>> uploadExpediente(
            @RequestParam("archivo") MultipartFile file,
            @RequestParam("docente_id") int docenteId,
            @RequestParam(value = "tipo", defaultValue = "otro") String tipo) {
        try {
            String ext = getExtension(file.getOriginalFilename());
            String savedName = "exp_" + docenteId + "_" + System.currentTimeMillis() + ext;
            Path dest = Paths.get(EXPEDIENTES_DIR + savedName);
            Files.createDirectories(dest.getParent());
            file.transferTo(dest.toFile());

            Integer newId = jdbcTemplate.queryForObject(
                """
                INSERT INTO expediente_documento(docente_id, tipo_documento, nombre_archivo, archivo_path)
                VALUES(?, ?, ?, ?)
                RETURNING documento_id
                """,
                Integer.class,
                docenteId, tipo, savedName, savedName
            );

            Map<String, Object> result = new HashMap<>();
            result.put("status", "ok");
            result.put("documento_id", newId);
            result.put("filename", savedName);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "No se pudo guardar el documento: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // =============================================
    // DELETE /api/expediente/{id}
    // =============================================
    @DeleteMapping("/expediente/{id}")
    public ResponseEntity<Map<String, Object>> deleteExpediente(@PathVariable int id) {
        try {
            // Obtener path antes de borrar
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT archivo_path FROM expediente_documento WHERE documento_id = ?", id
            );

            jdbcTemplate.update("DELETE FROM expediente_documento WHERE documento_id = ?", id);

            // Borrar archivo físico
            if (!rows.isEmpty()) {
                String path = rows.get(0).get("archivo_path") != null
                    ? rows.get(0).get("archivo_path").toString() : "";
                if (!path.isEmpty()) {
                    File f = new File(EXPEDIENTES_DIR + path);
                    if (f.exists()) f.delete();
                }
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

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.')).toLowerCase();
    }
}
