package com.emi.gestiondocente;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class GestionDocenteApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(GestionDocenteApplication.class);
    }

    public static void main(String[] args) {
        // En desarrollo local seguimos usando el puerto 8091
        System.setProperty("server.port", "8091");
        SpringApplication.run(GestionDocenteApplication.class, args);
    }

    @Bean
    public CommandLineRunner runMigration(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                // Columnas faltantes en Docente
                jdbcTemplate.execute("ALTER TABLE Docente ADD COLUMN IF NOT EXISTS foto_path VARCHAR(255)");
                jdbcTemplate.execute("ALTER TABLE Docente ADD COLUMN IF NOT EXISTS cedula_path VARCHAR(255)");
                
                // Columnas en asignacion y contrato
                jdbcTemplate.execute("ALTER TABLE asignacion ADD COLUMN IF NOT EXISTS nivel_pago VARCHAR(50) DEFAULT 'Licenciatura'");
                jdbcTemplate.execute("ALTER TABLE asignacion ADD COLUMN IF NOT EXISTS horas_m1 NUMERIC DEFAULT 0");
                jdbcTemplate.execute("ALTER TABLE asignacion ADD COLUMN IF NOT EXISTS horas_m2 NUMERIC DEFAULT 0");
                jdbcTemplate.execute("ALTER TABLE asignacion ADD COLUMN IF NOT EXISTS horas_m3 NUMERIC DEFAULT 0");
                jdbcTemplate.execute("ALTER TABLE asignacion ADD COLUMN IF NOT EXISTS horas_m4 NUMERIC DEFAULT 0");
                jdbcTemplate.execute("ALTER TABLE contrato_emitido ADD COLUMN IF NOT EXISTS folio VARCHAR(100)");
                
                // Tabla para expedientes
                jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS expediente_documento (
                        documento_id SERIAL PRIMARY KEY,
                        docente_id INT REFERENCES Docente(docente_id),
                        tipo_documento VARCHAR(100),
                        nombre_archivo VARCHAR(255),
                        archivo_path VARCHAR(255),
                        fecha_subida TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        estado VARCHAR(20) DEFAULT 'Pendiente',
                        observaciones TEXT
                    )
                """);

                // Campo fecha de alta en Docente
                jdbcTemplate.execute("ALTER TABLE Docente ADD COLUMN IF NOT EXISTS fecha_alta DATE DEFAULT CURRENT_DATE");

                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
                jdbcTemplate.execute("UPDATE usuario SET password_hash = crypt(username, gen_salt('bf'))");

                // Usuario administrador SEM (si no existe)
                jdbcTemplate.execute("""
                    INSERT INTO usuario (username, password_hash, role)
                    SELECT 'sem', crypt('sem123', gen_salt('bf')), 'SEM'
                    WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE role = 'SEM')
                """);

                System.out.println("Migracion de DB exitosa: Tablas y columnas de archivos verificadas");
            } catch (Exception e) {
                System.out.println("Error Migracion DB: " + e.getMessage());
            }
        };
    }
}
