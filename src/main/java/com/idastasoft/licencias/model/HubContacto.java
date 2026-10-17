package com.idastasoft.licencias.model;

import jakarta.persistence.*;

/**
 * Medios de contacto del soporte de MOODU (REQ-HUB-PLANES-01).
 *
 * <p>Fila unica (id = 1). Vive en BD y no en hub-config.json a proposito:
 * el disco de Render es efimero y se pierde en cada redeploy (mismo motivo
 * que el catalogo de temas, AGENTS 11.438). El JSON solo hace de semilla
 * la primera vez.</p>
 *
 * <p>La Tienda los muestra en el modal "Mejorar Plan". Vacio = el modal
 * avisa que no hay medios cargados; nunca rompe.</p>
 */
@Entity
@Table(name = "hub_contacto")
public class HubContacto {

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

    /** Solo los medios con valor: el panel y la Tienda noiteran vacios. */
    public java.util.Map<String, Object> comoMapa() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        poner(m, "whatsapp", whatsapp);
        poner(m, "correo", correo);
        poner(m, "telefono", telefono);
        poner(m, "fanpage", fanpage);
        poner(m, "web", web);
        poner(m, "telegram", telegram);
        poner(m, "instagram", instagram);
        return m;
    }

    private void poner(java.util.Map<String, Object> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v.trim());
    }

    public void desdeMapa(java.util.Map<String, Object> m) {
        if (m == null) return;
        whatsapp = txt(m.get("whatsapp"));
        correo = txt(m.get("correo"));
        telefono = txt(m.get("telefono"));
        fanpage = txt(m.get("fanpage"));
        web = txt(m.get("web"));
        telegram = txt(m.get("telegram"));
        instagram = txt(m.get("instagram"));
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
}