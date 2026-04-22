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
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(GestionDocenteApplication.class);
    }

    public static void main(String[] args) {
        System.setProperty("server.port", "8091");
        SpringApplication.run(GestionDocenteApplication.class, args);
    }

    @Bean
    public CommandLineRunner runMigration(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE asignacion ADD COLUMN IF NOT EXISTS nivel_pago VARCHAR(50) DEFAULT 'Licenciatura'");
                jdbcTemplate.execute("ALTER TABLE contrato_emitido ADD COLUMN IF NOT EXISTS folio VARCHAR(100)");
                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
                jdbcTemplate.execute("UPDATE usuario SET password_hash = crypt('admin', gen_salt('bf')) WHERE username = 'admin'");
                System.out.println("Migracion de DB exitosa: nivel_pago, folio y pgcrypto admin reset");
            } catch (Exception e) {
                System.out.println("Error Migracion DB: " + e.getMessage());
            }
        };
    }
}
