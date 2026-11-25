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
    private final ParametroService parametros;

    public ModuloPlanService(ModuloPlanRepo repo, ParametroService parametros) {
        this.repo = repo;
        this.parametros = parametros;
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
                if (p.isActivo()) out.add(publico(p, m));
            }
            return out;
        }
        return sintetizarLegacy(m);
    }

    /**
     * H-04: precio PUBLICO canonico de un modulo. Cuando tiene planes propios,
     * el precio que ve el admin y el que ve la Tienda deben ser el MISMO: se toma
     * el plan de entrada (el mas barato vigente) y se ignora el campo legacy,
     * que queda expuesto como {@code precioLegacy} solo para diagnostico.
     *
     * <p>Sin planes propios se usa el legacy (el plan sintetizado ya deriva de el),
     * asi no cambia el contrato de los modulos cargados antes de la fase de planes.</p>
     */
    public BigDecimal precioDeEntrada(List<Map<String, Object>> planes,
                                      ModuloCatalogo modulo,
                                      BigDecimal legacy) {
        BigDecimal viejo = legacy == null ? BigDecimal.ZERO : legacy;
        if (planes == null || planes.isEmpty()) return viejo;
        List<BigDecimal> valores = new java.util.ArrayList<>();
        for (Map<String, Object> p : planes) {
            Object raw = p.get("precioLista") != null ? p.get("precioLista") : p.get("precio");
            if (raw == null) continue;
            try {
                valores.add(new BigDecimal(String.valueOf(raw)));
            } catch (NumberFormatException ignored) {
                // Un plan con precio corrupto no define el precio publico.
            }
        }
        if (valores.isEmpty()) return viejo;
        boolean dePago = modulo != null && modulo.getTipo() == ModuloCatalogo.TipoLicencia.PAGA;
        java.util.Optional<BigDecimal> positivo = valores.stream()
                .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
                .min(BigDecimal::compareTo);
        if (dePago && positivo.isPresent()) return positivo.get();
        return valores.stream().min(BigDecimal::compareTo).orElse(viejo);
    }

    /**
     * Mapa que consume la Tienda. El descuento de temporada se calcula aqui
     * (nunca se reescribe el precio guardado): asi una oferta se activa/caduca
     * por fecha sin tocar la BD.
     */
    private Map<String, Object> publico(ModuloPlan p, ModuloCatalogo modulo) {
        Map<String, Object> o = new LinkedHashMap<>();
        BigDecimal listo = p.getPrecio() == null ? BigDecimal.ZERO : p.getPrecio();
        boolean oferta = modulo != null && modulo.descuentoVigente(java.time.LocalDate.now());
        BigDecimal finalPrecio = modulo == null ? listo : modulo.precioConDescuento(listo, java.time.LocalDate.now());
        o.put("id", p.getId());
        o.put("tipo", p.getTipo().name());
        o.put("precio", finalPrecio);
        o.put("precioLista", listo);
        o.put("descuentoPct", modulo == null || modulo.getDescuentoPct() == null ? 0 : modulo.getDescuentoPct());
        o.put("descuentoVigente", oferta);
        o.put("dispositivosIncluidos", modulo == null ? 0 : modulo.dispositivosIncluidosEfectivos());
        int tope = parametros.maxAniosLicencia();
        o.put("periodos", p.getPeriodos());
        o.put("vigenciaDias", p.vigenciaDias(tope));
        o.put("vigente", p.isActivo());
        o.put("etiqueta", etiqueta(p.getTipo(), p.getPeriodos(), tope));
        o.put("gratuito", listo.compareTo(BigDecimal.ZERO) == 0
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
            int tope = parametros.maxAniosLicencia();
            int dias = Math.max(0, m.getVigenciaDias());
            if (dias == 0 || dias > 365) {
                // Un legacy "permanente" (dias 0) pasa a TRIENAL: ya no se
                // ofrece sin vencimiento para modulo de PAGA (HUB-11).
                o.put("tipo", "TRIENAL");
                o.put("periodos", 1);
                o.put("vigenciaDias", tope * 365);
                o.put("etiqueta", etiqueta(ModuloPlan.TipoPlan.TRIENAL, 1, tope));
            } else if (dias >= 300) {
                int anios = Math.min(tope, Math.max(1, Math.round(dias / 365f)));
                o.put("tipo", "ANUAL");
                o.put("periodos", anios);
                o.put("vigenciaDias", anios * 365);
                o.put("etiqueta", etiqueta(ModuloPlan.TipoPlan.ANUAL, anios, tope));
            } else {
                int meses = Math.min(tope * 12, Math.max(1, Math.round(dias / 30f)));
                o.put("tipo", "MENSUAL");
                o.put("periodos", meses);
                o.put("vigenciaDias", meses * 30);
                o.put("etiqueta", etiqueta(ModuloPlan.TipoPlan.MENSUAL, meses, tope));
            }
            BigDecimal listo = m.getPrecio() != null ? m.getPrecio() : BigDecimal.ZERO;
            o.put("precio", m.precioConDescuento(listo, java.time.LocalDate.now()));
            o.put("precioLista", listo);
            o.put("descuentoPct", m.getDescuentoPct() == null ? 0 : m.getDescuentoPct());
            o.put("descuentoVigente", m.descuentoVigente(java.time.LocalDate.now()));
            o.put("dispositivosIncluidos", m.dispositivosIncluidosEfectivos());
            o.put("gratuito", false);
        }
        out.add(o);
        return out;
    }

    private String etiqueta(ModuloPlan.TipoPlan t, int periodos, int maxAnios) {
        int p = Math.max(1, periodos);
        int tope = Math.max(1, maxAnios);
        return switch (t) {
            case PERMANENTE -> "Permanente";
            case TRIENAL -> "Plan de " + tope + " años";
            case ANUAL -> p == 1 ? "Anual" : "Anual x" + p;
            case MENSUAL -> p == 1 ? "Mensual" : "Mensual x" + p;
        };
    }

    // ─── ADMIN ───

    @Transactional
    public List<Map<String, Object>> listarAdmin(ModuloCatalogo m) {
        return repo.findByModuloIdOrderByOrdenAscIdAsc(m.getId()).stream().map(p -> publico(p, m)).toList();
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
        if (!tier.admiteAlta(tipo.name())) {
            throw new IllegalArgumentException(
                "MOODU " + tier.name() + " solo admite "
                + String.join(" o ", tier.periodicidadesAlta())
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
        int tope = parametros.maxAniosLicencia();
        // HUB-11: ningun plan de pago puede superar el tope de anios. El
        // servidor es la verdad; el panel solo espeja el motivo.
        if (tipo == ModuloPlan.TipoPlan.ANUAL && periodos > tope) {
            throw new IllegalArgumentException("El plan Anual no puede superar " + tope
                    + " años (tope configurado en el Hub).");
        }
        if (tipo == ModuloPlan.TipoPlan.MENSUAL && periodos > tope * 12) {
            throw new IllegalArgumentException("El plan Mensual no puede superar " + tope
                    + " años (tope configurado en el Hub).");
        }
        BigDecimal precio = precioDe(body.get("precio"));

        List<ModuloPlan> delModulo = repo.findByModuloIdOrderByOrdenAscIdAsc(modulo.getId());

        // El precio ANUAL no se escribe a mano (REQ-HUBPAQ-01): se deriva del
        // MENSUAL con el factor que configuro el admin. Si no hay mensual, se
        // rechaza con motivo claro en vez de guardar un precio inventado.
        if (tipo == ModuloPlan.TipoPlan.ANUAL) {
            ModuloPlan mensual = delModulo.stream()
                    .filter(x -> x.getTipo() == ModuloPlan.TipoPlan.MENSUAL).findFirst().orElse(null);
            if (mensual == null || mensual.getPrecio() == null) {
                throw new IllegalArgumentException(
                        "Fije primero el precio MENSUAL del modulo: el precio ANUAL se calcula solo.");
            }
            precio = anualDe(mensual.getPrecio());
        }

        ModuloPlan p = delModulo.stream().filter(x -> x.getTipo() == tipo).findFirst().orElse(null);
        if (p == null) {
            p = new ModuloPlan();
            p.setModulo(modulo);
            p.setOrden(delModulo.size());
        }
        p.setTipo(tipo);
        p.setPeriodos(tipo == ModuloPlan.TipoPlan.PERMANENTE || tipo == ModuloPlan.TipoPlan.TRIENAL
                ? 1 : Math.max(1, periodos));
        p.setPrecio(precio);
        p.setActivo(!Boolean.FALSE.equals(body.get("activo")));
        ModuloPlan guardado = repo.save(p);

        // Guardar el MENSUAL genera/actualiza el ANUAL en la misma transaccion.
        if (tipo == ModuloPlan.TipoPlan.MENSUAL) {
            sincronizarAnual(modulo, delModulo, precio, p.isActivo());
        }
        return publico(guardado, modulo);
    }

    /** anual = mensual × factor, 2 decimales HALF_UP. */
    private BigDecimal anualDe(BigDecimal mensual) {
        return mensual.multiply(BigDecimal.valueOf(parametros.factorAnual()))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Upsert del plan ANUAL derivado; nunca llama a guardar() (evita recursion). */
    private void sincronizarAnual(ModuloCatalogo modulo, List<ModuloPlan> delModulo,
                                  BigDecimal mensual, boolean activo) {
        ModuloPlan anual = delModulo.stream()
                .filter(x -> x.getTipo() == ModuloPlan.TipoPlan.ANUAL).findFirst().orElse(null);
        if (anual == null) {
            anual = new ModuloPlan();
            anual.setModulo(modulo);
            anual.setOrden(delModulo.size() + 1);
        }
        anual.setTipo(ModuloPlan.TipoPlan.ANUAL);
        anual.setPeriodos(1);
        anual.setPrecio(anualDe(mensual));
        anual.setActivo(activo);
        repo.save(anual);
    }

    @Transactional
    public boolean eliminar(Long moduloId, Long planId) {
        ModuloPlan p = repo.findById(planId).orElse(null);
        if (p == null || !p.getModulo().getId().equals(moduloId)) return false;
        repo.delete(p);
        return true;
    }

    private ModuloPlan.TipoPlan parseTipo(Object v) {
        ModuloPlan.TipoPlan t;
        try {
            t = ModuloPlan.TipoPlan.valueOf(String.valueOf(v).toUpperCase().trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Plan invalido: debe ser MENSUAL, ANUAL o TRIENAL");
        }
        // PERMANENTE se conserva para leer BDs viejas y modulos GRATIS, pero
        // ya NO se puede crear: es la decision comercial de HUB-11.
        if (t == ModuloPlan.TipoPlan.PERMANENTE) {
            throw new IllegalArgumentException("Ya no se ofrece el plan Permanente: use TRIENAL (plan de "
                    + parametros.maxAniosLicencia() + " años).");
        }
        return t;
    }

    /**
     * Migration HUB-11: los planes PERMANENTE de modulos de PAGA pasan a
     * TRIENAL conservando precio y orden. Idempotente (los GRATIS se saltan).
     */
    @Transactional
    public int convertirPermanentesDePago() {
        int n = 0;
        for (ModuloPlan p : repo.findAll()) {
            if (p.getTipo() != ModuloPlan.TipoPlan.PERMANENTE) continue;
            ModuloCatalogo m = p.getModulo();
            if (m != null && m.getTipo() == ModuloCatalogo.TipoLicencia.GRATIS) continue;
            p.setTipo(ModuloPlan.TipoPlan.TRIENAL);
            p.setPeriodos(1);
            repo.save(p);
            n++;
        }
        return n;
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