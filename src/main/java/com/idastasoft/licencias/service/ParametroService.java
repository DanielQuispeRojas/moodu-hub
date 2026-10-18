package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.HubParametro;
import com.idastasoft.licencias.repository.HubParametroRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parametros editables del Hub en caliente (tabla hub_parametro).
 *
 * <p>Un almacen clave/valor simple. Lo usa el aviso de MUDANZA de hosting y
 * la URL de la tienda Comprape: son datos que cambian en el momento exacto en
 * que el admin los necesita cambiar, y no pueden depender de recompilar
 * (AGENTS 11.438: lo editable vive en BD, el disco del hosting es efimero).</p>
 */
@Service
public class ParametroService {

    /** Aviso de mudanza del Hub a otro hosting. */
    public static final String MUDANZA_ACTIVA = "MUDANZA_ACTIVA";
    public static final String MUDANZA_URL_NUEVA = "MUDANZA_URL_NUEVA";
    public static final String MUDANZA_FECHA_LIMITE = "MUDANZA_FECHA_LIMITE";
    public static final String MUDANZA_MENSAJE = "MUDANZA_MENSAJE";
    /** URL publica del Hub (vacia = se deduce de la peticion). */
    public static final String HUB_URL = "HUB_URL";
    /** URL de la tienda Comprape (vacia = el desktop usa su override local). */
    public static final String COMPRAPE_URL = "COMPRAPE_URL";

    private final HubParametroRepo repo;

    public ParametroService(HubParametroRepo repo) {
        this.repo = repo;
    }

    /** Valor o defecto (nunca null). */
    @Transactional(readOnly = true)
    public String get(String clave, String defecto) {
        return repo.findByClaveIgnoreCase(clave)
                .map(HubParametro::getValor)
                .filter(v -> v != null && !v.isBlank())
                .orElse(defecto);
    }

    @Transactional(readOnly = true)
    public boolean getBool(String clave, boolean defecto) {
        String v = get(clave, null);
        if (v == null) return defecto;
        return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim()) || "si".equalsIgnoreCase(v.trim());
    }

    /** Guardar en caliente (update in place; crear si no existe). */
    @Transactional
    public void set(String clave, String valor, String usuario) {
        if (clave == null || clave.isBlank()) return;
        HubParametro p = repo.findByClaveIgnoreCase(clave.trim()).orElseGet(() -> new HubParametro(clave.trim(), valor));
        p.setClave(clave.trim());
        p.setValor(valor == null || valor.isBlank() ? null : valor.trim());
        p.setActualizadoPor(usuario);
        repo.save(p);
    }

    /** Guarda varias claves de una vez (el panel admin manda el mapa). */
    @Transactional
    public Map<String, Object> guardarVarios(Map<String, Object> datos, String usuario) {
        Map<String, Object> res = new LinkedHashMap<>();
        if (datos == null) return res;
        for (Map.Entry<String, Object> e : datos.entrySet()) {
            set(e.getKey(), e.getValue() == null ? null : String.valueOf(e.getValue()), usuario);
            res.put(e.getKey(), e.getValue());
        }
        return res;
    }

    /** Todo como mapa (lo que ve el panel). */
    @Transactional(readOnly = true)
    public Map<String, Object> mapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (HubParametro p : repo.findAllByOrderByClaveAsc()) {
            m.put(p.getClave(), p.getValor());
        }
        return m;
    }

    /** Solo las claves del aviso de mudanza + URLs (contrato publico). */
    @Transactional(readOnly = true)
    public Map<String, Object> mudanzaYUrls(String hubUrlDetectado) {
        Map<String, Object> m = new LinkedHashMap<>();
        String hubUrl = get(HUB_URL, null);
        m.put("hubUrl", (hubUrl != null && !hubUrl.isBlank()) ? hubUrl.trim() : hubUrlDetectado);
        m.put("comprapeUrl", get(COMPRAPE_URL, null));
        Map<String, Object> mud = new LinkedHashMap<>();
        boolean activa = getBool(MUDANZA_ACTIVA, false);
        String nueva = get(MUDANZA_URL_NUEVA, null);
        // Una mudanza sin URL nueva no se anuncia: el usuario no tendria a donde
        // ir. Es un dato mal guardado, no una mudanza real.
        mud.put("activa", activa && nueva != null && !nueva.isBlank());
        if (nueva != null && !nueva.isBlank()) mud.put("urlNueva", nueva.trim());
        String limite = get(MUDANZA_FECHA_LIMITE, null);
        if (limite != null && !limite.isBlank()) mud.put("fechaLimite", limite.trim());
        String msg = get(MUDANZA_MENSAJE, null);
        if (msg != null && !msg.isBlank()) mud.put("mensaje", msg.trim());
        m.put("mudanza", mud);
        return m;
    }
}