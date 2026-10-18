package com.idastasoft.licencias.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.idastasoft.licencias.model.TemaCatalogo;
import com.idastasoft.licencias.repository.TemaCatalogoRepo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catalogo de themes de MOODU del Hub.
 *
 * El Hub es la fuente de verdad de los themes publicables (los de MOODU y los
 * que suban terceros): guarda el .json YA VALIDADO y NORMALIZADO con el mismo
 * contrato que el validador del desktop (src/utils/tema.js -> validarTemaImportado),
 * de modo que un theme guardado aqui siempre se puede aplicar y descargar.
 *
 * Contrato validado (espejo de validarTemaImportado):
 *  - base: "light" | "dark"
 *  - vars: los 8 colores base en hex (#rrggbb): primary, primaryHover,
 *    bgBody, bgSurface, textPrimary, success, warning, danger
 *  - fontUi / fontMono: opcionales, de listas cerradas
 *  - fondos: opcional (transparencia 0-100, blur/redondeado/fondoModular,
 *    y por panel general/izquierdo/derecho/central/footer: tema|color|imagen)
 *  - nav: opcional (alineacion, efectoMac, footer, footerVisible, posicion)
 */
@Service
public class TemaCatalogoService {

    /** Grupos visuales publicados (informativos; el desktop los muestra como etiqueta). */
    public static final List<String> GRUPOS = List.of("claros", "intensos", "oscuros", "pastel");

    /** Claves de color obligatorias (mismas que VARS_BASE del desktop). */
    private static final List<String> VARS = List.of(
            "primary", "primaryHover", "bgBody", "bgSurface", "textPrimary", "success", "warning", "danger");

    private static final List<String> FUENTES_UI = List.of(
            "inter", "roboto", "open-sans", "lato", "montserrat", "nunito", "sistema");
    private static final List<String> FUENTES_MONO = List.of("jetbrains", "fira", "consolas");

    private static final List<String> PANELES = List.of("general", "izquierdo", "derecho", "central", "footer");

    private static final List<String> MUNDOS = List.of("tema", "color", "imagen");
    private static final List<String> ALINEACIONES = List.of("izquierda", "centrada", "derecha");
    private static final List<String> FOOTERS = List.of("default", "centrado");
    private static final List<String> POSICIONES = List.of("arriba", "izquierda", "derecha");

    /** Tope del archivo: 1.2 MB admite un fondo en imagen (~900 KB en dataURL). */
    private static final int MAX_CHARS = 1_200_000;

    /**
     * Los 8 presets de fabrica de MOODU (mismos nombres, grupos y colores que
     * PRESETS en src/utils/tema.js del desktop).
     * nombre, clave, grupo, base, primary, primaryHover, bgBody, bgSurface,
     * textPrimary, success, warning, danger.
     */
    private static final String[][] FABRICA = {
            {"Azul MOODU", "azul-moodu", "claros", "light", "#2563eb", "#1d4ed8", "#f1f3f6", "#ffffff", "#1e293b", "#16a34a", "#d97706", "#dc2626"},
            {"Arena clara", "arena-clara", "claros", "light", "#0d9488", "#0f766e", "#f5f3ee", "#ffffff", "#26312e", "#15803d", "#b45309", "#dc2626"},
            {"Eléctrico", "electrico", "intensos", "light", "#4f46e5", "#4338ca", "#eef0ff", "#ffffff", "#1b1e3b", "#16a34a", "#d97706", "#e11d48"},
            {"Coral vivo", "coral-vivo", "intensos", "light", "#ea580c", "#c2410c", "#fff4ec", "#ffffff", "#33231a", "#16a34a", "#b45309", "#dc2626"},
            {"Noche", "noche", "oscuros", "dark", "#3b82f6", "#2563eb", "#0f1117", "#191b23", "#f1f5f9", "#22c55e", "#f59e0b", "#ef4444"},
            {"Grafito azul", "grafito-azul", "oscuros", "dark", "#22d3ee", "#06b6d4", "#0b0f16", "#141b28", "#e8eef7", "#22c55e", "#f59e0b", "#f87171"},
            {"Rosa pastel", "rosa-pastel", "pastel", "light", "#ec4899", "#db2777", "#fbf3f6", "#ffffff", "#4a3540", "#4d9e6b", "#c08a3e", "#d97070"},
            {"Menta pastel", "menta-pastel", "pastel", "light", "#45b8a8", "#359a8c", "#eef7f4", "#ffffff", "#2c3e39", "#4d9e6b", "#c08a3e", "#d97070"},
    };

    private final TemaCatalogoRepo repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public TemaCatalogoService(TemaCatalogoRepo repo) {
        this.repo = repo;
    }

    // ─── Validacion y normalizacion ───────────────────────────────

    /** Resultado de validar un .json de theme: ok + mensaje + theme normalizado. */
    public record Validacion(boolean ok, String mensaje, String base, String contenido, List<String> muestras) {
        public static Validacion error(String mensaje) {
            return new Validacion(false, mensaje, null, null, List.of());
        }
    }

    private static boolean hexValido(String s) {
        return s != null && s.matches("^#[0-9a-fA-F]{6}$");
    }

    private static boolean dataUrlImagen(String s) {
        return s != null && s.startsWith("data:image/") && s.contains("base64,");
    }

    private static boolean boolDe(JsonNode n) {
        return n != null && n.isBoolean() && n.booleanValue();
    }

    private static int intDe(JsonNode n, int defecto) {
        if (n == null || !n.isNumber()) return defecto;
        return n.intValue();
    }

    /** Valida el JSON contra el contrato de themes y devuelve la forma normalizada. */
    public Validacion validar(String json) {
        if (json == null || json.isBlank()) {
            return Validacion.error("El archivo del theme esta vacio.");
        }
        //Los .json exportados por herramientas de Windows traen BOM; el navegador
        //al leerlo con FileReader la quita, pero el body puede llegar con ella.
        String limpio = json.strip();
        if (limpio.startsWith("\uFEFF")) {
            limpio = limpio.substring(1).strip();
        }
        if (limpio.isEmpty()) {
            return Validacion.error("El archivo del theme esta vacio.");
        }
        if (limpio.length() > MAX_CHARS) {
            return Validacion.error("El archivo del theme supera el maximo permitido (1.2 MB).");
        }
        JsonNode root;
        try {
            root = mapper.readTree(limpio);
        } catch (Exception e) {
            return Validacion.error("El archivo no es un JSON valido.");
        }
        if (root == null || !root.isObject()) {
            return Validacion.error("El archivo no es un tema MOODU valido.");
        }
        String base = texto(root.get("base"));
        if (!"light".equals(base) && !"dark".equals(base)) {
            return Validacion.error("El tema debe traer base \"light\" o \"dark\".");
        }
        JsonNode vars = root.get("vars");
        if (vars == null || !vars.isObject()) {
            return Validacion.error("El tema debe traer sus colores en \"vars\".");
        }
        ObjectNode varsOk = mapper.createObjectNode();
        for (String clave : VARS) {
            String val = texto(vars.get(clave));
            if (!hexValido(val)) {
                return Validacion.error("Color invalido o faltante: " + clave + " (esperado #rrggbb).");
            }
            varsOk.put(clave, val.toLowerCase(Locale.ROOT));
        }
        String fontUi = texto(root.get("fontUi"));
        if (fontUi != null && !FUENTES_UI.contains(fontUi)) {
            return Validacion.error("Fuente de interfaz desconocida: " + fontUi + ".");
        }
        String fontMono = texto(root.get("fontMono"));
        if (fontMono != null && !FUENTES_MONO.contains(fontMono)) {
            return Validacion.error("Fuente monoespaciada desconocida: " + fontMono + ".");
        }

        ObjectNode out = mapper.createObjectNode();
        out.put("base", base);
        out.set("vars", varsOk);
        if (fontUi != null) out.put("fontUi", fontUi);
        if (fontMono != null) out.put("fontMono", fontMono);

        // Fondos (opcionales): se revalidan igual que en el desktop.
        JsonNode fondos = root.get("fondos");
        if (fondos != null) {
            if (!fondos.isObject()) {
                return Validacion.error("Los fondos deben venir en \"fondos\".");
            }
            int tr = intDe(fondos.get("transparencia"), -1);
            if (tr < 0) {
                int op = intDe(fondos.get("opacidad"), -1);   // forma vieja (100 = solido)
                tr = (op >= 0 && op <= 100) ? 100 - op : -1;
            }
            if (tr < 0 || tr > 100) {
                return Validacion.error("La transparencia debe ser un numero entre 0 y 100.");
            }
            for (String flag : new String[]{"blur", "redondeado", "fondoModular"}) {
                JsonNode v = fondos.get(flag);
                if (v != null && !v.isBoolean()) {
                    return Validacion.error("El valor de \"" + flag + "\" debe ser verdadero o falso.");
                }
            }
            ObjectNode fOk = mapper.createObjectNode();
            fOk.put("transparencia", tr);
            for (String flag : new String[]{"blur", "redondeado", "fondoModular"}) {
                fOk.put(flag, boolDe(fondos.get(flag)));
            }
            for (String panel : PANELES) {
                JsonNode c = fondos.get(panel);
                if (c == null || c.isNull()) continue;
                if (!c.isObject()) {
                    return Validacion.error("Fondo invalido en " + panel + ": debe ser un objeto.");
                }
                String modo = texto(c.get("modo"));
                if (modo == null || !MUNDOS.contains(modo)) {
                    return Validacion.error("Fondo invalido en " + panel + ": modo tema/color/imagen.");
                }
                ObjectNode cOk = mapper.createObjectNode();
                cOk.put("modo", modo);
                if ("color".equals(modo)) {
                    String col = texto(c.get("color"));
                    if (!hexValido(col)) {
                        return Validacion.error("Color invalido en " + panel + ".");
                    }
                    cOk.put("color", col.toLowerCase(Locale.ROOT));
                } else if ("imagen".equals(modo)) {
                    String img = texto(c.get("imagen"));
                    if (!dataUrlImagen(img) || img.length() > 900_000) {
                        return Validacion.error("Imagen invalida o muy pesada en " + panel + ".");
                    }
                    cOk.put("imagen", img);
                }
                fOk.set(panel, cOk);
            }
            out.set("fondos", fOk);
        }

        // Navegacion (opcional).
        JsonNode nav = root.get("nav");
        if (nav != null) {
            if (!nav.isObject()) {
                return Validacion.error("La navegacion debe venir en \"nav\".");
            }
            String al = texto(nav.get("alineacion"));
            if (al != null && !ALINEACIONES.contains(al)) {
                return Validacion.error("Alineacion de tabs desconocida (izquierda/centrada/derecha).");
            }
            String ft = texto(nav.get("footer"));
            if (ft != null && !FOOTERS.contains(ft)) {
                return Validacion.error("Alineacion del footer desconocida (default/centrado).");
            }
            String po = texto(nav.get("posicion"));
            if (po != null && !POSICIONES.contains(po)) {
                return Validacion.error("Posicion del nav desconocida (arriba/izquierda/derecha).");
            }
            JsonNode mac = nav.get("efectoMac");
            if (mac != null && !mac.isBoolean()) {
                return Validacion.error("El efecto MAC debe ser verdadero o falso.");
            }
            JsonNode fv = nav.get("footerVisible");
            if (fv != null && !fv.isBoolean()) {
                return Validacion.error("La visibilidad del footer debe ser verdadero o falso.");
            }
            ObjectNode nOk = mapper.createObjectNode();
            if (al != null) nOk.put("alineacion", al);
            nOk.put("efectoMac", boolDe(mac));
            if (ft != null) nOk.put("footer", ft);
            nOk.put("footerVisible", !fv.isBoolean() || fv.booleanValue());
            if (po != null) nOk.put("posicion", po);
            out.set("nav", nOk);
        }

        try {
            String contenido = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out);
            List<String> muestras = List.of(
                    varsOk.get("primary").asText(), varsOk.get("bgBody").asText(),
                    varsOk.get("bgSurface").asText(), varsOk.get("textPrimary").asText());
            return new Validacion(true, "Tema valido.", base, contenido, muestras);
        } catch (Exception e) {
            return Validacion.error("No se pudo preparar el tema: " + e.getMessage());
        }
    }

    private static String texto(JsonNode n) {
        if (n == null || !n.isTextual()) return null;
        String s = n.textValue().trim();
        return s.isEmpty() ? null : s;
    }

    // ─── Listado ───────────────────────────────────────────────────

    /**
     * Publica los presets de fabrica de MOODU (los 8 de src/utils/tema.js) para
     * que tambien se puedan compartir/curar desde la Tienda. Idempotente: el
     * marcador es que exista alguna fila creada por "sistema". Pasa SIEMPRE por
     * validar()+crear(): la semilla no puede publicar un theme invalido.
     */
    public int sembrarFabrica() {
        List<TemaCatalogo> todas = repo.findAllByOrderByNombreAsc();
        boolean yaSembrado = todas.stream()
                .anyMatch(t -> t.getCreadoPor() != null && t.getCreadoPor().equalsIgnoreCase("sistema"));
        if (yaSembrado) {
            // backfill idempotente: los presets ya sembrados quedan marcados como de
            // fabrica (asi salen de la Tienda) sin pedir migracion manual.
            int marcados = 0;
            for (TemaCatalogo t : todas) {
                if ("sistema".equalsIgnoreCase(t.getCreadoPor()) && !t.esDeFabrica()) {
                    t.setEsFabrica(Boolean.TRUE);
                    repo.save(t);
                    marcados++;
                }
            }
            return marcados;
        }
        int n = 0;
        for (String[] p : FABRICA) {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("nombre", p[0]);
                body.put("clave", p[1]);
                body.put("grupo", p[2]);
                body.put("version", "1.0.0");
                body.put("descripcion", "Preset de fabrica de MOODU (tambien disponible sin conexion).");
                body.put("activo", true);
                body.put("esFabrica", true);
                body.put("contenido", fabricaJson(p));
                if (Boolean.TRUE.equals(crear(body, "sistema").get("ok"))) {
                    n++;
                }
            } catch (Exception e) {
                System.err.println("[Hub] No se pudo sembrar el theme de fabrica " + p[1] + ": " + e.getMessage());
            }
        }
        return n;
    }

    private static String fabricaJson(String[] p) {
        String[] claves = {"primary", "primaryHover", "bgBody", "bgSurface", "textPrimary", "success", "warning", "danger"};
        StringBuilder sb = new StringBuilder("{\"nombre\":\"").append(p[0])
                .append("\",\"base\":\"").append(p[3]).append("\",\"vars\":{");
        for (int i = 0; i < claves.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(claves[i]).append("\":\"").append(p[4 + i]).append('"');
        }
        return sb.append("}}").toString();
    }

    /**
     * Temas activos para la Tienda del desktop (sin el archivo: pesa).
     * Los de FABRICA (los presets que trae MOODU de serie) NO se ofrecen: el desktop
     * ya los trae incorporados y en la Tienda solo van los que sube el admin.
     * Filtro en Java y no en la query: una columna Boolean nullable con NULL (filas
     * viejas) no cumple "= false" en Postgres y las ocultaria de la Tienda sin querer.
     */
    public List<Map<String, Object>> listarPublicos() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TemaCatalogo t : repo.findByActivoTrueOrderByNombreAsc()) {
            if (t.esDeFabrica()) continue;
            out.add(ficha(t, false));
        }
        return out;
    }

    /** Listado del panel admin (incluye inactivos, de fabrica y si hay archivo valido). */
    public List<Map<String, Object>> listarTodos() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TemaCatalogo t : repo.findAllByOrderByNombreAsc()) {
            out.add(ficha(t, true));
        }
        return out;
    }

    private Map<String, Object> ficha(TemaCatalogo t, boolean admin) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("clave", t.getClave());
        m.put("nombre", t.getNombre());
        m.put("descripcion", t.getDescripcion());
        m.put("grupo", t.getGrupo());
        m.put("version", t.getVersion());
        m.put("base", t.getBase());
        m.put("activo", t.isActivo());
        m.put("fechaCreacion", t.getFechaCreacion());
        Validacion v = validar(t.getContenido());
        m.put("muestras", v.muestras());
        if (admin) {
            m.put("creadoPor", t.getCreadoPor());
            m.put("fechaModificacion", t.getFechaModificacion());
            m.put("archivoOk", v.ok());
            m.put("esFabrica", t.esDeFabrica());
            m.put("tamano", t.getContenido() != null ? t.getContenido().length() : 0);
        }
        return m;
    }

    /** Ficha publica de un tema, con el archivo (el desktop lo valida al aplicar). */
    public Map<String, Object> detalle(String clave) {
        TemaCatalogo t = entidadPublica(clave);
        if (t == null) return null;
        Map<String, Object> m = ficha(t, false);
        m.put("contenido", t.getContenido());
        return m;
    }

    public TemaCatalogo entidad(String clave) {
        return repo.findByClaveIgnoreCase(clave.trim()).orElse(null);
    }

    /**
     * Tema tal como lo ve la Tienda: debe existir, estar activo y NO ser de fabrica.
     * Unica puerta para el detalle y la descarga (asi no se sirve el archivo de un
     * preset de fabrica aunque se conozca su clave).
     */
    public TemaCatalogo entidadPublica(String clave) {
        TemaCatalogo t = entidad(clave);
        if (t == null || !t.isActivo() || t.esDeFabrica() || t.getContenido() == null) return null;
        return t;
    }

    // ─── Escritura (admin) ────────────────────────────────────────

    /** Clave tecnica a partir del nombre: sin acentos, minusculas, guiones. */
    public static String derivarClave(String nombre) {
        String n = java.text.Normalizer.normalize(nombre == null ? "" : nombre, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (n.isBlank()) n = "tema";
        return n.length() > 60 ? n.substring(0, 60) : n;
    }

    private String claveLibre(String base, Long idActual) {
        String clave = base;
        int i = 2;
        while (true) {
            var repo1 = repo.findByClaveIgnoreCase(clave);
            if (repo1.isEmpty() || (idActual != null && repo1.get().getId().equals(idActual))) {
                return clave;
            }
            clave = base + "-" + i++;
        }
    }

    /** Crea un tema. Devuelve la ficha o un mapa de error {ok:false, mensaje}. */
    public Map<String, Object> crear(Map<String, Object> body, String usuario) {
        String nombre = str(body, "nombre");
        if (nombre == null || nombre.isBlank()) {
            return error("El nombre del tema es obligatorio.");
        }
        if (nombre.length() > 100) {
            return error("El nombre no puede pasar de 100 caracteres.");
        }
        String contenido = str(body, "contenido");
        Validacion v = validar(contenido);
        if (!v.ok()) return error(v.mensaje());

        TemaCatalogo t = new TemaCatalogo();
        t.setNombre(nombre.trim());
        String clave = str(body, "clave");
        t.setClave(claveLibre(clave == null || clave.isBlank() ? derivarClave(nombre) : derivarClave(clave), null));
        t.setDescripcion(str(body, "descripcion"));
        t.setGrupo(grupoValido(str(body, "grupo")));
        t.setVersion(versionValida(str(body, "version")));
        t.setBase(v.base());
        t.setContenido(v.contenido());
        t.setActivo(body.containsKey("activo") ? Boolean.parseBoolean(str(body, "activo")) : true);
        t.setEsFabrica(body.containsKey("esFabrica") && Boolean.parseBoolean(str(body, "esFabrica")));
        t.setCreadoPor(usuario);
        TemaCatalogo g = repo.save(t);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("tema", ficha(g, true));
        return out;
    }

    /** Actualiza un tema (con o sin archivo nuevo). */
    public Map<String, Object> actualizar(Long id, Map<String, Object> body) {
        TemaCatalogo t = repo.findById(id).orElse(null);
        if (t == null) return error("Tema no encontrado.");
        String nombre = str(body, "nombre");
        if (nombre != null && !nombre.isBlank()) {
            t.setNombre(nombre.trim());
        }
        if (body.containsKey("descripcion")) t.setDescripcion(str(body, "descripcion"));
        if (body.containsKey("grupo")) t.setGrupo(grupoValido(str(body, "grupo")));
        if (body.containsKey("version")) t.setVersion(versionValida(str(body, "version")));
        if (body.containsKey("activo")) {
            t.setActivo(Boolean.parseBoolean(str(body, "activo")));
        }
        if (body.containsKey("esFabrica")) {
            t.setEsFabrica(Boolean.parseBoolean(str(body, "esFabrica")));
        }
        String contenido = str(body, "contenido");
        if (contenido != null && !contenido.isBlank()) {
            Validacion v = validar(contenido);
            if (!v.ok()) return error(v.mensaje());
            t.setContenido(v.contenido());
            t.setBase(v.base());
        }
        if (body.containsKey("clave")) {
            String clave = str(body, "clave");
            if (clave != null && !clave.isBlank()) {
                t.setClave(claveLibre(derivarClave(clave), id));
            }
        }
        t.setFechaModificacion(java.time.LocalDateTime.now());
        TemaCatalogo g = repo.save(t);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("tema", ficha(g, true));
        return out;
    }

    public Map<String, Object> eliminar(Long id) {
        if (!repo.existsById(id)) return error("Tema no encontrado.");
        repo.deleteById(id);
        return Map.of("ok", true);
    }

    private String grupoValido(String g) {
        if (g != null && GRUPOS.contains(g)) return g;
        return "claros";
    }

    private String versionValida(String v) {
        if (v == null || v.isBlank()) return "1.0.0";
        String s = v.trim();
        return s.length() > 20 ? s.substring(0, 20) : s;
    }

    private static String str(Map<String, Object> body, String clave) {
        Object v = body.get(clave);
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() ? null : s.trim();
    }

    private static Map<String, Object> error(String mensaje) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("mensaje", mensaje);
        return m;
    }
}