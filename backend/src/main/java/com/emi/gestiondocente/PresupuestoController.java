package com.emi.gestiondocente;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PresupuestoController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/presupuesto")
    public ResponseEntity<List<Map<String, Object>>> getPresupuesto() {
        String sql = """
            SELECT 
                d.docente_id, d.nombre, d.condicion, d.rfc, d.curp, d.regimen_sat,
                m.nombre as materia, c.siglas as carrera,
                a.horas, a.nivel_pago
            FROM Docente d
            JOIN Asignacion a ON d.docente_id = a.docente_id
            JOIN Materia m ON a.materia_id = m.materia_id
            JOIN Carrera c ON m.carrera_id = c.carrera_id
            WHERE a.horas > 0
            ORDER BY c.siglas, d.nombre
            """;
            
        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql);
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Map<String, Object> r : records) {
            Map<String, Object> map = new HashMap<>(r);
            
            BigDecimal horas = (BigDecimal) r.get("horas");
            if (horas == null) horas = BigDecimal.ZERO;
            
            String nivelPago = (String) r.get("nivel_pago");
            if (nivelPago == null) nivelPago = "Licenciatura"; // default
            
            BigDecimal tarifa = new BigDecimal("486.71");
            if (nivelPago.equalsIgnoreCase("Maestría")) {
                tarifa = new BigDecimal("851.75");
            } else if (nivelPago.equalsIgnoreCase("Técnico")) {
                tarifa = new BigDecimal("231.19");
            }
            
            BigDecimal subtotal = horas.multiply(tarifa);
            
            // Impuestos calculation
            String regimen = (String) r.get("regimen_sat");
            if (regimen == null) regimen = "RS";
            
            BigDecimal ivaRate = new BigDecimal("0.16");
            BigDecimal retIvaRate = new BigDecimal("0.106667");
            
            BigDecimal isrRetRate = BigDecimal.ZERO;
            if (regimen.equalsIgnoreCase("RESICO")) {
                isrRetRate = new BigDecimal("0.0125");
            } else if (regimen.equalsIgnoreCase("SP")) {
                isrRetRate = new BigDecimal("0.10");
            } else {
                // Para RS, asumiremos 10% fijo para el reporte, o 0 si no se retiene directo
                isrRetRate = new BigDecimal("0.10");
            }
            
            BigDecimal iva = BigDecimal.ZERO;
            BigDecimal retIva = BigDecimal.ZERO;
            BigDecimal retIsr = subtotal.multiply(isrRetRate);
            
            if (regimen.equalsIgnoreCase("SP") || regimen.equalsIgnoreCase("RESICO")) {
                iva = subtotal.multiply(ivaRate);
                retIva = subtotal.multiply(retIvaRate);
            }
            
            BigDecimal neto = subtotal.add(iva).subtract(retIva).subtract(retIsr);
            
            map.put("tarifa", tarifa);
            map.put("subtotal", subtotal);
            map.put("iva", iva);
            map.put("retIva", retIva);
            map.put("retIsr", retIsr);
            map.put("neto", neto);
            
            result.add(map);
        }
        
        return ResponseEntity.ok(result);
    }
}
