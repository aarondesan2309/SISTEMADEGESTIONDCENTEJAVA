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
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class FotoCedulaController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String FOTOS_DIR   = "C:\\temp\\gestion-docente-web\\fotos\\";
    private static final String CEDULAS_DIR = "C:\\temp\\gestion-docente-web\\cedulas\\";

    // =============================================
    // POST /api/fotos
    // =============================================
    @PostMapping("/fotos")
    public ResponseEntity<Map<String, Object>> uploadFoto(
            @RequestParam("foto") MultipartFile file,
            @RequestParam("docente_id") int docenteId) {
        try {
            String ext = getExtension(file.getOriginalFilename());
            String savedName = "foto_" + docenteId + ext;
            Path dest = Paths.get(FOTOS_DIR + savedName);
            Files.createDirectories(dest.getParent());
            file.transferTo(dest.toFile());

            jdbcTemplate.update(
                "UPDATE Docente SET foto_path = ? WHERE docente_id = ?",
                savedName, docenteId
            );

            Map<String, Object> result = new HashMap<>();
            result.put("status", "ok");
            result.put("filename", savedName);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "No se pudo guardar la foto: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // =============================================
    // POST /api/cedulas
    // =============================================
    @PostMapping("/cedulas")
    public ResponseEntity<Map<String, Object>> uploadCedula(
            @RequestParam("cedula") MultipartFile file,
            @RequestParam("docente_id") int docenteId) {
        try {
            String ext = getExtension(file.getOriginalFilename());
            String savedName = "cedula_" + docenteId + ext;
            Path dest = Paths.get(CEDULAS_DIR + savedName);
            Files.createDirectories(dest.getParent());
            file.transferTo(dest.toFile());

            jdbcTemplate.update(
                "UPDATE Docente SET cedula_path = ? WHERE docente_id = ?",
                savedName, docenteId
            );

            Map<String, Object> result = new HashMap<>();
            result.put("status", "ok");
            result.put("filename", savedName);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "No se pudo guardar la cédula: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.')).toLowerCase();
    }
}
