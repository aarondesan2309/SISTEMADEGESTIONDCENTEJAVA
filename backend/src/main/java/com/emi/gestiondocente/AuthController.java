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
public class AuthController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");

        if (username == null || password == null) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Usuario y contraseña son requeridos");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT role FROM usuario WHERE username = ? AND password_hash = crypt(?, password_hash)",
                username, password
            );

            if (!rows.isEmpty()) {
                String role = rows.get(0).get("role").toString();
                System.out.println("Login exitoso para: " + username + " con rol: " + role);
                Map<String, String> result = new HashMap<>();
                result.put("status", "ok");
                result.put("role", role);
                return ResponseEntity.ok(result);
            } else {
                System.out.println("Login fallido para: " + username + " (Credenciales invalidas)");
                Map<String, String> error = new HashMap<>();
                error.put("status", "error");
                error.put("message", "Credenciales inválidas");
                return ResponseEntity.status(401).body(error);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Error interno del servidor");
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
