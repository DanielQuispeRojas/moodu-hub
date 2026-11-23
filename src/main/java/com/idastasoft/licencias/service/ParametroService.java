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

    /**
     * Catalogo publico de normativas (REQ-HUB-NORMATIVAS-01 / HUB-10). Lo edita
     * el ADMIN en caliente: si el repo se muda, cambia el parametro y ningun
     * MOODU necesita recompilar. Los defaults son el repo publico actual.
     */
    public static final String NORMATIVAS_REPO_URL = "NORMATIVAS_REPO_URL";
    public static final String NORMATIVAS_MANIFEST_URL = "NORMATIVAS_MANIFEST_URL";
    public static final String NORMATIVAS_VERSION = "NORMATIVAS_VERSION";
    public static final String NORMATIVAS_REPO_URL_DEFAULT =
            "https://github.com/DanielQuispeRojas/moodu_normativas.git";
    public static final String NORMATIVAS_MANIFEST_URL_DEFAULT =
            "https://raw.githubusercontent.com/DanielQuispeRojas/moodu_normativas/main/manifest.json";
    public static final String NORMATIVAS_VERSION_DEFAULT = "2026.1";
    /**
     * Factor del precio ANUAL: {@code anual = mensual × factor} (REQ-HUBPAQ-01).
     * Lo ingresa el admin; default 10 (el modelo documentado de 10 mensualidades).
     */
    public static final String ANUAL_FACTOR = "ANUAL_FACTOR";
    public static final int ANUAL_FACTOR_DEFAULT = 10;
    public static final int ANUAL_FACTOR_MIN = 1;
    public static final int ANUAL_FACTOR_MAX = 24;

    /**
     * Tope de anios de un plan de pago (HUB-11). Sustituye al antiguo plan
     * PERMANENTE: ningun modulo de PAGA puede superar este plazo. El admin lo
     * edita en caliente; el plan TRIENAL deriva su vigencia y su etiqueta de
     * aqui (si cambia a 5, el "plan de 3 anios" pasa a ser de 5 sin recompilar).
     */
    public static final String MAX_ANIOS_LICENCIA = "MAX_ANIOS_LICENCIA";
    public static final int MAX_ANIOS_LICENCIA_DEFAULT = 3;
    public static final int MAX_ANIOS_LICENCIA_MIN = 1;
    public static final int MAX_ANIOS_LICENCIA_MAX = 20;

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
        // El tope de anios se valida AQUI (punto unico): el panel puede mandarlo
        // por el formulario generico de parametros o por un endpoint propio.
        if (MAX_ANIOS_LICENCIA.equalsIgnoreCase(clave.trim())) {
            int n;
            try {
                n = Integer.parseInt(String.valueOf(valor).trim());
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("El tope de anios debe ser un numero entero.");
            }
            if (n < MAX_ANIOS_LICENCIA_MIN || n > MAX_ANIOS_LICENCIA_MAX) {
                throw new IllegalArgumentException("El tope de anios debe estar entre "
                        + MAX_ANIOS_LICENCIA_MIN + " y " + MAX_ANIOS_LICENCIA_MAX + ".");
            }
        }
        HubParametro p = repo.findByClaveIgnoreCase(clave.trim()).orElseGet(() -> new HubParametro(clave.trim(), valor));
        p.setClave(clave.trim());
        p.setValor(valor == null || valor.isBlank() ? null : valor.trim());
        p.setActualizadoPor(usuario);
        repo.save(p);
    }

    /**
     * Factor anual vigente. Un valor corrupto cae al default: es un parametro
     * de visualizacion/precio, nunca debe tumbar el arranque del Hub.
     */
    @Transactional(readOnly = true)
    public int factorAnual() {
        String v = get(ANUAL_FACTOR, String.valueOf(ANUAL_FACTOR_DEFAULT));
        try {
            int f = Integer.parseInt(v.trim());
            return f >= ANUAL_FACTOR_MIN && f <= ANUAL_FACTOR_MAX ? f : ANUAL_FACTOR_DEFAULT;
        } catch (RuntimeException e) {
            return ANUAL_FACTOR_DEFAULT;
        }
    }

    /**
     * Tope de anios vigente. Un valor corrupto cae al default: es un parametro
     * de contratacion, nunca debe impedir que el Hub arranque (HUB-11).
     */
    @Transactional(readOnly = true)
    public int maxAniosLicencia() {
        String v = get(MAX_ANIOS_LICENCIA, String.valueOf(MAX_ANIOS_LICENCIA_DEFAULT));
        try {
            int n = Integer.parseInt(v.trim());
            return n >= MAX_ANIOS_LICENCIA_MIN && n <= MAX_ANIOS_LICENCIA_MAX
                    ? n : MAX_ANIOS_LICENCIA_DEFAULT;
        } catch (RuntimeException e) {
            return MAX_ANIOS_LICENCIA_DEFAULT;
        }
    }

    /** Guarda el factor con validacion estricta (400 con motivo desde el panel). */
    @Transactional
    public int guardarFactorAnual(Object valor, String usuario) {
        int f;
        try {
            f = Integer.parseInt(String.valueOf(valor).trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("El factor anual debe ser un numero entero.");
        }
        if (f < ANUAL_FACTOR_MIN || f > ANUAL_FACTOR_MAX) {
            throw new IllegalArgumentException("El factor anual debe estar entre "
                    + ANUAL_FACTOR_MIN + " y " + ANUAL_FACTOR_MAX + ".");
        }
        set(ANUAL_FACTOR, String.valueOf(f), usuario);
        return f;
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

    /**
     * Contrato publico de normativas (HUB-10). El Hub es la UNICA fuente de la
     * ubicacion del catalogo: asi un cambio de hosting del repo no obliga a
     * recompilar ningun MOODU.
     *
     * <p>Sin parametro se publica el fallback embebido con aviso; una URL
     * mal escrita NO se propaga como activa (se cae al fallback y se avisa).
     * La lectura es publica y no consume cupo de licencia.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> normativas() {
        Map<String, Object> m = new LinkedHashMap<>();
        String repo = get(NORMATIVAS_REPO_URL, null);
        String manifest = get(NORMATIVAS_MANIFEST_URL, null);
        String version = get(NORMATIVAS_VERSION, null);

        boolean repoOk = urlValida(repo);
        boolean manifestOk = urlValida(manifest);
        boolean versionOk = version != null && !version.isBlank()
                && version.trim().matches("^[0-9]{4}\\.[0-9]{1,3}$");

        m.put("normativasRepoUrl", repoOk ? repo.trim() : NORMATIVAS_REPO_URL_DEFAULT);
        m.put("normativasManifestUrl", manifestOk ? manifest.trim() : NORMATIVAS_MANIFEST_URL_DEFAULT);
        m.put("normativasVersion", versionOk ? version.trim() : NORMATIVAS_VERSION_DEFAULT);
        m.put("normativasOrigen",
                (repoOk && manifestOk && versionOk) ? "HUB" : "EMBEBIDO");
        if (!(repoOk && manifestOk && versionOk)) {
            StringBuilder aviso = new StringBuilder("Usando ubicacion embebida del catalogo de normativas");
            if (repo != null && !repo.isBlank() && !repoOk) aviso.append(" (repo invalido)");
            if (manifest != null && !manifest.isBlank() && !manifestOk) aviso.append(" (manifiesto invalido)");
            if (version != null && !version.isBlank() && !versionOk) aviso.append(" (version invalida)");
            m.put("normativasAviso", aviso.append(".").toString());
        }
        return m;
    }

    /** http(s) con host: evita propagar como activo un valor mal escrito. */
    private static boolean urlValida(String valor) {
        if (valor == null || valor.isBlank()) return false;
        try {
            var uri = java.net.URI.create(valor.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (RuntimeException e) {
            return false;
        }
    }
}