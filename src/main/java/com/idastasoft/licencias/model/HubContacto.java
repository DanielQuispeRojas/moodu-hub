package com.idastasoft.licencias.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Medios de contacto del soporte de MOODU y portada de la seccion Soporte.
 *
 * <p>Fila unica (id = 1). Vive en BD y no en hub-config.json a proposito:
 * el disco de Render es efimero y se pierde en cada redeploy (mismo motivo
 * que el catalogo de temas, AGENTS 11.438). El JSON solo hace de semilla
 * la primera vez.</p>
 *
 * <p><b>Redes (REQ-HUB-SOPORTE-01):</b> lista libre {@code [{clave, valor,
 * icono}]}. El admin escribe "Whatsapp" + el enlace; el usuario lo ve como
 * "Whatsapp" con un boton "Abrir". Los 7 campos fijos de la v1 se conservan
 * (los usa el modal "Mejorar Plan"); las redes libres los reemplazan para
 * lo que no exista como campo.</p>
 *
 * <p><b>Portada:</b> lista {@code [{id, titulo, url, imagen, enlace}]} para
 * el slider de la seccion Soporte. {@code imagen} es base64 servido por el
 * Hub (el disco es efimero: en BD); {@code url} permite una imagen externa.
 * Una de las dos, nunca ambas.</p>
 *
 * <p>Sin {@code @Lob} a proposito: en Postgres eso mapea a {@code oid} y la
 * lectura fuera de sesion revienta con "Unable to access lob stream"
 * (AGENTS 11.195 / 11.438).</p>
 */
@Entity
@Table(name = "hub_contacto")
public class HubContacto {

    public static final int MAX_REDES = 12;
    public static final int MAX_PORTADA = 8;
    public static final int MAX_BYTES_IMAGEN = 1_572_864; // 1.5 MB

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    private Integer id = 1;

    /** Con codigo de pais y sin signos: 51987654321 (el panel arma wa.me). */
    @Column(length = 60)
    private String whatsapp;

    @Column(length = 160)
    private String correo;

    @Column(length = 60)
    private String telefono;

    @Column(length = 300)
    private String fanpage;

    @Column(length = 300)
    private String web;

    @Column(length = 100)
    private String telegram;

    @Column(length = 300)
    private String instagram;

    /** Lista libre de redes/enlaces: [{clave, valor, icono}]. */
    @Column(columnDefinition = "TEXT")
    private String redesJson;

    /** Portada del slider: [{id, titulo, url, imagen(base64), enlace}]. */
    @Column(columnDefinition = "TEXT")
    private String portadaJson;

    /**
     * Lo que ve el cliente.
     *
     * <p>La portada va SIN el base64 (son MB): se anuncia el {@code imagenUrl}
     * para pedir los bytes por {@code /api/catalogo/portada/{id}}. El admin
     * en cambio necesita el base64 para poder re-guardar la portada sin
     * perderla, asi que pide {@code conBase64 = true}.</p>
     *
     * @param conBase64 true solo para el panel admin (incluye el archivo)
     */
    public Map<String, Object> comoMapa(boolean conBase64) {
        Map<String, Object> m = new LinkedHashMap<>();
        poner(m, "whatsapp", whatsapp);
        poner(m, "correo", correo);
        poner(m, "telefono", telefono);
        poner(m, "fanpage", fanpage);
        poner(m, "instagram", instagram);
        poner(m, "telegram", telegram);
        poner(m, "web", web);
        m.put("redes", redes());
        List<Map<String, Object>> pub = new ArrayList<>();
        for (Map<String, Object> p : portada()) {
            Map<String, Object> c = new LinkedHashMap<>(p);
            boolean tieneImagen = c.get("imagen") != null;
            if (!conBase64) c.remove("imagen");
            c.put("imagenUrl", tieneImagen ? "/api/catalogo/portada/" + c.get("id") : c.get("url"));
            pub.add(c);
        }
        m.put("portada", pub);
        return m;
    }

    public Map<String, Object> comoMapa() {
        return comoMapa(false);
    }

    private void poner(Map<String, Object> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v.trim());
    }

    public void desdeMapa(Map<String, Object> m) {
        if (m == null) return;
        whatsapp = txt(m.get("whatsapp"));
        correo = txt(m.get("correo"));
        telefono = txt(m.get("telefono"));
        fanpage = txt(m.get("fanpage"));
        web = txt(m.get("web"));
        telegram = txt(m.get("telegram"));
        instagram = txt(m.get("instagram"));
        if (m.containsKey("redes")) redesJson = serializarRedes(m.get("redes"));
        if (m.containsKey("portada")) portadaJson = serializarPortada(m.get("portada"));
    }

    // ── Redes libres ────────────────────────────────────────────────────
    /** [{clave, valor, icono}] validadas (clave y valor obligatorios). */
    public List<Map<String, Object>> redes() {
        List<Map<String, Object>> lista = leer(redesJson, new TypeReference<List<Map<String, Object>>>() {});
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : lista) {
            String clave = txt(r.get("clave"));
            String valor = txt(r.get("valor"));
            if (clave == null || valor == null) continue;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("clave", clave);
            c.put("valor", valor);
            c.put("icono", txt(r.get("icono")));
            out.add(c);
            if (out.size() >= MAX_REDES) break;
        }
        return out;
    }

    private String serializarRedes(Object entrada) {
        List<Map<String, Object>> limpio = new ArrayList<>();
        if (entrada instanceof List<?> lista) {
            for (Object o : lista) {
                if (!(o instanceof Map<?, ?> mapa)) continue;
                String clave = txt(mapa.get("clave"));
                String valor = txt(mapa.get("valor"));
                if (clave == null || valor == null) continue;
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("clave", clave);
                c.put("valor", valor);
                c.put("icono", txt(mapa.get("icono")));
                limpio.add(c);
                if (limpio.size() >= MAX_REDES) break;
            }
        }
        if (limpio.isEmpty()) return null;
        return escribir(limpio);
    }

    // ── Portada (slider) ────────────────────────────────────────────────
    public List<Map<String, Object>> portada() {
        List<Map<String, Object>> lista = leer(portadaJson, new TypeReference<List<Map<String, Object>>>() {});
        List<Map<String, Object>> out = new ArrayList<>();
        int i = 1;
        for (Map<String, Object> p : lista) {
            Map<String, Object> c = new LinkedHashMap<>();
            Object id = p.get("id");
            c.put("id", id instanceof Number n ? n.intValue() : i);
            c.put("titulo", txt(p.get("titulo")));
            c.put("url", txt(p.get("url")));
            c.put("enlace", txt(p.get("enlace")));
            String imagen = txt(p.get("imagen"));
            if (imagen == null && c.get("url") == null) continue; // sin imagen no hay slide
            if (imagen != null && imagen.length() > MAX_BYTES_IMAGEN * 2) imagen = null;
            c.put("imagen", imagen);
            out.add(c);
            i++;
            if (out.size() >= MAX_PORTADA) break;
        }
        return out;
    }

    private String serializarPortada(Object entrada) {
        List<Map<String, Object>> limpio = new ArrayList<>();
        int i = 1;
        if (entrada instanceof List<?> lista) {
            for (Object o : lista) {
                if (!(o instanceof Map<?, ?> mapa)) continue;
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("id", i++);
                c.put("titulo", txt(mapa.get("titulo")));
                c.put("url", txt(mapa.get("url")));
                c.put("enlace", txt(mapa.get("enlace")));
                String imagen = txt(mapa.get("imagen"));
                // El base64 puede venir con el prefijo data: (lo manda el panel).
                if (imagen != null) {
                    int coma = imagen.indexOf(',');
                    if (imagen.startsWith("data:") && coma > 0) imagen = imagen.substring(coma + 1);
                    if (imagen.length() > MAX_BYTES_IMAGEN * 2) imagen = null; // fuera de tope
                }
                c.put("imagen", imagen);
                if (imagen == null && c.get("url") == null) continue; // sin imagen no hay slide
                limpio.add(c);
                if (limpio.size() >= MAX_PORTADA) break;
            }
        }
        if (limpio.isEmpty()) return null;
        return escribir(limpio);
    }

    /** Bytes + mime de una imagen de la portada (id del slide). */
    public byte[] portadaBytes(int idSlide) {
        for (Map<String, Object> p : portada()) {
            Object pid = p.get("id");
            if (pid instanceof Number n && n.intValue() == idSlide) {
                String imagen = (String) p.get("imagen");
                if (imagen == null) return null;
                try {
                    return java.util.Base64.getMimeDecoder().decode(imagen);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    // ── helpers ─────────────────────────────────────────────────────────
    private static <T> T leer(String json, TypeReference<T> tipo) {
        if (json == null || json.isBlank()) {
            try {
                return MAPPER.readValue("[]", tipo);
            } catch (Exception e) {
                return null;
            }
        }
        try {
            return MAPPER.readValue(json, tipo);
        } catch (Exception e) {
            System.err.println("[Hub] contacto: JSON ilegible, se muestra vacio: " + e.getMessage());
            return null;
        }
    }

    private static String escribir(Object valor) {
        try {
            return MAPPER.writeValueAsString(valor);
        } catch (Exception e) {
            return null;
        }
    }

    private String txt(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getWhatsapp() { return whatsapp; }
    public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getFanpage() { return fanpage; }
    public void setFanpage(String fanpage) { this.fanpage = fanpage; }
    public String getWeb() { return web; }
    public void setWeb(String web) { this.web = web; }
    public String getTelegram() { return telegram; }
    public void setTelegram(String telegram) { this.telegram = telegram; }
    public String getInstagram() { return instagram; }
    public void setInstagram(String instagram) { this.instagram = instagram; }
    public String getRedesJson() { return redesJson; }
    public void setRedesJson(String redesJson) { this.redesJson = redesJson; }
    public String getPortadaJson() { return portadaJson; }
    public void setPortadaJson(String portadaJson) { this.portadaJson = portadaJson; }
}