package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.ModuloCatalogo;
import com.idastasoft.licencias.model.ModuloPlan;
import com.idastasoft.licencias.model.TipoMoodu;
import com.idastasoft.licencias.repository.ModuloPlanRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Planes de un modulo del catalogo (REQ-HUB-PLANES-01).
 *
 * <p>Cada plan tiene su PROPIO precio: el modulo no tiene un precio unico.
 * Si el modulo no tiene planes cargados, se sintetiza uno desde los campos
 * legacy (tipo/precio/vigenciaDias) para que lo ya cargado siga
 * funcionando sin migracion.</p>
 */
@Service
public class ModuloPlanService {

    private final ModuloPlanRepo repo;

    public ModuloPlanService(ModuloPlanRepo repo) {
        this.repo = repo;
    }

    /**
     * Planes del modulo tal como los ve la Tienda.
     * Sin filas propias -> se sintetiza uno desde los campos legacy.
     */
    public List<Map<String, Object>> planesPublicos(Long moduloId, ModuloCatalogo m) {
        List<ModuloPlan> filas = repo.findByModuloIdOrderByOrdenAscIdAsc(moduloId);
        if (!filas.isEmpty()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (ModuloPlan p : filas) {
                if (p.isActivo()) out.add(publico(p));
            }
            return out;
        }
        return sintetizarLegacy(m);
    }

    private Map<String, Object> publico(ModuloPlan p) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", p.getId());
        o.put("tipo", p.getTipo().name());
        o.put("precio", p.getPrecio());
        o.put("periodos", p.getPeriodos());
        o.put("vigenciaDias", p.vigenciaDias());
        o.put("vigente", p.isActivo());
        o.put("etiqueta", etiqueta(p.getTipo(), p.getPeriodos()));
        o.put("gratuito", p.getPrecio() != null
                && p.getPrecio().compareTo(BigDecimal.ZERO) == 0
                && p.getTipo() != ModuloPlan.TipoPlan.PERMANENTE);
        return o;
    }

    /** Un plan unico deducido de los campos legacy del modulo. */
    private List<Map<String, Object>> sintetizarLegacy(ModuloCatalogo m) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (m == null) return out;
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", null);
        o.put("periodos", 1);
        o.put("vigente", true);
        if (m.getTipo() == ModuloCatalogo.TipoLicencia.GRATIS) {
            // Gratis = para siempre (decision del usuario 2026-10-16).
            o.put("tipo", "PERMANENTE");
            o.put("precio", BigDecimal.ZERO);
            o.put("vigenciaDias", 0);
            o.put("etiqueta", "Gratuito");
            o.put("gratuito", true);
        } else {
            int dias = Math.max(0, m.getVigenciaDias());
            if (dias == 0) {
                o.put("tipo", "PERMANENTE");
                o.put("periodos", 1);
                o.put("vigenciaDias", 0);
                o.put("etiqueta", "Permanente");
            } else if (dias >= 300) {
                int anios = Math.max(1, Math.round(dias / 365f));
                o.put("tipo", "ANUAL");
                o.put("periodos", anios);
                o.put("vigenciaDias", anios * 365);
                o.put("etiqueta", etiqueta(ModuloPlan.TipoPlan.ANUAL, anios));
            } else {
                int meses = Math.max(1, Math.round(dias / 30f));
                o.put("tipo", "MENSUAL");
                o.put("periodos", meses);
                o.put("vigenciaDias", meses * 30);
                o.put("etiqueta", etiqueta(ModuloPlan.TipoPlan.MENSUAL, meses));
            }
            o.put("precio", m.getPrecio() != null ? m.getPrecio() : BigDecimal.ZERO);
            o.put("gratuito", false);
        }
        out.add(o);
        return out;
    }

    private String etiqueta(ModuloPlan.TipoPlan t, int periodos) {
        int p = Math.max(1, periodos);
        return switch (t) {
            case PERMANENTE -> "Permanente";
            case ANUAL -> p == 1 ? "Anual" : "Anual x" + p;
            case MENSUAL -> p == 1 ? "Mensual" : "Mensual x" + p;
        };
    }

    // ─── ADMIN ───

    @Transactional
    public List<Map<String, Object>> listarAdmin(ModuloCatalogo m) {
        return repo.findByModuloIdOrderByOrdenAscIdAsc(m.getId()).stream().map(this::publico).toList();
    }

    /**
     * Un modulo que es un TIER de MOODU ({@code moodu_plus} / {@code moodu_pro})
     * solo admite las periodicidades de su tipo: PLUS es permanente y PRO es
     * mensual o anual (nunca permanente, §11.91). Un modulo normal no se
     * restringe.
     *
     * @throws IllegalArgumentException con el motivo (lo ve el panel)
     */
    private void exigirPeriodicidadDelTier(ModuloCatalogo modulo, ModuloPlan.TipoPlan tipo) {
        TipoMoodu tier = modulo == null ? null : TipoMoodu.deClaveCatalogo(modulo.getClave());
        if (tier == null) return;
        if (!tier.admite(tipo.name())) {
            throw new IllegalArgumentException(
                "MOODU " + tier.name() + " solo admite "
                + String.join(" o ", tier.periodicidadesPermitidas())
                + " (no " + tipo.name() + ").");
        }
    }

    /**
     * Crea o actualiza un plan. No deja dos planes del mismo tipo en un
     * modulo: si ya existe ese tipo se edita el existente (el admin no
     * necesita saber si existia).
     *
     * <p>{@code noRollbackFor}: un rechazo de validacion no es un fallo que
     * deba deshacer la transaccion (§11.442). Sin esto el panel recibia un 500
     * en vez del 400 con el motivo.</p>
     */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public Map<String, Object> guardar(ModuloCatalogo modulo, Map<String, Object> body) {
        ModuloPlan.TipoPlan tipo = parseTipo(body.get("tipo"));
        exigirPeriodicidadDelTier(modulo, tipo);
        int periodos = intDe(body.get("periodos"), 1);
        BigDecimal precio = precioDe(body.get("precio"));

        List<ModuloPlan> delModulo = repo.findByModuloIdOrderByOrdenAscIdAsc(modulo.getId());
        ModuloPlan p = delModulo.stream().filter(x -> x.getTipo() == tipo).findFirst().orElse(null);
        if (p == null) {
            p = new ModuloPlan();
            p.setModulo(modulo);
            p.setOrden(delModulo.size());
        }
        p.setTipo(tipo);
        p.setPeriodos(tipo == ModuloPlan.TipoPlan.PERMANENTE ? 1 : Math.max(1, periodos));
        p.setPrecio(precio);
        p.setActivo(!Boolean.FALSE.equals(body.get("activo")));
        return publico(repo.save(p));
    }

    @Transactional
    public boolean eliminar(Long moduloId, Long planId) {
        ModuloPlan p = repo.findById(planId).orElse(null);
        if (p == null || !p.getModulo().getId().equals(moduloId)) return false;
        repo.delete(p);
        return true;
    }

    private ModuloPlan.TipoPlan parseTipo(Object v) {
        try {
            return ModuloPlan.TipoPlan.valueOf(String.valueOf(v).toUpperCase().trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Plan invalido: debe ser MENSUAL, ANUAL o PERMANENTE");
        }
    }

    private int intDe(Object v, int def) {
        if (v == null) return def;
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private BigDecimal precioDe(Object v) {
        if (v == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Precio invalido");
        }
    }
}