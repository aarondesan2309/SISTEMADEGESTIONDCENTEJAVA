package com.emi.gestiondocente;

import com.emi.gestiondocente.config.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.regex.*;
import java.util.zip.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ContratoController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${sgdc.storage.contrato-template}")
    private String templatePath;

    @GetMapping("/contratos")
    public ResponseEntity<List<Map<String, Object>>> getContratos() {
        String sql = "SELECT d.docente_id, d.nombre, d.condicion, d.rfc, d.curp, d.regimen_sat, " +
                "m.materia_id, m.nombre as materia, c.id as contrato_id, c.emitido_por, c.folio, " +
                "to_char(c.fecha_emision, 'YYYY-MM-DD HH24:MI:SS') as fecha_emision, " +
                "car.siglas as carrera, " +
                "COALESCE(a.horas, 0) as horas, " +
                "COALESCE(a.horas_m1, 0) as horas_m1, COALESCE(a.horas_m2, 0) as horas_m2, " +
                "COALESCE(a.horas_m3, 0) as horas_m3, COALESCE(a.horas_m4, 0) as horas_m4, " +
                "COALESCE(a.nivel_pago, 'Licenciatura') as nivel_pago " +
                "FROM docente d " +
                "JOIN asignacion a ON d.docente_id = a.docente_id " +
                "JOIN materia m ON a.materia_id = m.materia_id " +
                "JOIN carrera car ON m.carrera_id = car.carrera_id " +
                "LEFT JOIN contrato_emitido c ON d.docente_id = c.docente_id AND m.materia_id = c.materia_id " +
                "ORDER BY d.nombre ASC, m.nombre ASC";
        return ResponseEntity.ok(jdbcTemplate.queryForList(sql));
    }

    @PostMapping("/anularContrato")
    public ResponseEntity<?> anularContrato(@RequestBody Map<String, Object> payload) {
        try {
            Integer docId = (Integer) payload.get("docente_id");
            Integer materiaId = (Integer) payload.get("materia_id");
            String userRole = (String) payload.get("user_role");

            if (docId == null || materiaId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "docente_id y materia_id son obligatorios"));
            }

            // Validar permiso de carrera (opcional pero recomendado)
            if (userRole != null && !userRole.equalsIgnoreCase("ADM") && !userRole.equalsIgnoreCase("DIRECTOR")) {
                String siglasCarrera = jdbcTemplate.queryForObject(
                        "SELECT c.siglas FROM materia m JOIN carrera c ON m.carrera_id = c.carrera_id WHERE m.materia_id = ?",
                        String.class, materiaId);
                if (siglasCarrera != null && !siglasCarrera.equalsIgnoreCase(userRole)) {
                    return ResponseEntity.status(403).body(Map.of("message", "No tienes permiso para anular contratos de la carrera " + siglasCarrera));
                }
            }

            jdbcTemplate.update("DELETE FROM contrato_emitido WHERE docente_id = ? AND materia_id = ?", docId, materiaId);
            
            return ResponseEntity.ok(Map.of("message", "Contrato anulado exitosamente. Ya puedes generarlo de nuevo."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Error al anular contrato: " + e.getMessage()));
        }
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
                            .body(Collections.singletonMap("message",
                                    "No tienes permisos para generar contratos de la carrera " + siglasCarrera));
                }
            }

            boolean reprint = Boolean.TRUE.equals(payload.get("reprint"));

            // Validar si ya existe un contrato para esta materia y docente
            List<Map<String, Object>> existentes = jdbcTemplate.queryForList(
                    "SELECT id, folio FROM contrato_emitido WHERE docente_id = ? AND materia_id = ?",
                    docId, materiaId);

            if (!existentes.isEmpty() && !reprint) {
                return ResponseEntity.badRequest()
                        .body(Collections.singletonMap("message",
                                "Ya se ha generado un contrato para esta materia anteriormente."));
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

            // Resolver plantilla: primero intenta plantilla_EMI.docx (por tenant),
            // si no existe usa la plantilla por defecto configurada en properties.
            String tenant = TenantContext.getCurrentTenant();
            String actualTemplatePath = templatePath;
            if (tenant != null && !tenant.isBlank()) {
                String suffix = tenant.replaceFirst("^gestion_docente_", "").toUpperCase();
                String specificPath = templatePath.replace(".docx", "_" + suffix + ".docx");
                File specificFile = new File(specificPath);
                if (specificFile.exists() && specificFile.isFile()) {
                    actualTemplatePath = specificPath;
                }
            }

            File templateFile = new File(actualTemplatePath);
            if (!templateFile.exists() || !templateFile.isFile()) {
                return ResponseEntity.internalServerError()
                        .body(Collections.singletonMap("message",
                                "No se encontró la plantilla Word en: " + actualTemplatePath +
                                        ". Verifica la propiedad sgdc.storage.contrato-template en application.properties."));
            }

            int newId;
            String folio;
            if (reprint && !existentes.isEmpty()) {
                newId = ((Number) existentes.get(0).get("id")).intValue();
                folio = String.valueOf(existentes.get(0).get("folio"));
            } else {
                newId = jdbcTemplate.queryForObject(
                        "INSERT INTO contrato_emitido (docente_id, materia_id, emitido_por) VALUES (?, ?, ?) RETURNING id",
                        Integer.class, docId, materiaId, emitidoPor);
                folio = "E.M.Ingría. " + (230 + newId);
                jdbcTemplate.update("UPDATE contrato_emitido SET folio = ? WHERE id = ?", folio, newId);
            }

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

            // Filtrar SOLO por la materia seleccionada — LIMIT 1 evita duplicados si hay
            // múltiples filas de asignacion para el mismo (docente, materia)
            List<Map<String, Object>> materias = jdbcTemplate.queryForList(
                    "SELECT m.nombre as materia, a.horas, a.horas_m1, a.horas_m2, a.horas_m3, a.horas_m4, " +
                            "a.nivel_pago, a.ciclo_id " +
                            "FROM asignacion a JOIN materia m ON a.materia_id = m.materia_id " +
                            "WHERE a.docente_id = ? AND m.materia_id = ? AND a.horas > 0 " +
                            "ORDER BY a.horas DESC LIMIT 1",
                    docId, materiaId);

            // Cargar el ciclo académico de la asignación (si tiene). Si no hay ciclo, se usa
            // valores hardcoded como fallback (compatibilidad pre-migración).
            Map<String, Object> ciclo = null;
            if (!materias.isEmpty() && materias.get(0).get("ciclo_id") != null) {
                Integer cicloId = ((Number) materias.get(0).get("ciclo_id")).intValue();
                List<Map<String, Object>> ciclos = jdbcTemplate.queryForList(
                        "SELECT * FROM ciclo_academico WHERE id = ?", cicloId);
                if (!ciclos.isEmpty()) ciclo = ciclos.get(0);
            }

            Map<String, String> replacements = buildReplacements(docente, materias, folio, ciclo);
            byte[] docxBytes = modifyZip(actualTemplatePath, replacements);

            // ✅ FIX 7: Nombre de archivo seguro — elimina caracteres problemáticos
            // incluyendo acentos y ñ.
            String nombreLimpio = docente.get("nombre").toString()
                    .replaceAll("[^a-zA-Z0-9_\\- ]", "")
                    .replaceAll("[^a-zA-Z0-9_\\- ]", "")
                    .replaceAll("\\s+", "_").trim();
            String fileName = "Contrato_" + nombreLimpio + "_" +
                    (materias.isEmpty() ? "Materia" : materias.get(0).get("materia").toString().replace(" ", "_"))
                    + ".docx";

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

    // ✅ FIX 8: modifyZip ahora lee la plantilla desde FileInputStream de forma
    // robusta,
    // y usa xml:space="preserve" en los <w:t> de reemplazo para no perder espacios.
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

                    // Normalizar «CAMPO» fragmentados por spell-check de Word
                    // Word separa «X» en tres runs: <w:t>«</w:t> + <w:proofErr/> + <w:t>X</w:t> + <w:t>»</w:t>
                    content = content.replaceAll("<w:proofErr[^>]*/?>", "");
                    content = Pattern.compile(
                        "<w:t>«</w:t></w:r><w:r[^>]*>(?:<w:rPr>.*?</w:rPr>)?<w:t>([\\w]+)</w:t></w:r><w:r[^>]*>(?:<w:rPr>.*?</w:rPr>)?<w:t>»</w:t></w:r>",
                        Pattern.DOTALL
                    ).matcher(content).replaceAll("<w:t>«$1»</w:t></w:r>");

                    for (Map.Entry<String, String> rep : replacements.entrySet()) {
                        String key = rep.getKey();
                        String val = escapeXml(rep.getValue());

                        // Reemplazar marcadores directos «campo» (chevrones reales de Word)
                        content = content.replace("\u00AB" + key + "\u00BB", val);
                        // Reemplazar versión escapada en XML &lt;&lt;campo&gt;&gt;
                        content = content.replace("&lt;&lt;" + key + "&gt;&gt;", val);
                    }

                    // ✅ FIX 9: Reemplazar MERGEFIELDs
                    if (name.endsWith(".xml")) {
                        String xml = content; // Usar el content ya procesado por chevrones
                        for (Map.Entry<String, String> entryReplacement : replacements.entrySet()) {
                            xml = replaceMergeField(xml, entryReplacement.getKey(), entryReplacement.getValue());
                        }

                        // Limpiar mailMerge en settings.xml
                        if (name.equals("word/settings.xml")) {
                            xml = xml.replaceAll("(?s)<w:mailMerge>.*?</w:mailMerge>", "");
                        }

                        // ✅ CORRECCIÓN: Eliminar typos heredados de la plantilla
                        xml = xml.replace("Prestadoro", "Prestador")
                                 .replace("prestadoro", "prestador")
                                 .replace("Quarta", "Cuarta")
                                 .replace("quarta", "cuarta");

                        content = xml;
                    }
                    bytes = content.getBytes(StandardCharsets.UTF_8);
                }

                // ✅ FIX 10: Crear la ZipEntry sin copiar metadatos del original
                ZipEntry outEntry = new ZipEntry(name);
                zos.putNextEntry(outEntry);
                zos.write(bytes);
                zos.closeEntry();
                zis.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    // Reemplaza un MERGEFIELD de Word en el XML del docx.
    // Estrategia robusta: itera por bloques fldChar-begin...fldChar-end,
    // concatena todos los <w:instrText> del bloque (maneja fragmentación de Word)
    // y si coincide con el campo buscado, reemplaza el bloque completo desde
    // el <w:r> que contiene el begin hasta el </w:r> que cierra el end.
    // Produce XML válido: <w:r><w:t xml:space="preserve">valor</w:t></w:r>.
    private String replaceMergeField(String xml, String fieldName, String value) {
        String valEscaped = escapeXml(value);
        Pattern instrPat  = Pattern.compile("<w:instrText[^>]*>(.*?)</w:instrText>", Pattern.DOTALL);
        Pattern fieldPat  = Pattern.compile("(?i)\\bMERGEFIELD\\s+" + Pattern.quote(fieldName) + "(?![\\w])");
        int searchFrom = 0;

        while (true) {
            // Localizar siguiente bloque begin
            int bPos = xml.indexOf("fldCharType=\"begin\"", searchFrom);
            if (bPos == -1) break;

            // Localizar el end correspondiente
            int ePos = xml.indexOf("fldCharType=\"end\"", bPos + 1);
            if (ePos == -1) break;

            // Concatenar todo el instrText del bloque (maneja fragmentación)
            String segment = xml.substring(bPos, ePos);
            StringBuilder instr = new StringBuilder();
            Matcher im = instrPat.matcher(segment);
            while (im.find()) instr.append(im.group(1));

            if (!fieldPat.matcher(instr).find()) {
                searchFrom = ePos + 1;
                continue;
            }

            // Encontrar el <w:r> que CONTIENE el begin fldChar buscando hacia atrás
            int runStart = -1;
            int scan = bPos;
            while (scan > 0) {
                int rr = Math.max(xml.lastIndexOf("<w:r>", scan - 1),
                                  xml.lastIndexOf("<w:r ", scan - 1));
                if (rr < 0) break;
                // Verificar que no hay </w:r> entre rr y bPos (estaría cerrado antes)
                if (!xml.substring(rr, bPos).contains("</w:r>")) {
                    runStart = rr;
                    break;
                }
                scan = rr;
            }
            if (runStart == -1) { searchFrom = bPos + 1; continue; }

            // Encontrar </w:r> que cierra después del end fldChar
            int runEndClose = xml.indexOf("</w:r>", ePos);
            if (runEndClose == -1) { searchFrom = bPos + 1; continue; }
            int runEnd = runEndClose + 6;   // longitud de "</w:r>"

            xml = xml.substring(0, runStart)
                    + "<w:r><w:t xml:space=\"preserve\">" + valEscaped + "</w:t></w:r>"
                    + xml.substring(runEnd);

            searchFrom = runStart;
        }
        return xml;
    }

    private String escapeXml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private Map<String, String> buildReplacements(Map<String, Object> doc,
            List<Map<String, Object>> materias,
            String folio,
            Map<String, Object> ciclo) {
        Map<String, String> r = new HashMap<>();
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "MX"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);

        String gen = val(doc, "genero");
        boolean f = "F".equalsIgnoreCase(gen);

        // ✅ FIX 12: Si regimen_sat está vacío o es null, defaultear a "SP"
        // (Servicios Profesionales) que es el caso más común en docentes por
        // honorarios.
        String reg = val(doc, "regimen_sat");
        if (reg == null || reg.trim().isEmpty())
            reg = "SP";

        // --- Datos personales ---
        r.put("NOMBRE", val(doc, "nombre"));
        r.put("RFC", val(doc, "rfc"));
        r.put("CURP", val(doc, "curp"));
        r.put("DOMICILIO", val(doc, "domicilio"));
        r.put("INE_PASAPORTE", "Credencial para Votar No.");
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
        r.put("PRESTADOR1", f ? "a" : "");
        r.put("AuO", f ? "a" : "");
        r.put("AuQ", f ? "a" : "");
        r.put("DEL_DELA", f ? "de la" : "del");
        r.put("Una_Un_prestador", f ? "una" : "un");

        // --- Carrera ---
        // r.put("CARRERA", val(doc, "carrera"));
        String carreraRaw = val(doc, "carrera");
        StringBuilder carreraFinal = new StringBuilder();

        if (carreraRaw != null) {
            String[] carreras = carreraRaw.split(",");

            for (String c : carreras) {
                String cc = c.trim();
                String nombre = switch (cc) {
                    case "ICI"    -> "Ingeniero en Computación e Informática";
                    case "IC"     -> "Ingeniero Constructor Militar";
                    case "ICE"    -> "Ingeniero en Comunicaciones y Electrónica";
                    case "II"     -> "Ingeniero Industrial";
                    case "TC"     -> "Tronco Común";
                    default       -> cc;
                };

                if (carreraFinal.length() > 0)
                    carreraFinal.append(", ");
                carreraFinal.append(nombre);
            }
        }

        r.put("CARRERA", carreraFinal.toString());
        // --- Materias y horas ---
        StringBuilder msb = new StringBuilder();
        BigDecimal th = BigDecimal.ZERO;
        BigDecimal[] hMeses = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        String nivel = "Licenciatura"; // nivel por defecto

        for (Map<String, Object> m : materias) {
            if (msb.length() > 0)
                msb.append(", ");
            msb.append(val(m, "materia"));

            Object horasObj = m.get("horas");
            if (horasObj != null) th = th.add(new BigDecimal(horasObj.toString()));
            
            if (m.get("horas_m1") != null) hMeses[0] = hMeses[0].add(new BigDecimal(m.get("horas_m1").toString()));
            if (m.get("horas_m2") != null) hMeses[1] = hMeses[1].add(new BigDecimal(m.get("horas_m2").toString()));
            if (m.get("horas_m3") != null) hMeses[2] = hMeses[2].add(new BigDecimal(m.get("horas_m3").toString()));
            if (m.get("horas_m4") != null) hMeses[3] = hMeses[3].add(new BigDecimal(m.get("horas_m4").toString()));

            // El último nivel_pago en la iteración define el tabulador general.
            if (m.get("nivel_pago") != null && !m.get("nivel_pago").toString().isEmpty()) {
                nivel = m.get("nivel_pago").toString();
            }
        }

        r.put("UNIDAD_DE_APENDIZAJE", msb.toString());
        r.put("NIVEL_IMPARTIR", nivel);

        // Fallback 1: sin desglose mensual (todos en 0) → distribuir total entre 4 meses
        final BigDecimal thFinal = th;
        boolean sinDesglose = java.util.Arrays.stream(hMeses).allMatch(h -> h.compareTo(BigDecimal.ZERO) == 0);
        // Fallback 2: patrón viejo (cada mes = total, bug histórico) → dividir por 4
        boolean patronViejo = !sinDesglose && thFinal.compareTo(BigDecimal.ZERO) > 0
                && java.util.Arrays.stream(hMeses).allMatch(h -> h.compareTo(thFinal) == 0);
        if ((sinDesglose || patronViejo) && thFinal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal porMes = thFinal.divide(new BigDecimal("4"), 2, RoundingMode.HALF_UP);
            for (int i = 0; i < 4; i++) hMeses[i] = porMes;
        }

        // --- Tabuladores: intentar leer del ciclo, fallback a hardcoded ---
        BigDecimal thora = null;
        if (ciclo != null) {
            Integer cicloId = ((Number) ciclo.get("id")).intValue();
            List<Map<String, Object>> tabs = jdbcTemplate.queryForList(
                    "SELECT monto_por_hora FROM tabulador_pago WHERE ciclo_id = ? AND LOWER(nivel) = LOWER(?)",
                    cicloId, nivel);
            if (!tabs.isEmpty()) {
                thora = new BigDecimal(tabs.get(0).get("monto_por_hora").toString());
            }
        }
        if (thora == null) {
            // Fallback a tabuladores hardcoded (compatibilidad pre-migración)
            switch (nivel.toLowerCase()) {
                case "doctorado": thora = new BigDecimal("1048.95"); break;
                case "maestría":
                case "maestria":  thora = new BigDecimal("734.27"); break;
                case "técnico":
                case "tecnico":   thora = new BigDecimal("199.30"); break;
                default:          thora = new BigDecimal("419.58"); break;
            }
        }
        r.put("POR_HORA", nf.format(thora));
        r.put("IMP_HS", "$" + nf.format(thora));
        r.put("LETRA_HORA", amountToWords(thora));

        // --- Cálculos fiscales ---
        boolean aplicaIvaRiva = !"RS".equalsIgnoreCase(reg);
        BigDecimal tasaIva = new BigDecimal("0.16");
        BigDecimal tasaRiva = new BigDecimal("0.106667");
        BigDecimal tasaIsr = "RESICO".equalsIgnoreCase(reg) ? new BigDecimal("0.0125") : new BigDecimal("0.10");

        BigDecimal brutoTotal = BigDecimal.ZERO;
        BigDecimal ivaTotal = BigDecimal.ZERO;
        BigDecimal risrTotal = BigDecimal.ZERO;
        BigDecimal rivaTotal = BigDecimal.ZERO;
        BigDecimal netoTotal = BigDecimal.ZERO;
        BigDecimal subtotalTotal = BigDecimal.ZERO;

        for (int i = 0; i < 4; i++) {
            BigDecimal hm = hMeses[i];
            int ms = i + 1;
            
            BigDecimal brutoM = hm.multiply(thora).setScale(2, RoundingMode.HALF_UP);
            BigDecimal ivaM = aplicaIvaRiva ? brutoM.multiply(tasaIva).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal rivaM = aplicaIvaRiva ? brutoM.multiply(tasaRiva).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal risrM = brutoM.multiply(tasaIsr).setScale(2, RoundingMode.HALF_UP);
            BigDecimal netoM = brutoM.add(ivaM).subtract(rivaM).subtract(risrM);
            BigDecimal subtotalM = brutoM.add(ivaM);

            r.put("HsMes" + ms, hm.stripTrailingZeros().toPlainString());
            r.put("ImpBruto_Mes" + ms, "$" + nf.format(brutoM));
            r.put("IvaMes" + ms, "$" + nf.format(ivaM));
            r.put("SubtalMes" + ms, "$" + nf.format(subtotalM));
            r.put("RetISRMes" + ms, "$" + nf.format(risrM));
            r.put("RetIVAMes" + ms, "$" + nf.format(rivaM));
            r.put("NetoMes" + ms, "$" + nf.format(netoM));

            brutoTotal = brutoTotal.add(brutoM);
            ivaTotal = ivaTotal.add(ivaM);
            risrTotal = risrTotal.add(risrM);
            rivaTotal = rivaTotal.add(rivaM);
            netoTotal = netoTotal.add(netoM);
            subtotalTotal = subtotalTotal.add(subtotalM);
        }

        r.put("T_HS", th.stripTrailingZeros().toPlainString() + " Hrs. Total");
        r.put("HORAS", th.stripTrailingZeros().toPlainString());
        r.put("T_IB", "$" + nf.format(brutoTotal));
        r.put("T_SUB", "$" + nf.format(brutoTotal));
        r.put("T_IVA", "$" + nf.format(ivaTotal));
        r.put("T_SbT", "$" + nf.format(subtotalTotal));
        r.put("T_RISR", "$" + nf.format(risrTotal));
        r.put("T_ISR", "$" + nf.format(risrTotal));
        r.put("T_RIVA", "$" + nf.format(rivaTotal));
        r.put("T_IVA1", "$" + nf.format(rivaTotal));
        r.put("T_NET", "$" + nf.format(netoTotal));
        r.put("T_IN", "$" + nf.format(netoTotal));

        // Otros alias comunes
        r.put("IMP_BRUTO", "$" + nf.format(brutoTotal));
        r.put("IMP_NETO", "$" + nf.format(netoTotal));
        r.put("TOTAL_RECIBIR", nf.format(netoTotal));  // template tiene "$" literal antes del campo
        r.put("LETRA", amountToWords(netoTotal));       // monto en palabras

        // --- Datos del periodo contractual (del ciclo activo, con fallback hardcoded) ---
        if (ciclo != null) {
            r.put("PERIODO",      val(ciclo, "periodo_txt"));
            r.put("FECHA_INICIO", formatFechaLarga(ciclo.get("fecha_contrato")));
            r.put("DURACION",     val(ciclo, "duracion_txt"));
            r.put("MES1_NOMBRE",  val(ciclo, "mes1_nombre"));
            r.put("MES2_NOMBRE",  val(ciclo, "mes2_nombre"));
            r.put("MES3_NOMBRE",  val(ciclo, "mes3_nombre"));
            r.put("MES4_NOMBRE",  val(ciclo, "mes4_nombre"));
        } else {
            r.put("PERIODO",      "Marzo - Junio 2026");
            r.put("FECHA_INICIO", "10 de marzo de 2026");
            r.put("DURACION",     "4 meses");
            r.put("MES1_NOMBRE",  "Marzo 2026");
            r.put("MES2_NOMBRE",  "Abril 2026");
            r.put("MES3_NOMBRE",  "Mayo 2026");
            r.put("MES4_NOMBRE",  "Junio 2026");
        }

        // ✅ FIX 15: Exponer también el régimen SAT en el documento por si la plantilla
        // lo usa.
        r.put("REGIMEN_SAT", reg);

        return r;
    }

    private String val(Map<?, Object> m, String k) {
        Object v = m.get(k);
        return (v != null) ? v.toString().trim() : "";
    }

    // Formatea una fecha (java.sql.Date / java.time.LocalDate / String) como
    // "DD de <mes> de YYYY" en español. Usado para la cláusula del contrato.
    private String formatFechaLarga(Object fecha) {
        if (fecha == null) return "";
        java.time.LocalDate d;
        if (fecha instanceof java.time.LocalDate) {
            d = (java.time.LocalDate) fecha;
        } else if (fecha instanceof java.sql.Date) {
            d = ((java.sql.Date) fecha).toLocalDate();
        } else {
            try { d = java.time.LocalDate.parse(fecha.toString()); }
            catch (Exception e) { return fecha.toString(); }
        }
        String[] meses = {"enero","febrero","marzo","abril","mayo","junio",
                          "julio","agosto","septiembre","octubre","noviembre","diciembre"};
        return d.getDayOfMonth() + " de " + meses[d.getMonthValue() - 1] + " de " + d.getYear();
    }

    private String amountToWords(BigDecimal amount) {
        long pesos = amount.longValue();
        int centavos = amount.subtract(new BigDecimal(pesos))
            .multiply(new BigDecimal("100"))
            .setScale(0, RoundingMode.HALF_UP).intValue();
        return "(" + toSpanishWords(pesos) + " Pesos " + String.format("%02d", centavos) + "/100 M.N.)";
    }

    private static final String[] SW_UNITS = {
        "", "Un", "Dos", "Tres", "Cuatro", "Cinco", "Seis", "Siete", "Ocho", "Nueve",
        "Diez", "Once", "Doce", "Trece", "Catorce", "Quince", "Dieciséis", "Diecisiete",
        "Dieciocho", "Diecinueve", "Veinte", "Veintiún", "Veintidós", "Veintitrés",
        "Veinticuatro", "Veinticinco", "Veintiséis", "Veintisiete", "Veintiocho", "Veintinueve"
    };
    private static final String[] SW_TENS = {
        "", "Diez", "Veinte", "Treinta", "Cuarenta", "Cincuenta", "Sesenta", "Setenta", "Ochenta", "Noventa"
    };
    private static final String[] SW_HUNDS = {
        "", "Cien", "Doscientos", "Trescientos", "Cuatrocientos", "Quinientos",
        "Seiscientos", "Setecientos", "Ochocientos", "Novecientos"
    };

    private String toSpanishWords(long n) {
        if (n == 0) return "Cero";
        StringBuilder sb = new StringBuilder();
        if (n >= 1000000) {
            long m = n / 1000000; n %= 1000000;
            sb.append(m == 1 ? "Un Millón" : toSpanishWords(m) + " Millones");
            if (n > 0) sb.append(" ");
        }
        if (n >= 1000) {
            long t = n / 1000; n %= 1000;
            sb.append(t == 1 ? "Mil" : toSpanishWords(t) + " Mil");
            if (n > 0) sb.append(" ");
        }
        if (n >= 100) {
            int h = (int)(n / 100); n %= 100;
            sb.append(h == 1 && n > 0 ? "Ciento" : SW_HUNDS[h]);
            if (n > 0) sb.append(" ");
        }
        if (n > 0) {
            if (n < 30) {
                sb.append(SW_UNITS[(int)n]);
            } else {
                int t = (int)(n / 10), u = (int)(n % 10);
                sb.append(SW_TENS[t]);
                if (u > 0) sb.append(" y ").append(SW_UNITS[u]);
            }
        }
        return sb.toString().trim();
    }
}
