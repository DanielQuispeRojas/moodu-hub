package com.idastasoft.licencias.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tipo (tier) de MOODU. NO es un modulo: es el alcance del SISTEMA (el core).
 * Los modulos se contratan aparte.
 *
 * <p><b>Fuente de verdad: la lista de modulos de la licencia.</b> No hay una
 * columna "tipo" que pueda contradecir a la lista:</p>
 * <ul>
 *   <li>Si la licencia trae {@code moodu_pro} =&gt; <b>PRO</b>.</li>
 *   <li>Si trae {@code moodu_plus} =&gt; <b>PLUS</b>.</li>
 *   <li>Si no trae ninguna =&gt; <b>GRATIS</b> (el que todos pueden tener).</li>
 * </ul>
 * Asi los plazos los trae cada modulo de la licencia por su cuenta
 * ({@link LicenciaModulo}: PLUS es PERMANENTE, PRO es MENSUAL o ANUAL) sin
 * duplicar el dato, y "Mejorar Plan" en la Tienda funciona igual que para
 * cualquier otro modulo.
 *
 * <p>Alcance por tipo (2026-10-18):</p>
 * <ul>
 *   <li><b>GRATIS</b>: maquina unica (UNICA) y NODO. No habilita maquina central.</li>
 *   <li><b>PLUS</b>: + modo <b>maquina central</b> (abre canal, los NODOs se le
 *       unen). A futuro: ceder central.</li>
 *   <li><b>PRO</b>: + <b>todos los modulos oficiales</b> (no hace falta
 *       licenciarlos uno por uno) y <b>dispositivos ilimitados</b>. Los modulos
 *       de TERCEROS siguen con su propia licencia (MOODU no los cubre).</li>
 * </ul>
 */
public enum TipoMoodu {

    GRATIS,
    PLUS,
    PRO;

    /** Clave de catalogo del PLUS (plan PERMANENTE). */
    public static final String CLAVE_PLUS = "moodu_plus";
    /** Clave de catalogo del PRO (plan MENSUAL o ANUAL). */
    public static final String CLAVE_PRO = "moodu_pro";

    /** Capacidad: el core puede actuar como maquina central (canal + NODOs). */
    public static final String CAP_MODO_CENTRAL = "MODO_CENTRAL";
    /** Capacidad: todos los modulos oficiales disponibles sin listarlos. */
    public static final String CAP_TODOS_MODULOS = "TODOS_MODULOS";
    /** Capacidad: el cupo de dispositivos no aplica (ilimitado). */
    public static final String CAP_DISPOSITIVOS_ILIMITADOS = "DISPOSITIVOS_ILIMITADOS";

    public static TipoMoodu de(String valor) {
        if (valor == null || valor.isBlank()) return GRATIS;
        try {
            return valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GRATIS; // desconocido = el gratis (fail-safe)
        }
    }

    public static String normalizar(String valor) {
        return de(valor).name();
    }

    /**
     * Tipo de MOODU de una licencia, deducido de sus modulos. PRO pisa a PLUS
     * (si alguien los tuviera a la vez, manda el mayor alcance).
     */
    public static TipoMoodu deLicencia(List<String> modulos) {
        if (modulos == null) return GRATIS;
        boolean plus = false;
        for (String m : modulos) {
            if (m == null) continue;
            String c = m.trim().toLowerCase(Locale.ROOT);
            if (c.equals(CLAVE_PRO)) return PRO;
            if (c.equals(CLAVE_PLUS)) plus = true;
        }
        return plus ? PLUS : GRATIS;
    }

    /**
     * Tipo al que pertenece una CLAVE DE CATALOGO ({@code moodu_plus} /
     * {@code moodu_pro}), o null si el modulo no es un tier del sistema.
     * Lo usa el Hub para validar la periodicidad del plan al guardarlo: PLUS
     * solo PERMANENTE, PRO solo MENSUAL o ANUAL.
     */
    public static TipoMoodu deClaveCatalogo(String clave) {
        if (clave == null || clave.isBlank()) return null;
        String c = clave.trim().toLowerCase(Locale.ROOT);
        if (c.equals(CLAVE_PRO)) return PRO;
        if (c.equals(CLAVE_PLUS)) return PLUS;
        return null;
    }

    /** true si la clave es un tier de MOODU (PLUS/PRO) y no un modulo normal. */
    public static boolean esClaveTier(String clave) {
        return deClaveCatalogo(clave) != null;
    }

    /** Clave de catalogo que hay que agregar a la licencia para obtener este tipo. */
    public String claveCatalogo() {
        return switch (this) {
            case GRATIS -> null;
            case PLUS -> CLAVE_PLUS;
            case PRO -> CLAVE_PRO;
        };
    }

    /** Capacidades del core que otorga este tipo (acumulativas por nivel). */
    public Set<String> capacidades() {
        Set<String> caps = new LinkedHashSet<>();
        switch (this) {
            case GRATIS -> { }
            case PLUS -> caps.add(CAP_MODO_CENTRAL);
            case PRO -> {
                caps.add(CAP_MODO_CENTRAL);
                caps.add(CAP_TODOS_MODULOS);
                caps.add(CAP_DISPOSITIVOS_ILIMITADOS);
            }
        }
        return caps;
    }

    public boolean tiene(String capacidad) {
        return capacidad != null && capacidades().contains(capacidad.trim().toUpperCase(Locale.ROOT));
    }

    public boolean maquinasIlimitadas() {
        return tiene(CAP_DISPOSITIVOS_ILIMITADOS);
    }

    public boolean maquinaCentral() {
        return tiene(CAP_MODO_CENTRAL);
    }

    /**
     * Periodicidades admitidas para el plan de este tipo en el catalogo.
     * PLUS es PERMANENTE; PRO es solo MENSUAL o ANUAL (no permanente).
     * El Hub lo valida al guardar el plan del modulo.
     */
    public Set<String> periodicidadesPermitidas() {
        return switch (this) {
            case GRATIS -> Set.of("GRATUITO");
            case PLUS -> Set.of("PERMANENTE");
            case PRO -> Set.of("MENSUAL", "ANUAL");
        };
    }

    /** Compara contra un tipo de plan del catalogo (null si no aplica). */
    public boolean admite(String periodicidad) {
        if (periodicidad == null || periodicidad.isBlank()) return false;
        return periodicidadesPermitidas().contains(periodicidad.trim().toUpperCase(Locale.ROOT));
    }

    public String etiqueta() {
        return switch (this) {
            case GRATIS -> "MOODU Gratis";
            case PLUS -> "MOODU Plus";
            case PRO -> "MOODU Pro";
        };
    }

    public String descripcion() {
        return switch (this) {
            case GRATIS -> "Maquina unica (UNICA) y NODO. No habilita maquina central.";
            case PLUS -> "Todo lo de Gratis + modo maquina central (canal al que se unen los NODOs).";
            case PRO -> "Todo lo de Plus + todos los modulos oficiales y dispositivos ilimitados.";
        };
    }

    public String resumenAlcance() {
        StringBuilder sb = new StringBuilder(etiqueta()).append(": ").append(descripcion());
        sb.append(" | Formas de pago: ")
          .append(periodicidadesPermitidas().isEmpty() ? "gratis" : String.join(" o ", periodicidadesPermitidas()));
        return sb.toString();
    }
}