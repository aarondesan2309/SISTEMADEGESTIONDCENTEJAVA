package com.emi.gestiondocente;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;
import java.util.zip.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ContratoController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TEMPLATE_PATH = "C:\\temp\\gestion-docente-web\\MAR-AGO26 (2).docx";

    @GetMapping("/contratos")
    public ResponseEntity<List<Map<String, Object>>> getContratos() {
        String sql = "SELECT d.docente_id, d.nombre, d.condicion, d.rfc, d.curp, d.regimen_sat, c.emitido_por, c.folio, to_char(c.fecha_emision, 'YYYY-MM-DD HH24:MI:SS') as fecha_emision, string_agg(DISTINCT car.siglas, ', ') as carrera FROM docente d LEFT JOIN (SELECT DISTINCT ON (docente_id) docente_id, emitido_por, fecha_emision, folio FROM contrato_emitido ORDER BY docente_id, fecha_emision DESC) c ON d.docente_id = c.docente_id LEFT JOIN asignacion a ON d.docente_id = a.docente_id LEFT JOIN materia m ON a.materia_id = m.materia_id LEFT JOIN carrera car ON m.carrera_id = car.carrera_id GROUP BY d.docente_id, c.emitido_por, c.fecha_emision, c.folio ORDER BY d.nombre ASC";
        return ResponseEntity.ok(jdbcTemplate.queryForList(sql));
    }

    @PostMapping("/generarContrato")
    public ResponseEntity<?> generarContrato(@RequestBody Map<String, Object> payload) {
        System.out.println("Solicitud de contrato recibida: " + payload);
        try {
            int docId = Integer.parseInt(payload.get("docente_id").toString());
            String emitidoPor = payload.get("emitido_por") != null ? payload.get("emitido_por").toString() : "Sistema";

            BigDecimal totalHoras = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(horas), 0) FROM asignacion WHERE docente_id = ?", BigDecimal.class, docId);
            if (totalHoras != null && totalHoras.compareTo(new BigDecimal("80")) > 0) {
                Map<String, String> err = new HashMap<>();
                err.put("message", "Exceso de horas (" + totalHoras + "). El máximo es 80.");
                return ResponseEntity.badRequest().body(err);
            }

            Integer newId = jdbcTemplate.queryForObject("INSERT INTO contrato_emitido (docente_id, emitido_por) VALUES (?, ?) RETURNING id", Integer.class, docId, emitidoPor);
            String folio = "E.M.Ingría. " + (230 + newId);
            jdbcTemplate.update("UPDATE contrato_emitido SET folio = ? WHERE id = ?", folio, newId);

            Map<String, Object> docente = jdbcTemplate.queryForMap("SELECT d.nombre, d.rfc, d.curp, d.grado_acad, d.condicion, d.genero, d.domicilio, d.credencial_ine, d.regimen_sat, string_agg(DISTINCT c.siglas, ', ') as carrera FROM docente d LEFT JOIN asignacion a ON d.docente_id = a.docente_id LEFT JOIN materia m ON a.materia_id = m.materia_id LEFT JOIN carrera c ON m.carrera_id = c.carrera_id WHERE d.docente_id = ? GROUP BY d.docente_id", docId);
            List<Map<String, Object>> materias = jdbcTemplate.queryForList("SELECT m.nombre as materia, a.horas, a.nivel_pago FROM asignacion a JOIN materia m ON a.materia_id = m.materia_id WHERE a.docente_id = ? AND a.horas > 0 ORDER BY m.nombre", docId);

            Map<String, String> replacements = buildReplacements(docente, materias, folio);
            byte[] docxBytes = modifyZip(TEMPLATE_PATH, replacements);

            String fileName = "Contrato_" + docente.get("nombre").toString().replaceAll("[^a-zA-Z]", "_") + ".docx";
            
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(docxBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    private byte[] modifyZip(String path, Map<String, String> replacements) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(path));
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] bytes = zis.readAllBytes();
                String name = entry.getName();
                
                if (name.endsWith(".xml")) {
                    String content = new String(bytes, StandardCharsets.UTF_8);
                    // Simple replacement for MERGEFIELD pattern
                    // Word XML usually looks like <w:instrText> MERGEFIELD FieldName </w:instrText>
                    // Followed later by <w:t> DisplayText </w:t>
                    for (Map.Entry<String, String> rep : replacements.entrySet()) {
                        String key = rep.getKey();
                        String val = escapeXml(rep.getValue());
                        
                        // Replace simple markers
                        content = content.replace("«" + key + "»", val);
                        content = content.replace("&lt;&lt;" + key + "&gt;&gt;", val);
                        
                        // Replace the text within the next available <w:t> after a MERGEFIELD instruction
                        // This is a simplified approach to avoid complex regex
                        if (content.contains("MERGEFIELD " + key)) {
                            content = replaceMergeField(content, key, val);
                        }
                    }
                    
                    if (name.equals("word/settings.xml")) {
                        content = content.replaceAll("(?s)<w:mailMerge>.*?</w:mailMerge>", "");
                    }
                    bytes = content.getBytes(StandardCharsets.UTF_8);
                }
                
                ZipEntry outEntry = new ZipEntry(name);
                zos.putNextEntry(outEntry);
                zos.write(bytes);
                zos.closeEntry();
                zis.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private String replaceMergeField(String xml, String fieldName, String value) {
        // Find the MERGEFIELD instruction
        String key = "MERGEFIELD " + fieldName;
        int pos = xml.indexOf(key);
        while (pos != -1) {
            // Find the next 'separate' char and 'end' char
            int sep = xml.indexOf("w:fldCharType=\"separate\"", pos);
            int end = xml.indexOf("w:fldCharType=\"end\"", pos);
            
            if (sep != -1 && end != -1 && sep < end) {
                // Find all <w:t> tags between 'separate' and 'end'
                int tStart = xml.indexOf("<w:t", sep);
                if (tStart != -1 && tStart < end) {
                    int tOpenEnd = xml.indexOf(">", tStart) + 1;
                    int tClose = xml.indexOf("</w:t>", tOpenEnd);
                    if (tClose != -1 && tClose <= end) {
                        xml = xml.substring(0, tOpenEnd) + value + xml.substring(tClose);
                        // Update positions for next loop
                        end = xml.indexOf("w:fldCharType=\"end\"", pos);
                    }
                }
            }
            pos = xml.indexOf(key, pos + 1);
        }
        return xml;
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private Map<String, String> buildReplacements(Map<String, Object> doc, List<Map<String, Object>> materias, String folio) {
        Map<String, String> r = new HashMap<>();
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "MX"));
        nf.setMinimumFractionDigits(2); nf.setMaximumFractionDigits(2);

        String gen = val(doc, "genero");
        boolean f = "F".equalsIgnoreCase(gen);
        String reg = val(doc, "regimen_sat");
        if (reg == null || reg.isEmpty()) reg = "SP";

        r.put("NOMBRE", val(doc, "nombre"));
        r.put("RFC", val(doc, "rfc"));
        r.put("CURP", val(doc, "curp"));
        r.put("DOMICILIO", val(doc, "domicilio"));
        r.put("INE_PASAPORTE", val(doc, "credencial_ine"));
        r.put("NO_INE", val(doc, "credencial_ine"));
        r.put("NACIONALIDAD", "Mexicana");
        r.put("FOLIO", folio);

        String grado = val(doc, "grado_acad");
        if ("Licenciatura".equalsIgnoreCase(grado)) grado = f ? "Licenciada" : "Licenciado";
        else if (grado != null && (grado.toLowerCase().contains("maest"))) grado = f ? "Maestra" : "Maestro";
        else if (grado != null && (grado.toLowerCase().contains("doct"))) grado = f ? "Doctora" : "Doctor";
        r.put("GRADO", grado);
        r.put("ellael", f ? "ella" : "el");
        r.put("SEXO", f ? "La" : "El");
        r.put("PRESTADOR1", f ? "a" : "o");
        r.put("AuO", f ? "a" : "o");
        r.put("DEL_DELA", f ? "de la" : "del");
        r.put("Una_Un_prestador", f ? "una" : "un");

        r.put("CARRERA", val(doc, "carrera"));
        
        StringBuilder msb = new StringBuilder();
        BigDecimal th = BigDecimal.ZERO;
        String nivel = "Licenciatura";
        for (Map<String, Object> m : materias) {
            if (msb.length() > 0) msb.append(", ");
            msb.append(m.get("materia"));
            BigDecimal h = (BigDecimal) m.get("horas");
            if (h != null) th = th.add(h);
            if (m.get("nivel_pago") != null) nivel = m.get("nivel_pago").toString();
        }
        r.put("UNIDAD_DE_APENDIZAJE", msb.toString());
        r.put("NIVEL_IMPARTIR", nivel);
        r.put("HORAS", th.stripTrailingZeros().toPlainString());
        r.put("T_HS", th.stripTrailingZeros().toPlainString());
        r.put("HsMes1", th.stripTrailingZeros().toPlainString());
        r.put("HsMes2", th.stripTrailingZeros().toPlainString());

        BigDecimal thora = new BigDecimal("486.71");
        if ("Maestría".equalsIgnoreCase(nivel)) thora = new BigDecimal("851.75");
        else if ("Técnico".equalsIgnoreCase(nivel)) thora = new BigDecimal("231.19");
        r.put("POR_HORA", "$" + nf.format(thora));
        r.put("IMP_HS", "$" + nf.format(thora));

        BigDecimal bruto = th.multiply(thora).setScale(2, RoundingMode.HALF_UP);
        BigDecimal iva = BigDecimal.ZERO, riva = BigDecimal.ZERO, risr;
        if (!"RS".equalsIgnoreCase(reg)) {
            iva = bruto.multiply(new BigDecimal("0.16")).setScale(2, RoundingMode.HALF_UP);
            riva = bruto.multiply(new BigDecimal("0.106667")).setScale(2, RoundingMode.HALF_UP);
        }
        risr = bruto.multiply("RESICO".equalsIgnoreCase(reg) ? new BigDecimal("0.0125") : new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal neto = bruto.add(iva).subtract(riva).subtract(risr);

        for (int i=1; i<=4; i++) {
            r.put("SubtalMes"+i, "$" + nf.format(bruto));
            r.put("IvaMes"+i, "$" + nf.format(iva));
            r.put("RetISRMes"+i, "$" + nf.format(risr));
            r.put("RetIVAMes"+i, "$" + nf.format(riva));
            r.put("NetoMes"+i, "$" + nf.format(neto));
            r.put("ImpBruto_Mes"+i, "$" + nf.format(bruto));
        }

        BigDecimal t4 = new BigDecimal("4");
        r.put("TOTAL_RECIBIR", "$" + nf.format(neto.multiply(t4)));
        r.put("T_SbT", "$" + nf.format(bruto.multiply(t4)));
        r.put("T_IVA", "$" + nf.format(iva.multiply(t4)));
        r.put("T_ISR", "$" + nf.format(risr.multiply(t4)));
        r.put("T_IVA1", "$" + nf.format(riva.multiply(t4)));
        r.put("T_IB", "$" + nf.format(bruto.multiply(t4)));
        r.put("T_IN", "$" + nf.format(neto.multiply(t4)));

        r.put("PERIODO", "Marzo - Junio 2026");
        r.put("FECHA_INICIO", "10 de marzo de 2026");
        r.put("DURACION", "4 meses");
        r.put("LETRA", neto.multiply(t4).toString() + " M.N.");
        return r;
    }

    private String val(Map<String, Object> m, String k) {
        return m.get(k) != null ? m.get(k).toString() : "";
    }
}
