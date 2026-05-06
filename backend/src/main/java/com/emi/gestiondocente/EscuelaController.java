package com.emi.gestiondocente;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class EscuelaController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPass;

    /**
     * POST /api/escuela/configurar
     * Crea una nueva base de datos para un plantel, replica el schema,
     * inserta carreras y crea usuarios (admin, director, jefes de carrera).
     */
    @PostMapping("/escuela/configurar")
    public ResponseEntity<Map<String, Object>> configurarEscuela(@RequestBody Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();

        try {
            String siglas    = (String) payload.get("siglas");
            String nombre    = (String) payload.get("nombre");
            String director  = (String) payload.get("director");
            String ciclo     = (String) payload.get("ciclo");
            String dbHost    = (String) payload.getOrDefault("dbHost", "localhost");
            String dbPort    = (String) payload.getOrDefault("dbPort", "5432");
            String dbName    = (String) payload.getOrDefault("dbName", "gestion_docente_" + siglas.toLowerCase());
            String dbUsuario = (String) payload.getOrDefault("dbUser", "postgres");
            String dbClave   = (String) payload.getOrDefault("dbPass", dbPass);

            @SuppressWarnings("unchecked")
            List<String> carreras = (List<String>) payload.getOrDefault("carreras", new ArrayList<>());

            // Usuarios a crear
            @SuppressWarnings("unchecked")
            List<Map<String, String>> usuarios = (List<Map<String, String>>) payload.getOrDefault("usuarios", new ArrayList<>());

            // 1. Conectar a postgres (database administrativa) para crear la BD
            String adminUrl = String.format("jdbc:postgresql://%s:%s/postgres", dbHost, dbPort);
            try (Connection adminConn = DriverManager.getConnection(adminUrl, dbUsuario,
                    dbClave.isEmpty() ? dbPass : dbClave)) {
                adminConn.setAutoCommit(true);
                Statement stmt = adminConn.createStatement();

                // Verificar si ya existe
                var rs = stmt.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + dbName + "'");
                if (rs.next()) {
                    result.put("status", "warning");
                    result.put("message", "La base de datos '" + dbName + "' ya existe. Se configurarán solo los usuarios y carreras.");
                } else {
                    stmt.execute("CREATE DATABASE " + dbName);
                    result.put("dbCreated", true);
                }
                rs.close();
                stmt.close();
            }

            // 2. Conectar a la nueva BD y crear el schema completo
            String newDbUrl = String.format("jdbc:postgresql://%s:%s/%s", dbHost, dbPort, dbName);
            try (Connection conn = DriverManager.getConnection(newDbUrl, dbUsuario,
                    dbClave.isEmpty() ? dbPass : dbClave)) {
                conn.setAutoCommit(true);
                Statement stmt = conn.createStatement();

                // Extensión pgcrypto para passwords
                stmt.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");

                // --- Crear tablas ---
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS carrera (
                        carrera_id SERIAL PRIMARY KEY,
                        nombre VARCHAR(255),
                        siglas VARCHAR(20)
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS docente (
                        docente_id SERIAL PRIMARY KEY,
                        nombre VARCHAR(255) NOT NULL,
                        rfc VARCHAR(20),
                        curp VARCHAR(20),
                        grado_acad VARCHAR(100),
                        grado_mil VARCHAR(100),
                        condicion VARCHAR(50),
                        genero VARCHAR(10),
                        domicilio TEXT,
                        credencial_ine VARCHAR(50),
                        tipo_sangre VARCHAR(10),
                        estado_evaluacion VARCHAR(50),
                        matricula VARCHAR(50),
                        foto_path VARCHAR(255),
                        cedula_path VARCHAR(255),
                        regimen_sat VARCHAR(20),
                        fecha_alta DATE DEFAULT CURRENT_DATE
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS materia (
                        materia_id SERIAL PRIMARY KEY,
                        nombre VARCHAR(255) NOT NULL,
                        carrera_id INT REFERENCES carrera(carrera_id)
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS asignacion (
                        asignacion_id SERIAL PRIMARY KEY,
                        docente_id INT REFERENCES docente(docente_id),
                        materia_id INT REFERENCES materia(materia_id),
                        horas NUMERIC,
                        horas_m1 NUMERIC DEFAULT 0,
                        horas_m2 NUMERIC DEFAULT 0,
                        horas_m3 NUMERIC DEFAULT 0,
                        horas_m4 NUMERIC DEFAULT 0,
                        nivel_pago VARCHAR(50) DEFAULT 'Licenciatura'
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS evaluacion (
                        evaluacion_id SERIAL PRIMARY KEY,
                        docente_id INT REFERENCES docente(docente_id),
                        evaluador VARCHAR(255),
                        periodo VARCHAR(50),
                        fecha_evaluacion DATE,
                        puntaje_desempeno NUMERIC,
                        puntaje_pedagogia NUMERIC,
                        puntaje_perfil NUMERIC,
                        puntaje_responsabilidad NUMERIC,
                        puntaje_final NUMERIC,
                        resultado VARCHAR(50),
                        observaciones TEXT
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS contrato_emitido (
                        id SERIAL PRIMARY KEY,
                        docente_id INT REFERENCES docente(docente_id),
                        emitido_por VARCHAR(255),
                        fecha_emision TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        folio VARCHAR(100),
                        materia_id INT REFERENCES materia(materia_id)
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS expediente_documento (
                        documento_id SERIAL PRIMARY KEY,
                        docente_id INT REFERENCES docente(docente_id),
                        tipo_documento VARCHAR(100) NOT NULL,
                        nombre_archivo VARCHAR(255),
                        archivo_path VARCHAR(255),
                        fecha_subida TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        estado VARCHAR(20) DEFAULT 'Pendiente',
                        observaciones TEXT
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS vehiculo (
                        vehiculo_id SERIAL PRIMARY KEY,
                        docente_id INT REFERENCES docente(docente_id),
                        marca VARCHAR(50),
                        modelo VARCHAR(80),
                        anio VARCHAR(10),
                        color VARCHAR(50),
                        placas VARCHAR(20)
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS usuario (
                        usuario_id SERIAL PRIMARY KEY,
                        username VARCHAR(50) NOT NULL UNIQUE,
                        password_hash VARCHAR(255) NOT NULL,
                        role VARCHAR(50) NOT NULL
                    )
                """);

                // 3. Insertar carreras
                List<String> carrerasInsertadas = new ArrayList<>();
                for (String carreraSiglas : carreras) {
                    String nombreCarrera = getNombreCarrera(carreraSiglas);
                    // Solo insertar si no existe
                    var rsC = stmt.executeQuery(
                        "SELECT 1 FROM carrera WHERE siglas = '" + carreraSiglas + "'");
                    if (!rsC.next()) {
                        stmt.execute("INSERT INTO carrera (nombre, siglas) VALUES ('" +
                            nombreCarrera.replace("'", "''") + "', '" + carreraSiglas + "')");
                        carrerasInsertadas.add(carreraSiglas);
                    }
                    rsC.close();
                }

                // 4. Insertar usuarios
                List<Map<String, String>> usuariosCreados = new ArrayList<>();
                for (Map<String, String> u : usuarios) {
                    String uname = u.get("username");
                    String upass = u.get("password");
                    String urole = u.get("role");
                    if (uname == null || upass == null || urole == null) continue;

                    // Verificar si ya existe
                    var rsU = stmt.executeQuery(
                        "SELECT 1 FROM usuario WHERE username = '" + uname.replace("'", "''") + "'");
                    if (!rsU.next()) {
                        stmt.execute("INSERT INTO usuario (username, password_hash, role) VALUES ('" +
                            uname.replace("'", "''") + "', crypt('" +
                            upass.replace("'", "''") + "', gen_salt('bf')), '" +
                            urole.replace("'", "''") + "')");
                        Map<String, String> created = new HashMap<>();
                        created.put("username", uname);
                        created.put("role", urole);
                        usuariosCreados.add(created);
                    }
                    rsU.close();
                }

                stmt.close();

                result.put("status", "ok");
                result.put("message", "Plantel " + siglas + " configurado exitosamente");
                result.put("database", dbName);
                result.put("carrerasInsertadas", carrerasInsertadas);
                result.put("usuariosCreados", usuariosCreados);
                result.put("totalCarreras", carreras.size());
                result.put("totalUsuarios", usuariosCreados.size());
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            result.put("status", "error");
            result.put("message", "Error configurando escuela: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * GET /api/escuelas — Lista las bases de datos de escuelas configuradas
     */
    @GetMapping("/escuelas")
    public ResponseEntity<List<Map<String, Object>>> listarEscuelas() {
        try {
            List<Map<String, Object>> escuelas = jdbcTemplate.queryForList(
                "SELECT datname FROM pg_database WHERE datname LIKE 'gestion_docente_%' ORDER BY datname"
            );
            return ResponseEntity.ok(escuelas);
        } catch (Exception e) {
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    /**
     * GET /api/escuelas/{dbname}/detalle — Carreras y usuarios de un plantel
     */
    @GetMapping("/escuelas/{dbname}/detalle")
    public ResponseEntity<Map<String, Object>> detalleEscuela(
            @PathVariable String dbname,
            @RequestParam(defaultValue = "localhost") String dbHost,
            @RequestParam(defaultValue = "5432") String dbPort) {
        Map<String, Object> result = new HashMap<>();
        try {
            String url = String.format("jdbc:postgresql://%s:%s/%s", dbHost, dbPort, dbname);
            try (java.sql.Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                java.sql.Statement stmt = conn.createStatement();

                // Carreras
                List<Map<String, Object>> carreras = new ArrayList<>();
                var rsC = stmt.executeQuery("SELECT carrera_id, nombre, siglas FROM carrera ORDER BY siglas");
                while (rsC.next()) {
                    Map<String, Object> c = new HashMap<>();
                    c.put("carrera_id", rsC.getInt("carrera_id"));
                    c.put("nombre", rsC.getString("nombre"));
                    c.put("siglas", rsC.getString("siglas"));
                    carreras.add(c);
                }
                rsC.close();

                // Usuarios
                List<Map<String, Object>> usuarios = new ArrayList<>();
                var rsU = stmt.executeQuery("SELECT username, role FROM usuario ORDER BY role, username");
                while (rsU.next()) {
                    Map<String, Object> u = new HashMap<>();
                    u.put("username", rsU.getString("username"));
                    u.put("role", rsU.getString("role"));
                    usuarios.add(u);
                }
                rsU.close();

                // Total docentes
                int totalDocentes = 0;
                try {
                    var rsD = stmt.executeQuery("SELECT COUNT(*) FROM docente");
                    if (rsD.next()) totalDocentes = rsD.getInt(1);
                    rsD.close();
                } catch (Exception ignored) {}

                stmt.close();
                result.put("status", "ok");
                result.put("database", dbname);
                result.put("carreras", carreras);
                result.put("usuarios", usuarios);
                result.put("totalDocentes", totalDocentes);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("database", dbname);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * POST /api/escuelas/{dbname}/usuario — Agregar usuario a plantel existente
     */
    @PostMapping("/escuelas/{dbname}/usuario")
    public ResponseEntity<Map<String, Object>> agregarUsuarioEscuela(
            @PathVariable String dbname,
            @RequestBody Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            String dbHost = (String) payload.getOrDefault("dbHost", "localhost");
            String dbPort = (String) payload.getOrDefault("dbPort", "5432");
            String uname  = (String) payload.get("username");
            String upass  = (String) payload.get("password");
            String urole  = (String) payload.get("role");

            if (uname == null || upass == null || urole == null) {
                result.put("status", "error");
                result.put("message", "username, password y role son obligatorios");
                return ResponseEntity.badRequest().body(result);
            }

            String url = String.format("jdbc:postgresql://%s:%s/%s", dbHost, dbPort, dbname);
            try (java.sql.Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                conn.setAutoCommit(true);
                java.sql.Statement stmt = conn.createStatement();

                // Asegurar extensión pgcrypto
                stmt.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");

                // Verificar si ya existe
                var rs = stmt.executeQuery("SELECT 1 FROM usuario WHERE username = '" + uname.replace("'","''") + "'");
                if (rs.next()) {
                    // Actualizar contraseña y rol
                    stmt.execute("UPDATE usuario SET password_hash = crypt('" + upass.replace("'","''") + "', gen_salt('bf')), role = '" + urole.replace("'","''") + "' WHERE username = '" + uname.replace("'","''") + "'");
                    result.put("action", "updated");
                } else {
                    stmt.execute("INSERT INTO usuario (username, password_hash, role) VALUES ('" + uname.replace("'","''") + "', crypt('" + upass.replace("'","''") + "', gen_salt('bf')), '" + urole.replace("'","''") + "')");
                    result.put("action", "created");
                }
                rs.close();
                stmt.close();
            }
            result.put("status", "ok");
            result.put("username", uname);
            result.put("role", urole);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("status", "error");
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * POST /api/escuelas/{dbname}/carrera — Agregar carrera a plantel existente
     */
    @PostMapping("/escuelas/{dbname}/carrera")
    public ResponseEntity<Map<String, Object>> agregarCarreraEscuela(
            @PathVariable String dbname,
            @RequestBody Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            String dbHost  = (String) payload.getOrDefault("dbHost", "localhost");
            String dbPort  = (String) payload.getOrDefault("dbPort", "5432");
            String siglas  = (String) payload.get("siglas");
            if (siglas == null) {
                result.put("status", "error"); result.put("message", "siglas es requerido");
                return ResponseEntity.badRequest().body(result);
            }
            String nombreCarrera = getNombreCarrera(siglas);

            String url = String.format("jdbc:postgresql://%s:%s/%s", dbHost, dbPort, dbname);
            try (java.sql.Connection conn = DriverManager.getConnection(url, dbUser, dbPass)) {
                conn.setAutoCommit(true);
                java.sql.Statement stmt = conn.createStatement();
                var rs = stmt.executeQuery("SELECT 1 FROM carrera WHERE siglas = '" + siglas.replace("'","''") + "'");
                if (!rs.next()) {
                    stmt.execute("INSERT INTO carrera (nombre, siglas) VALUES ('" + nombreCarrera.replace("'","''") + "', '" + siglas.replace("'","''") + "')");
                    result.put("action", "created");
                } else {
                    result.put("action", "already_exists");
                }
                rs.close(); stmt.close();
            }
            result.put("status", "ok");
            result.put("siglas", siglas);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("status", "error"); result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    private String getNombreCarrera(String siglas) {
        return switch (siglas.toUpperCase()) {
            case "MED" -> "Medicina";
            case "ODO" -> "Odontología";
            case "ENF" -> "Enfermería";
            case "TC"  -> "Tronco Común";
            case "ICI" -> "Ingeniería en Computación e Informática";
            case "ICE" -> "Ingeniería en Comunicaciones y Electrónica";
            case "II"  -> "Ingeniería Industrial";
            case "IC"  -> "Ingeniería en Construcción";
            case "FAR" -> "Farmacología";
            case "BIO" -> "Biología";
            case "QFB" -> "Química Farmacéutica Biológica";
            default    -> siglas;
        };
    }
}
