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

    // ✅ FIX 1: Ruta de plantilla configurable via variable de entorno o propiedad.
    // Agrega en application.properties: contrato.template.path=C:/temp/gestion-docente-web/MAR-AGO26 (2).docx
    // O define la variable de entorno CONTRATO_TEMPLATE_PATH antes de iniciar la app.
    private static final String TEMPLATE_PATH = System.getenv("CONTRATO_TEMPLATE_PATH") != null
            ? System.getenv("CONTRATO_TEMPLATE_PATH")
            : "C:\\temp\\gestion-docente-web\\MAR-AGO26 (2).docx";

    @GetMapping("/contratos")
    public ResponseEntity<List<Map<String, Object>>> getContratos() {
        String sql = "SELECT d.docente_id, d.nombre, d.condicion, d.rfc, d.curp, d.regimen_sat, " +
                "m.nombre as materia, c.emitido_por, c.folio, " +
                "to_char(c.fecha_emision, 'YYYY-MM-DD HH24:MI:SS') as fecha_emision, " +
                "car.siglas as carrera " +
                "FROM docente d " +
                "JOIN asignacion a ON d.docente_id = a.docente_id " +
                "JOIN materia m ON a.materia_id = m.materia_id " +
                "JOIN carrera car ON m.carrera_id = car.carrera_id " +
                "LEFT JOIN contrato_emitido c ON d.docente_id = c.docente_id AND m.materia_id = c.materia_id " +
                "ORDER BY d.nombre ASC, m.nombre ASC";
        return ResponseEntity.ok(jdbcTemplate.queryForList(sql));
    }

    @PostMapping("/generarContrato")
    public ResponseEntity<?> generarContrato(@RequestBody Map<String, Object> payload) {
        System.out.println("Solicitud de contrato recibida: " + payload);
        try {
            // ✅ FIX 2: Validar que docente_id no sea null antes de parsear.
            if (payload.get("docente_id") == null) {
                return ResponseEntity.badRequest()
                        .body(Collections.singletonMap("message", "El campo docente_id es obligatorio."));
            }

            int docId;
            try {
                docId = Integer.parseInt(payload.get("docente_id").toString());
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest()
                        .body(Collections.singletonMap("message", "docente_id inválido: " + payload.get("docente_id")));
            }

            String emitidoPor = payload.get("emitido_por") != null
                    ? payload.get("emitido_por").toString()
                    : "Sistema";

            // ✅ FIX 3: Validar que el docente realmente existe en la BD antes de continuar.
            Integer docenteExiste = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM docente WHERE docente_id = ?", Integer.class, docId);
            if (docenteExiste == null || docenteExiste == 0) {
                return ResponseEntity.badRequest()
                        .body(Collections.singletonMap("message", "No existe ningún docente con ID: " + docId));
            }

            // ✅ NUEVA REGLA: Generar contrato por materia específica
            if (payload.get("materia_id") == null) {
                return ResponseEntity.badRequest()
                        .body(Collections.singletonMap("message", "El campo materia_id es obligatorio."));
            }

            int materiaId;
            try {
                materiaId = Integer.parseInt(payload.get("materia_id").toString());
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest()
                        .body(Collections.singletonMap("message", "materia_id inválido."));
            }

            // Validar seguridad: El jefe de carrera solo puede generar de su propia carrera
            String userRole = payload.get("user_role") != null ? payload.get("user_role").toString() : "";
            if (!"ADM".equalsIgnoreCase(userRole)) {
                Integer carreraMateria = jdbcTemplate.queryForObject(
                        "SELECT m.carrera_id FROM materia m WHERE m.materia_id = ?", Integer.class, materiaId);
                String siglasCarrera = jdbcTemplate.queryForObject(
                        "SELECT siglas FROM carrera WHERE carrera_id = ?", String.class, carreraMateria);
                
                if (siglasCarrera == null || !siglasCarrera.equalsIgnoreCase(userRole)) {
                    return ResponseEntity.status(403)
                            .body(Collections.singletonMap("message", "No tienes permisos para generar contratos de la carrera " + siglasCarrera));
                }
            }

            // Validar si ya existe un contrato para esta materia y docente
            Integer contratoExistente = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM contrato_emitido WHERE docente_id = ? AND materia_id = ?", 
                    Integer.class, docId, materiaId);
            
            if (contratoExistente != null && contratoExistente > 0) {
                return ResponseEntity.badRequest()
                        .body(Collections.singletonMap("message", "Ya se ha generado un contrato para esta materia anteriormente."));
            }

            // Validar regla de las 80 horas
            BigDecimal totalHoras = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(horas), 0) FROM asignacion WHERE docente_id = ?",
                    BigDecimal.class, docId);

            if (totalHoras != null && totalHoras.compareTo(new BigDecimal("80")) > 0) {
                Map<String, String> err = new HashMap<>();
                err.put("message", "Exceso de horas (" + totalHoras + "). El máximo es 80.");
                return ResponseEntity.badRequest().body(err);
            }

            // Validar que la materia está asignada al docente y tiene horas
            BigDecimal horasMateria = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(horas), 0) FROM asignacion WHERE docente_id = ? AND materia_id = ?",
                    BigDecimal.class, docId, materiaId);

            if (horasMateria == null || horasMateria.compareTo(BigDecimal.ZERO) == 0) {
                return ResponseEntity.badRequest()
                        .body(Collections.singletonMap("message",
                                "El docente no tiene horas asignadas en esta materia."));
            }

            // ✅ FIX 5: Verificar que la plantilla .docx existe en disco antes de intentar leerla.
            File templateFile = new File(TEMPLATE_PATH);
            if (!templateFile.exists() || !templateFile.isFile()) {
                return ResponseEntity.internalServerError()
                        .body(Collections.singletonMap("message",
                                "No se encontró la plantilla Word en: " + TEMPLATE_PATH +
                                ". Verifica que el archivo exista o configura la variable CONTRATO_TEMPLATE_PATH."));
            }

            Integer newId = jdbcTemplate.queryForObject(
                    "INSERT INTO contrato_emitido (docente_id, materia_id, emitido_por) VALUES (?, ?, ?) RETURNING id",
                    Integer.class, docId, materiaId, emitidoPor);

            // ✅ FIX 6: Protección contra newId null (fallo silencioso de la BD).
            if (newId == null) {
                return ResponseEntity.internalServerError()
                        .body(Collections.singletonMap("message", "Error al registrar el contrato en la base de datos."));
            }

            String folio = "E.M.Ingría. " + (230 + newId);
            jdbcTemplate.update("UPDATE contrato_emitido SET folio = ? WHERE id = ?", folio, newId);

            Map<String, Object> docente = jdbcTemplate.queryForMap(
                    "SELECT d.nombre, d.rfc, d.curp, d.grado_acad, d.condicion, d.genero, " +
                    "d.domicilio, d.credencial_ine, d.regimen_sat, " +
                    "string_agg(DISTINCT c.siglas, ', ') as carrera " +
                    "FROM docente d " +
                    "LEFT JOIN asignacion a ON d.docente_id = a.docente_id " +
                    "LEFT JOIN materia m ON a.materia_id = m.materia_id " +
                    "LEFT JOIN carrera c ON m.carrera_id = c.carrera_id " +
                    "WHERE d.docente_id = ? GROUP BY d.docente_id",
                    docId);

            // Filtrar SOLO por la materia seleccionada
            List<Map<String, Object>> materias = jdbcTemplate.queryForList(
                    "SELECT m.nombre as materia, a.horas, a.nivel_pago " +
                    "FROM asignacion a JOIN materia m ON a.materia_id = m.materia_id " +
                    "WHERE a.docente_id = ? AND m.materia_id = ? AND a.horas > 0",
                    docId, materiaId);

            Map<String, String> replacements = buildReplacements(docente, materias, folio);
            byte[] docxBytes = modifyZip(TEMPLATE_PATH, replacements);

            // ✅ FIX 7: Nombre de archivo seguro — elimina caracteres problemáticos incluyendo acentos y ñ.
            String nombreLimpio = docente.get("nombre").toString()
                    .replaceAll("[áàäâ]", "a").replaceAll("[ÁÀÄÂ]", "A")
                    .replaceAll("[éèëê]", "e").replaceAll("[ÉÈËÊ]", "E")
                    .replaceAll("[íìïî]", "i").replaceAll("[ÍÌÏÎ]", "I")
                    .replaceAll("[óòöô]", "o").replaceAll("[ÓÒÖÔ]", "O")
                    .replaceAll("[úùüû]", "u").replaceAll("[ÚÙÜÛ]", "U")
                    .replaceAll("[ñ]", "n").replaceAll("[Ñ]", "N")
                    .replaceAll("[^a-zA-Z0-9_\\- ]", "")
                    .replaceAll("\\s+", "_").trim();
            String fileName = "Contrato_" + nombreLimpio + "_" + 
                               (materias.isEmpty() ? "Materia" : materias.get(0).get("materia").toString().replace(" ", "_")) + ".docx";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(docxBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Collections.singletonMap("message", "Error interno: " + e.getMessage()));
        }
    }

    // ✅ FIX 8: modifyZip ahora lee la plantilla desde FileInputStream de forma robusta,
    //    y usa xml:space="preserve" en los <w:t> de reemplazo para no perder espacios.
    private byte[] modifyZip(String path, Map<String, String> replacements) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(path));
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] bytes = zis.readAllBytes();
                String name = entry.getName();

                if (name.endsWith(".xml") || name.endsWith(".rels")) {
                    String content = new String(bytes, StandardCharsets.UTF_8);

                    for (Map.Entry<String, String> rep : replacements.entrySet()) {
                        String key = rep.getKey();
                        String val = escapeXml(rep.getValue());

                        // Reemplazar marcadores directos «campo» (chevrones reales de Word)
                        content = content.replace("\u00AB" + key + "\u00BB", val);
                        // Reemplazar versión escapada en XML &lt;&lt;campo&gt;&gt;
                        content = content.replace("&lt;&lt;" + key + "&gt;&gt;", val);

                        // ✅ FIX 9: Reemplazar MERGEFIELDs — el nombre puede aparecer
                        // fragmentado entre etiquetas XML. Primero normalizamos el XML
                        // para reagrupar los fragmentos del nombre del campo.
                        if (content.contains("MERGEFIELD") && content.contains(key)) {
                            content = replaceMergeField(content, key, val);
                        }
                    }

                    // Limpiar la sección mailMerge del settings.xml para evitar que Word
                    // pida reconectar el origen de datos al abrir el documento.
                    if (name.equals("word/settings.xml")) {
                        content = content.replaceAll("(?s)<w:mailMerge>.*?</w:mailMerge>", "");
                    }

                    bytes = content.getBytes(StandardCharsets.UTF_8);
                }

                // ✅ FIX 10: Crear la ZipEntry sin copiar metadatos del original
                //    (tamaño comprimido, CRC, etc.) para evitar corrupción del ZIP.
                ZipEntry outEntry = new ZipEntry(name);
                zos.putNextEntry(outEntry);
                zos.write(bytes);
                zos.closeEntry();
                zis.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    // ✅ FIX 11: replaceMergeField mejorado.
    //    Problema original: Word fragmenta el XML de los MERGEFIELD en múltiples <w:r> y
    //    <w:t>; el método anterior fallaba cuando el nombre del campo quedaba partido entre
    //    tags. Este método busca la secuencia completa fldChar begin → instrText → separate
    //    → w:t → end y reemplaza TODOS los <w:t> en la zona "display" (entre separate y end).
    private String replaceMergeField(String xml, String fieldName, String value) {
        String searchKey = "MERGEFIELD " + fieldName;
        int searchFrom = 0;

        while (true) {
            int mergePos = xml.indexOf(searchKey, searchFrom);
            if (mergePos == -1) break;

            // Buscar el fldChar "separate" y "end" MÁS CERCANOS después de la instrucción
            int sepPos = xml.indexOf("w:fldCharType=\"separate\"", mergePos);
            int endPos = xml.indexOf("w:fldCharType=\"end\"", mergePos);

            // Protección: si no encontramos los delimitadores, salir del loop
            if (sepPos == -1 || endPos == -1 || sepPos >= endPos) {
                searchFrom = mergePos + 1;
                continue;
            }

            // Reemplazar TODOS los <w:t ...>...</w:t> entre "separate" y "end"
            // (puede haber más de uno si el valor anterior tenía formato)
            StringBuilder zone = new StringBuilder(xml.substring(sepPos, endPos));
            int offset = 0;
            while (true) {
                int tOpen = zone.indexOf("<w:t", offset);
                if (tOpen == -1) break;
                int tOpenEnd = zone.indexOf(">", tOpen);
                if (tOpenEnd == -1) break;
                int tClose = zone.indexOf("</w:t>", tOpenEnd);
                if (tClose == -1) break;

                // Solo reemplazamos el PRIMER <w:t> con el valor real;
                // los siguientes los vaciamos para no duplicar el contenido.
                String replacement = (offset == 0) ? value : "";

                // Asegurar xml:space="preserve" para respetar espacios
                String openTag = zone.substring(tOpen, tOpenEnd + 1);
                if (!openTag.contains("xml:space")) {
                    openTag = openTag.replace("<w:t", "<w:t xml:space=\"preserve\"");
                }

                String before = zone.substring(0, tOpen);
                String after = zone.substring(tClose);
                zone = new StringBuilder(before + openTag + replacement + after);
                offset = before.length() + openTag.length() + replacement.length();
            }

            xml = xml.substring(0, sepPos) + zone + xml.substring(endPos);

            // Avanzar más allá del bloque que acabamos de procesar
            searchFrom = sepPos + zone.length();
        }
        return xml;
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private Map<String, String> buildReplacements(Map<String, Object> doc,
                                                   List<Map<String, Object>> materias,
                                                   String folio) {
        Map<String, String> r = new HashMap<>();
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "MX"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);

        String gen = val(doc, "genero");
        boolean f = "F".equalsIgnoreCase(gen);

        // ✅ FIX 12: Si regimen_sat está vacío o es null, defaultear a "SP"
        //    (Servicios Profesionales) que es el caso más común en docentes por honorarios.
        String reg = val(doc, "regimen_sat");
        if (reg == null || reg.trim().isEmpty()) reg = "SP";

        // --- Datos personales ---
        r.put("NOMBRE", val(doc, "nombre"));
        r.put("RFC", val(doc, "rfc"));
        r.put("CURP", val(doc, "curp"));
        r.put("DOMICILIO", val(doc, "domicilio"));
        r.put("INE_PASAPORTE", val(doc, "credencial_ine"));
        r.put("NO_INE", val(doc, "credencial_ine"));
        r.put("NACIONALIDAD", "Mexicana");
        r.put("FOLIO", folio);

        // --- Grado académico con concordancia de género ---
        String grado = val(doc, "grado_acad");
        if ("Licenciatura".equalsIgnoreCase(grado) || "L".equalsIgnoreCase(grado)) {
            grado = f ? "Licenciada" : "Licenciado";
        } else if (grado != null && grado.toLowerCase().contains("maest")) {
            grado = f ? "Maestra" : "Maestro";
        } else if (grado != null && grado.toLowerCase().contains("doct")) {
            grado = f ? "Doctora" : "Doctor";
        } else if (grado != null && grado.toLowerCase().contains("tec")) {
            grado = f ? "Técnica" : "Técnico";
        }
        r.put("GRADO", grado != null ? grado : "");

        // --- Concordancias de género para el cuerpo del contrato ---
        r.put("ellael", f ? "ella" : "el");
        r.put("SEXO", f ? "La" : "El");
        r.put("PRESTADOR1", f ? "a" : "o");
        r.put("AuO", f ? "a" : "");
        r.put("DEL_DELA", f ? "de la" : "del");
        r.put("Una_Un_prestador", f ? "una" : "un");

        // --- Carrera ---
      //  r.put("CARRERA", val(doc, "carrera"));
        String carreraRaw = val(doc, "carrera");
    StringBuilder carreraFinal = new StringBuilder();

    if (carreraRaw != null) {
     String[] carreras = carreraRaw.split(",");

    for (String c : carreras) {
        String cc = c.trim();
        String nombre = switch (cc) {
            case "ICI" -> "Ingeniero en Computación e Informática";
            case "IC"  -> "Ingeniero Constructor Militar";
            case "ICE" -> "Ingeniero en Comunicaciones y Electrónica";
            case "II"  -> "Ingeniero Industrial";
            default -> cc;
        };

        if (carreraFinal.length() > 0) carreraFinal.append(", ");
        carreraFinal.append(nombre);
    }
}

r.put("CARRERA", carreraFinal.toString());
        // --- Materias y horas ---
        StringBuilder msb = new StringBuilder();
        BigDecimal th = BigDecimal.ZERO;
        String nivel = "Licenciatura"; // nivel por defecto

        for (Map<String, Object> m : materias) {
            if (msb.length() > 0) msb.append(", ");
            msb.append(val(m, "materia"));

            Object horasObj = m.get("horas");
            if (horasObj != null) {
                BigDecimal h = new BigDecimal(horasObj.toString());
                th = th.add(h);
            }
            // El último nivel_pago en la iteración define el tabulador general.
            // Si hay materias mixtas, predominará el último nivel.
            if (m.get("nivel_pago") != null && !m.get("nivel_pago").toString().isEmpty()) {
                nivel = m.get("nivel_pago").toString();
            }
        }

        r.put("UNIDAD_DE_APENDIZAJE", msb.toString());
        r.put("NIVEL_IMPARTIR", nivel);
        r.put("HORAS", th.stripTrailingZeros().toPlainString());
        r.put("T_HS", th.stripTrailingZeros().toPlainString());
        r.put("HsMes1", th.stripTrailingZeros().toPlainString());
        r.put("HsMes2", th.stripTrailingZeros().toPlainString());

        // --- Tabuladores UDEFA ---
        BigDecimal thora;
        switch (nivel.toLowerCase()) {
            case "maestría":
            case "maestria":
                thora = new BigDecimal("851.75");
                break;
            case "técnico":
            case "tecnico":
                thora = new BigDecimal("231.19");
                break;
            default: // Licenciatura
                thora = new BigDecimal("486.71");
                break;
        }
        r.put("POR_HORA", "$" + nf.format(thora));
        r.put("IMP_HS", "$" + nf.format(thora));

        // --- Cálculos fiscales ---
        BigDecimal bruto = th.multiply(thora).setScale(2, RoundingMode.HALF_UP);
        BigDecimal iva   = BigDecimal.ZERO;
        BigDecimal riva  = BigDecimal.ZERO;
        BigDecimal risr;

        // ✅ FIX 13: Lógica fiscal corregida con los tres regímenes SAT:
        //   SP  = Servicios Profesionales (honorarios) → genera IVA 16%, retención IVA 2/3, ISR 10%
        //   RESICO = Régimen Simplificado de Confianza → NO genera IVA, ISR 1.25%
        //   RS  = Sueldos y Salarios (asimilados) → NO genera IVA, ISR 10%
        boolean esServiciosProfesionales = !"RS".equalsIgnoreCase(reg) && !"RESICO".equalsIgnoreCase(reg);

        if (esServiciosProfesionales) {
            iva  = bruto.multiply(new BigDecimal("0.16")).setScale(2, RoundingMode.HALF_UP);
            riva = bruto.multiply(new BigDecimal("0.106667")).setScale(2, RoundingMode.HALF_UP);
        }
        // RESICO: tasa reducida 1.25%; SP y RS: 10%
        BigDecimal tasaIsr = "RESICO".equalsIgnoreCase(reg)
                ? new BigDecimal("0.0125")
                : new BigDecimal("0.10");
        risr = bruto.multiply(tasaIsr).setScale(2, RoundingMode.HALF_UP);

        BigDecimal neto = bruto.add(iva).subtract(riva).subtract(risr);

        // Campos por mes (4 meses de contrato)
        for (int i = 1; i <= 4; i++) {
            r.put("SubtalMes" + i,  "$" + nf.format(bruto));
            r.put("IvaMes" + i,     "$" + nf.format(iva));
            r.put("RetISRMes" + i,  "$" + nf.format(risr));
            r.put("RetIVAMes" + i,  "$" + nf.format(riva));
            r.put("NetoMes" + i,    "$" + nf.format(neto));
            r.put("ImpBruto_Mes" + i, "$" + nf.format(bruto));
        }

        // Totales 4 meses
        BigDecimal t4 = new BigDecimal("4");
        r.put("TOTAL_RECIBIR", "$" + nf.format(neto.multiply(t4)));
        r.put("T_SbT",  "$" + nf.format(bruto.multiply(t4)));
        r.put("T_IVA",  "$" + nf.format(iva.multiply(t4)));
        r.put("T_ISR",  "$" + nf.format(risr.multiply(t4)));
        r.put("T_IVA1", "$" + nf.format(riva.multiply(t4)));
        r.put("T_IB",   "$" + nf.format(bruto.multiply(t4)));
        r.put("T_IN",   "$" + nf.format(neto.multiply(t4)));

        // ✅ FIX 14: LETRA con formato legible (ej. "$ 15,234.56 M.N.") en vez del toPlainString crudo.
        r.put("LETRA", "$ " + nf.format(neto.multiply(t4)) + " M.N.");

        // --- Datos del periodo contractual ---
        r.put("PERIODO", "Marzo - Junio 2026");
        r.put("FECHA_INICIO", "10 de marzo de 2026");
        r.put("DURACION", "4 meses");

        // ✅ FIX 15: Exponer también el régimen SAT en el documento por si la plantilla lo usa.
        r.put("REGIMEN_SAT", reg);

        return r;
    }

    // ✅ FIX 16: val() acepta tanto Map<String,Object> como cualquier Map para evitar
    //    unchecked cast en las llamadas con materias.
    private String val(Map<?, Object> m, String k) {
        Object v = m.get(k);
        return (v != null) ? v.toString().trim() : "";
    }
}
