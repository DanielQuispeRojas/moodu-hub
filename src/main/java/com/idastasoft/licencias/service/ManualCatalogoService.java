package com.idastasoft.licencias.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.idastasoft.licencias.model.ModuloCatalogo;
import com.idastasoft.licencias.repository.ModuloCatalogoRepo;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Guia de uso por modulo del catalogo (REQ-AYU-02).
 *
 * <p>El Hub es la fuente de verdad de los manuales de los modulos OFICIALES.
 * El contrato es el MISMO que ya consume el visor del desktop ({@code secciones}
 * con pasos, {@code desarrollo} y {@code faq}), asi que no hay que rehacer
 * AyudaView/ManualDetalle. Las imagenes viajan como data-URL dentro del JSON
 * porque el disco de Render es efimero (igual que ModuloFoto/TemaCatalogo).
 *
 * <p>Reglas: validar antes de escribir (los rechazos no envenenan la
 * transaccion), sembrar solo donde no haya manual (nunca pisar una edicion del
 * admin) y fallar en silencio si el JSON guardado esta corrupto: un manual
 * roto no tumba el catalogo ni el arranque.
 */
@Service
public class ManualCatalogoService {

    /** Tope del JSON ya serializado (incluidas las imagenes en base64). */
    public static final int LIMITE_BYTES = 4 * 1024 * 1024;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RECURSO_FABRICA = "catalogo/manuales-modulos.json";

    private final ModuloCatalogoRepo moduloRepo;

    public ManualCatalogoService(ModuloCatalogoRepo moduloRepo) {
        this.moduloRepo = moduloRepo;
    }

    /** Quita el manual: condensed a null (el desktop cae al manifest del modulo). */
    public String normalizar(String json) {
        if (json == null || json.isBlank()) return null;
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > LIMITE_BYTES) {
            throw new IllegalArgumentException("El manual excede el tamano maximo de "
                    + (LIMITE_BYTES / (1024 * 1024)) + " MB");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("El manual no es JSON valido: " + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("El manual debe ser un objeto JSON");
        }
        validarSecciones(root);
        validarOpcionalTexto(root, "desarrollo", 20_000);
        validarPreguntas(root);
        validarImagenes(root);
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo guardar el manual: " + e.getMessage());
        }
    }

    /**
     * Contrato real (el que ya lee ManualDetalle): secciones[{clave,titulo,
     * items[{titulo,objetivo,pasos[{t,d,img,pie,foco}],consejo}]}].
     * Los pasos llevan `t` (titulo) y `d` (detalle); NO `texto`.
     */
    private void validarSecciones(JsonNode root) {
        JsonNode secciones = root.get("secciones");
        if (secciones == null || !secciones.isArray() || secciones.isEmpty()) {
            throw new IllegalArgumentException("El manual debe tener al menos una seccion");
        }
        int n = 0;
        for (JsonNode seccion : secciones) {
            n++;
            if (!seccion.isObject()) {
                throw new IllegalArgumentException("La seccion " + n + " debe ser un objeto");
            }
            String titulo = texto(seccion.get("titulo"));
            if (titulo.isEmpty()) {
                throw new IllegalArgumentException("La seccion " + n + " no tiene titulo");
            }
            JsonNode items = seccion.get("items");
            if (items == null || !items.isArray() || items.isEmpty()) {
                throw new IllegalArgumentException("La seccion '" + titulo + "' debe tener al menos un item");
            }
            int i = 0;
            for (JsonNode item : items) {
                i++;
                if (!item.isObject() || texto(item.get("titulo")).isEmpty()) {
                    throw new IllegalArgumentException("El item " + i + " de '" + titulo + "' no tiene titulo");
                }
                JsonNode pasos = item.get("pasos");
                if (pasos == null || !pasos.isArray() || pasos.isEmpty()) {
                    throw new IllegalArgumentException("El item '" + texto(item.get("titulo"))
                            + "' debe tener al menos un paso");
                }
                int p = 0;
                for (JsonNode paso : pasos) {
                    p++;
                    if (!paso.isObject() || texto(paso.get("t")).isEmpty()) {
                        throw new IllegalArgumentException("El paso " + p + " del item '"
                                + texto(item.get("titulo")) + "' no tiene titulo (clave 't')");
                    }
                }
            }
        }
    }

    private void validarOpcionalTexto(JsonNode root, String clave, int tope) {
        JsonNode n = root.get(clave);
        if (n == null || n.isNull()) return;
        String s = n.isTextual() ? n.asText() : n.toString();
        if (s.length() > tope) {
            throw new IllegalArgumentException("El campo '" + clave + "' excede " + tope + " caracteres");
        }
    }

    /** FAQ del visor: `preguntas[{p,r}]` (no `faq`). */
    private void validarPreguntas(JsonNode root) {
        JsonNode preguntas = root.get("preguntas");
        if (preguntas == null || preguntas.isNull()) return;
        if (!preguntas.isArray()) throw new IllegalArgumentException("El campo 'preguntas' debe ser una lista");
        int n = 0;
        for (JsonNode item : preguntas) {
            n++;
            if (!item.isObject() || texto(item.get("p")).isEmpty() || texto(item.get("r")).isEmpty()) {
                throw new IllegalArgumentException("La pregunta " + n + " del FAQ debe tener 'p' y 'r'");
            }
        }
    }

    /** Solo data-URLs de imagen: no se aceptan rutas ni javascript:. */
    private void validarImagenes(JsonNode root) {
        java.util.ArrayDeque<JsonNode> pila = new java.util.ArrayDeque<>();
        pila.push(root);
        while (!pila.isEmpty()) {
            JsonNode n = pila.pop();
            if (n.isObject()) {
                for (JsonNode hijo : n) pila.push(hijo);
            } else if (n.isArray()) {
                for (JsonNode hijo : n) pila.push(hijo);
            } else if (n.isTextual()) {
                String s = n.asText().trim();
                if (s.isEmpty()) continue;
                boolean pareceImagen = s.toLowerCase().contains(".svg")
                        || s.toLowerCase().contains(".png") || s.toLowerCase().contains(".jpg")
                        || s.toLowerCase().contains(".jpeg") || s.toLowerCase().contains(".webp")
                        || s.startsWith("data:image/");
                if (!pareceImagen) continue;
                boolean ok = s.matches("(?i)^data:image/(svg\\+xml|png|jpeg|jpg|webp);base64,[A-Za-z0-9+/=]+$");
                if (!ok) {
                    throw new IllegalArgumentException("Las imagenes del manual deben ir como data-URL "
                            + "(PNG/JPEG/WebP/SVG). No se aceptan rutas ni URLs externas");
                }
            }
        }
    }

    private static String texto(JsonNode n) {
        return n == null || n.isNull() ? "" : (n.isTextual() ? n.asText() : n.toString()).trim();
    }

    /** Alias entre la clave canonica del desktop y la clave del catalogo del Hub. */
    private static String claveCatalogo(String clave) {
        if ("operador_caja_chica".equals(clave)) return "caja";
        if ("recepcion-taller-mecanico".equals(clave)) return "recepcion";
        return clave;
    }

    /**
     * Vista publica: nunca devuelve el JSON roto. Si esta corrupto se reporta
     * {@code tieneManual:false} con motivo, y el desktop usa el manifest.
     */
    public Map<String, Object> publico(ModuloCatalogo m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tieneManual", false);
        out.put("manual", null);
        out.put("motivo", null);
        if (m == null || !m.tieneManual()) {
            out.put("motivo", "Este modulo todavia no tiene guia de uso");
            return out;
        }
        try {
            out.put("manual", MAPPER.readTree(m.getManual()));
            out.put("tieneManual", true);
        } catch (Exception e) {
            out.put("motivo", "La guia de uso guardada no es valida");
        }
        return out;
    }

    public Map<String, Object> porClave(String clave) {
        ModuloCatalogo m = clave == null ? null : moduloRepo.findByClave(clave);
        if (m == null || !m.isActivo()) {
            throw new IllegalArgumentException("Modulo no encontrado");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("clave", m.getClave());
        out.put("nombre", m.getNombre());
        out.put("version", m.getVersion());
        out.putAll(publico(m));
        return out;
    }

    /** Guarda el manual de un modulo (el controller ya exigio ADMIN). */
    @Transactional
    public Map<String, Object> guardar(Long id, String json) {
        ModuloCatalogo m = moduloRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Modulo no encontrado"));
        String normalizado = normalizar(json);
        m.setManual(normalizado);
        moduloRepo.saveAndFlush(m);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("clave", m.getClave());
        out.putAll(publico(m));
        return out;
    }

    /**
     * Siembra los manuales de fabrica de los 6 modulos BETA, una sola vez por
     * clave. NUNCA sobrescribe un manual que el admin ya edito (es historial
     * del catalogo). Idempotente: al segundo arranque no hace nada.
     */
    @Transactional
    public int sembrarFabrica() {
        Map<String, String> semilla = leerSemilla();
        if (semilla.isEmpty()) return 0;
        int contados = 0;
        for (Map.Entry<String, String> e : semilla.entrySet()) {
            ModuloCatalogo m = moduloRepo.findByClave(e.getKey());
            if (m == null) m = moduloRepo.findByClave(claveCatalogo(e.getKey()));
            if (m == null) m = moduloRepo.findByClave(claveInversa(e.getKey()));
            if (m == null || m.tieneManual()) continue;
            if (aplicarSemilla(m, e.getValue())) contados++;
        }
        return contados;
    }

    /**
     * Cuando el admin da de alta un modulo oficial despues del arranque, su
     * guia de fabrica se aplica en el mismo acto. Nunca sobrescribe un manual
     * que ya exista (es edicion del admin).
     */
    @Transactional
    public boolean sembrarParaModulo(ModuloCatalogo m) {
        if (m == null || m.tieneManual()) return false;
        Map<String, String> semilla = leerSemilla();
        String json = semilla.get(m.getClave());
        if (json == null) json = semilla.get(claveCatalogo(m.getClave()));
        if (json == null) json = semilla.get(claveInversa(m.getClave()));
        return json != null && aplicarSemilla(m, json);
    }

    private Map<String, String> leerSemilla() {
        Map<String, String> out = new LinkedHashMap<>();
        try (InputStream in = new ClassPathResource(RECURSO_FABRICA).getInputStream()) {
            JsonNode root = MAPPER.readTree(in);
            if (root == null || !root.isObject()) return out;
            root.fields().forEachRemaining(e -> out.put(e.getKey(),
                    e.getValue().isTextual() ? e.getValue().asText() : e.getValue().toString()));
        } catch (Exception e) {
            System.err.println("[Hub] Sin manuales de fabrica (" + RECURSO_FABRICA + "): " + e.getMessage());
        }
        return out;
    }

    private boolean aplicarSemilla(ModuloCatalogo m, String json) {
        try {
            String normalizado = normalizar(json);
            if (normalizado == null) return false;
            m.setManual(normalizado);
            moduloRepo.saveAndFlush(m);
            return true;
        } catch (Exception ex) {
            System.err.println("[Hub] Manual de fabrica '" + m.getClave() + "' omitido: " + ex.getMessage());
            return false;
        }
    }

    /** catalogo 'caja' -> clave canonica 'operador_caja_chica' del JAR. */
    private static String claveInversa(String clave) {
        if ("caja".equals(clave)) return "operador_caja_chica";
        if ("recepcion".equals(clave)) return "recepcion-taller-mecanico";
        return clave;
    }
}
