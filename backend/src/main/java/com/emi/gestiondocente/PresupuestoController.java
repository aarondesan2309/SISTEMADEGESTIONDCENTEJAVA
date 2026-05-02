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
            if (nivelPago == null) nivelPago = "Licenciatura";

            // Tarifas IpmHorasC de BASE SUELDOS (igual que ContratoController)
            BigDecimal tarifa;
            switch (nivelPago.toLowerCase()) {
                case "doctorado":                      tarifa = new BigDecimal("1048.95"); break;
                case "maestría": case "maestria":      tarifa = new BigDecimal("734.27");  break;
                case "técnico":  case "tecnico":       tarifa = new BigDecimal("199.30");  break;
                default:                               tarifa = new BigDecimal("419.58");  break; // Licenciatura
            }

            BigDecimal subtotal = horas.multiply(tarifa).setScale(2, java.math.RoundingMode.HALF_UP);

            String regimen = (String) r.get("regimen_sat");
            if (regimen == null) regimen = "SP";

            BigDecimal iva    = BigDecimal.ZERO;
            BigDecimal retIva = BigDecimal.ZERO;
            BigDecimal retIsr;

            boolean esResico = regimen.equalsIgnoreCase("RESICO");
            // SP y RESICO: IVA 16% + retención IVA 10.67%
            // RS (Sueldos y Salarios asimilados): sin IVA ni retención
            boolean aplicaIvaRiva = !regimen.equalsIgnoreCase("RS");

            if (aplicaIvaRiva) {
                iva    = subtotal.multiply(new BigDecimal("0.16"))    .setScale(2, java.math.RoundingMode.HALF_UP);
                retIva = subtotal.multiply(new BigDecimal("0.106667")).setScale(2, java.math.RoundingMode.HALF_UP);
            }
            // ISR: SP/RS → 10%, RESICO → 1.25%
            BigDecimal tasaIsr = esResico ? new BigDecimal("0.0125") : new BigDecimal("0.10");
            retIsr = subtotal.multiply(tasaIsr).setScale(2, java.math.RoundingMode.HALF_UP);

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
