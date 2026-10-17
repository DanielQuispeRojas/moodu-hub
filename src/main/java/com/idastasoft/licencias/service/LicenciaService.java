package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.Licencia;
import com.idastasoft.licencias.model.LicenciaModulo;
import com.idastasoft.licencias.model.Maquina;
import com.idastasoft.licencias.model.ModuloCatalogo;
import com.idastasoft.licencias.repository.LicenciaModuloRepo;
import com.idastasoft.licencias.repository.LicenciaRepo;
import com.idastasoft.licencias.repository.MaquinaRepo;
import com.idastasoft.licencias.repository.ModuloCatalogoRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LicenciaService {

    private final LicenciaRepo licenciaRepo;
    private final MaquinaRepo maquinaRepo;
    private final LicenciaModuloRepo terminoRepo;
    private final PlanComprapeService planComprapeService;
    private final ModuloCatalogoRepo moduloRepo;

    public LicenciaService(LicenciaRepo licenciaRepo, MaquinaRepo maquinaRepo, LicenciaModuloRepo terminoRepo,
                           PlanComprapeService planComprapeService, ModuloCatalogoRepo moduloRepo) {
        this.licenciaRepo = licenciaRepo;
        this.maquinaRepo = maquinaRepo;
        this.terminoRepo = terminoRepo;
        this.planComprapeService = planComprapeService;
        this.moduloRepo = moduloRepo;
    }

    /**
     * Estado del marketplace Comprape de una licencia (docs/comprape.md 4.2, R8/R9).
     * SOLO LECTURA como consultarEstado(): no registra ni activa maquina alguna, para
     * que MOODU lo pueda consultar seguido (el countdown de 15 dias se refresca sin
     *VALIDAR y sin consumir cupo de maquinas).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> estadoComprape(String correo, String clave) {
        Licencia l = licenciaRepo.findByCorreoAndClaveActivacion(correo, clave).orElse(null);
        if (l == null) {
            return Map.of("ok", false, "mensaje",
                "Clave invalida o licencia no activada. Comuniquese con su proveedor o adquiera una en https://moodu.com");
        }
        if (!l.isActiva()) {
            return Map.of("ok", false, "mensaje", "La licencia esta desactivada. Contacte a su proveedor.");
        }
        if (!estaVigente(l)) {
            return Map.of("ok", false, "mensaje", "La licencia ha expirado. Renuevela para continuar.");
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("mensaje", "Plan Comprape consultado");
        res.put("correo", l.getCorreo());
        res.putAll(planComprapeService.estadoDe(l));
        return res;
    }

    /** Calcula la fecha de expiracion de la licencia. */
    public LocalDateTime calcularExpiracion(Licencia l) {
        if (l.getVigenciaUnidad() == Licencia.UnidadVigencia.INDEFINIDO) {
            return null;
        }
        LocalDateTime inicio = l.getFechaInicio() != null ? l.getFechaInicio() : LocalDateTime.now();
        return switch (l.getVigenciaUnidad()) {
            case DIAS -> inicio.plusDays(l.getVigenciaValor());
            case MESES -> inicio.plusMonths(l.getVigenciaValor());
            case ANIO -> inicio.plusYears(l.getVigenciaValor());
            default -> null;
        };
    }

    public boolean estaVigente(Licencia l) {
        if (!l.isActiva()) return false;
        LocalDateTime exp = calcularExpiracion(l);
        if (exp == null) return true; // indefinido
        return LocalDateTime.now().isBefore(exp);
    }

    // ─── TERMINOS POR MODULO (REQ-HUB-MODULOS-01) ─────────
    /** Expiracion del termino (null = permanente). */
    public LocalDateTime expiracionTermino(LicenciaModulo t) {
        if (t.getPlan() == LicenciaModulo.PlanModulo.PERMANENTE) return null;
        LocalDateTime inicio = t.getFechaInicio() != null ? t.getFechaInicio() : LocalDateTime.now();
        int n = Math.max(1, t.getPeriodos());
        return t.getPlan() == LicenciaModulo.PlanModulo.ANUAL ? inicio.plusYears(n) : inicio.plusMonths(n);
    }

    public boolean terminoVigente(LicenciaModulo t) {
        LocalDateTime exp = expiracionTermino(t);
        if (exp == null) return true;
        return LocalDateTime.now().isBefore(exp);
    }

    /**
     * Activa un modulo GRATUITO pedido desde la Tienda del desktop
     * (REQ-HUB-ACTIVA-GRATIS-01).
     *
     * <p><b>El Hub es quien valida que sea gratis</b>: no se fia de lo que
     * diga el cliente. Si el modulo no existe, esta inactivo o es de PAGA,
     * devuelve el error y no toca la licencia.</p>
     *
     * <p>Un modulo gratuito se otorga <b>PERMANENTE</b> (decision del
     * usuario 2026-10-16: "lo gratis es tuyo"). Si ya estaba, no hace
     * nada y responde ok (idempotente).</p>
     *
     * <p>No registra ni consume maquina: no es una activacion de equipo.</p>
     */
    @Transactional
    public Map<String, Object> activarModuloGratuito(String correo, String clave, String moduloClave) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("ok", false);

        if (moduloClave == null || moduloClave.isBlank()) {
            error.put("mensaje", "Indica el modulo que quieres activar.");
            return error;
        }
        Licencia l = licenciaRepo.findByCorreoAndClaveActivacion(correo, clave).orElse(null);
        if (l == null) {
            error.put("mensaje", "Clave invalida o licencia no activada. Comuniquese con su proveedor o adquiera una en https://moodu.com");
            return error;
        }
        if (!l.isActiva()) {
            error.put("mensaje", "La licencia esta desactivada. Contacte a su proveedor.");
            return error;
        }
        if (!estaVigente(l)) {
            error.put("mensaje", "La licencia ha expirado. Renuevela para continuar.");
            return error;
        }

        String claveNorm = moduloClave.trim().toLowerCase();
        ModuloCatalogo m = moduloRepo.findByClave(claveNorm);
        if (m == null || !m.isActivo()) {
            error.put("mensaje", "Ese modulo no existe en el catalogo de MOODU.");
            return error;
        }
        if (m.getTipo() != ModuloCatalogo.TipoLicencia.GRATIS) {
            // No lo Activamos de a poco: un modulo de paga se compra con soporte.
            error.put("mensaje", "El modulo " + m.getNombre() + " es de pago; su activacion la realiza el soporte de MOODU.");
            return error;
        }
        if (m.getPrecio() != null && m.getPrecio().compareTo(BigDecimal.ZERO) > 0) {
            error.put("mensaje", "El modulo " + m.getNombre() + " tiene precio (" + m.getPrecio()
                    + "); su activacion la realiza el soporte de MOODU.");
            return error;
        }

        // Idempotente: si ya esta en la lista legacy y con termino, no duplica.
        List<String> mods = l.getModulos() != null ? new ArrayList<>(l.getModulos()) : new ArrayList<>();
        boolean nuevoLegacy = !mods.contains(claveNorm);
        if (nuevoLegacy) {
            mods.add(claveNorm);
            l.setModulos(mods);
            licenciaRepo.save(l);
        }
        LicenciaModulo t = terminoRepo.findByLicenciaIdAndModuloClave(l.getId(), claveNorm).orElse(null);
        if (t == null) {
            t = new LicenciaModulo();
            t.setLicencia(l);
            t.setModuloClave(claveNorm);
            t.setPlan(LicenciaModulo.PlanModulo.PERMANENTE);
            t.setPeriodos(1);
            t.setFechaInicio(LocalDateTime.now());
            terminoRepo.save(t);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("mensaje", nuevoLegacy
                ? "Modulo " + m.getNombre() + " activado en tu licencia."
                : "El modulo " + m.getNombre() + " ya estaba activado en tu licencia.");
        out.put("modulo", claveNorm);
        out.put("agregado", nuevoLegacy);
        out.put("modulos", modulosHabilitados(l));
        out.put("modulosDetalle", detallesModulos(l));
        return out;
    }

    /** Detalle por modulo para el modal "ver" (clave, plan, expiracion, vigente). */
    public List<Map<String, Object>> detallesModulos(Licencia l) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LicenciaModulo t : terminoRepo.findByLicenciaId(l.getId())) {
            LocalDateTime exp = expiracionTermino(t);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clave", t.getModuloClave());
            m.put("plan", t.getPlan().name());
            m.put("periodos", t.getPeriodos());
            m.put("fechaInicio", t.getFechaInicio() != null ? t.getFechaInicio().toString() : null);
            m.put("expiracion", exp != null ? exp.toString() : "PERMANENTE");
            m.put("vigente", terminoVigente(t));
            // Lo que la Tienda muestra al lado del boton "Mejorar Plan"
            // (REQ-HUB-PLANES-01): cuanto le queda de licencia a ESTE modulo.
            if (exp == null) {
                m.put("diasRestantes", -1);      // -1 = permanente (no caduca)
                m.put("mesesRestantes", -1);
            } else {
                long dias = ChronoUnit.DAYS.between(LocalDateTime.now(), exp);
                m.put("diasRestantes", Math.max(0, dias));
                m.put("mesesRestantes", Math.max(0, dias / 30));
            }
            out.add(m);
        }
        return out;
    }

    /**
     * Claves habilitadas: licencia vigente Y termino vigente por modulo.
     * Sin filas de terminos (licencia aun no migrada) vale la lista legacy.
     */
    public List<String> modulosHabilitados(Licencia l) {
        List<LicenciaModulo> terms = terminoRepo.findByLicenciaId(l.getId());
        if (terms.isEmpty()) {
            return l.getModulos() != null ? new ArrayList<>(l.getModulos()) : new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (LicenciaModulo t : terms) {
            if (terminoVigente(t) && !out.contains(t.getModuloClave())) out.add(t.getModuloClave());
        }
        return out;
    }

    /** Reemplaza los terminos de la licencia (edicion/admin). */
    @Transactional
    public void guardarTerminos(Licencia l, List<Map<String, Object>> detalles) {
        List<LicenciaModulo> nuevos = new ArrayList<>();
        if (detalles != null) {
            for (Map<String, Object> d : detalles) {
                Object clave = d.get("clave");
                if (clave == null || String.valueOf(clave).isBlank()) continue;
                LicenciaModulo t = new LicenciaModulo();
                t.setModuloClave(String.valueOf(clave).trim());
                try {
                    t.setPlan(LicenciaModulo.PlanModulo.valueOf(
                            String.valueOf(d.getOrDefault("plan", "MENSUAL")).toUpperCase()));
                } catch (IllegalArgumentException e) {
                    t.setPlan(LicenciaModulo.PlanModulo.MENSUAL);
                }
                try {
                    t.setPeriodos(Math.max(1, Integer.parseInt(String.valueOf(d.getOrDefault("periodos", 1)))));
                } catch (NumberFormatException e) {
                    t.setPeriodos(1);
                }
                Object ini = d.get("fechaInicio");
                if (ini != null && !String.valueOf(ini).isBlank()) {
                    try {
                        t.setFechaInicio(LocalDateTime.parse(String.valueOf(ini).substring(0, 19)));
                    } catch (Exception e) {
                        t.setFechaInicio(LocalDateTime.now());
                    }
                } else {
                    t.setFechaInicio(LocalDateTime.now());
                }
                nuevos.add(t);
            }
        }
        reemplazarTerminos(l, nuevos);
    }

    /** Terminos que heredan la vigencia global (alta rapida / compat). */
    @Transactional
    public void heredarTerminos(Licencia l, List<String> claves) {
        List<LicenciaModulo> nuevos = new ArrayList<>();
        if (claves != null) {
            for (String clave : claves) {
                if (clave == null || clave.isBlank()) continue;
                LicenciaModulo t = new LicenciaModulo();
                t.setModuloClave(clave.trim());
                if (l.getVigenciaUnidad() == Licencia.UnidadVigencia.INDEFINIDO) {
                    t.setPlan(LicenciaModulo.PlanModulo.PERMANENTE);
                    t.setPeriodos(1);
                } else if (l.getVigenciaUnidad() == Licencia.UnidadVigencia.ANIO) {
                    t.setPlan(LicenciaModulo.PlanModulo.ANUAL);
                    t.setPeriodos(Math.max(1, l.getVigenciaValor()));
                } else {
                    // DIAS se aproxima a MENSUAL (minimo un mes); MESES directo.
                    t.setPlan(LicenciaModulo.PlanModulo.MENSUAL);
                    t.setPeriodos(l.getVigenciaUnidad() == Licencia.UnidadVigencia.DIAS ? 1
                            : Math.max(1, l.getVigenciaValor()));
                }
                t.setFechaInicio(l.getFechaInicio() != null ? l.getFechaInicio() : LocalDateTime.now());
                nuevos.add(t);
            }
        }
        reemplazarTerminos(l, nuevos);
    }

    /**
     * Reconcilia terminos sin delete+insert de la misma clave en una tx
     * (con IDENTITY el insert es inmediato y chocaba con el unique aunque el
     * borrado estuviera flusheado): lo que existe se ACTUALIZA, solo se
     * inserta lo nuevo y se borra lo que sale.
     */
    private void reemplazarTerminos(Licencia l, List<LicenciaModulo> nuevos) {
        Map<String, LicenciaModulo> actuales = new LinkedHashMap<>();
        for (LicenciaModulo t : terminoRepo.findByLicenciaId(l.getId())) {
            actuales.put(t.getModuloClave(), t);
        }
        java.util.Set<String> pedidas = new java.util.LinkedHashSet<>();
        for (LicenciaModulo n : nuevos) {
            if (n.getModuloClave() == null || n.getModuloClave().isBlank()) continue;
            pedidas.add(n.getModuloClave());
            LicenciaModulo ex = actuales.get(n.getModuloClave());
            if (ex != null) {
                ex.setPlan(n.getPlan());
                ex.setPeriodos(n.getPeriodos());
                ex.setFechaInicio(n.getFechaInicio());
            } else {
                n.setLicencia(l);
                terminoRepo.save(n);
                try {
                    l.getDetalles().add(n);
                } catch (Exception ignored) {
                    // l detached (runner): la coleccion se descarta, el save manda.
                }
            }
        }
        for (Map.Entry<String, LicenciaModulo> e : actuales.entrySet()) {
            if (!pedidas.contains(e.getKey())) {
                terminoRepo.delete(e.getValue());
                try {
                    l.getDetalles().remove(e.getValue());
                } catch (Exception ignored) {
                }
            }
        }
        terminoRepo.flush();
    }

    /**
     * Valida correo + clave de activacion. Si es valida y hay cupo de maquinas,
     * registra/activa la maquina y devuelve los datos de licencia.
     * Devuelve un Map con ok, mensaje y (si ok) modulos, maquinas, expiracion.
     */
    @Transactional
    public Map<String, Object> validar(String correo, String clave, String idHardware, String nombreEquipo) {
        return validar(correo, clave, idHardware, nombreEquipo, false);
    }

    /**
     * @param forzarActivacion si true, reactiva una maquina previamente desligada
     *        (solo debe enviarlo el desktop cuando el usuario acaba de ingresar correo+clave).
     */
    @Transactional
    public Map<String, Object> validar(String correo, String clave, String idHardware,
                                     String nombreEquipo, boolean forzarActivacion) {
        Licencia l = licenciaRepo.findByCorreoAndClaveActivacion(correo, clave).orElse(null);
        if (l == null) {
            return Map.of("ok", false, "mensaje",
                "Clave invalida o licencia no activada. Comuniquese con su proveedor o adquiera una en https://moodu.com");
        }
        if (!l.isActiva()) {
            return Map.of("ok", false, "mensaje", "La licencia esta desactivada. Contacte a su proveedor.");
        }
        if (!estaVigente(l)) {
            return Map.of("ok", false, "mensaje", "La licencia ha expirado. Renuevela para continuar.");
        }

        long activas = maquinaRepo.countByLicenciaIdAndActivaTrue(l.getId());
        Maquina m = maquinaRepo.findByLicenciaIdAndIdHardware(l.getId(), idHardware).orElse(null);
        if (m == null) {
            if (activas >= l.getMaquinasPermitidas()) {
                return Map.of("ok", false, "mensaje",
                    "Ha superado el numero de maquinas permitidas (" + l.getMaquinasPermitidas()
                        + "). Desligue una maquina en su cuenta de MOODU o amplie su licencia.");
            }
            m = new Maquina();
            m.setLicencia(l);
            m.setIdHardware(idHardware);
            m.setNombreEquipo(nombreEquipo);
            m.setActiva(true);
            maquinaRepo.save(m);
        } else {
            // Si el admin la desactivo (robo/venta), NO la reactivamos solo (la
            // revalidacion silenciosa del desktop la dejara en false -> cierra sesion).
            // Solo se reactiva si el usuario acaba de ingresar correo+clave (forzarActivacion).
            if (!m.isActiva() && !forzarActivacion) {
                return Map.of("ok", false, "mensaje",
                    "Esta maquina fue desligada de la licencia. Vuelva a ingresar su correo y clave de activacion para reactivarla.");
            }
            m.setActiva(true);
            m.setNombreEquipo(nombreEquipo);
            maquinaRepo.save(m);
        }

        return Map.of(
            "ok", true,
            "mensaje", "Licencia valida",
            "correo", l.getCorreo(),
            "modulos", modulosHabilitados(l),
            "modulosDetalle", detallesModulos(l),
            "maquinasPermitidas", l.getMaquinasPermitidas(),
            "maquinasActivas", maquinaRepo.countByLicenciaIdAndActivaTrue(l.getId()),
            "expiracion", calcularExpiracion(l) != null ? calcularExpiracion(l).toString() : "INDEFINIDO",
            "vigente", true
        );
    }

    /**
     * Consulta el estado de una licencia SIN registrar ni activar maquina alguna.
     * Es el equivalente de "validar()" pero de SOLO LECTURA: no crea fila Maquina
     * (antes el endpoint /estado usaba validar() con idHardware "ping", que llenaba
     * el cupo de maquinas con entradas basura).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> consultarEstado(String correo, String clave) {
        Licencia l = licenciaRepo.findByCorreoAndClaveActivacion(correo, clave).orElse(null);
        if (l == null) {
            return Map.of("ok", false, "mensaje",
                "Clave invalida o licencia no activada. Comuniquese con su proveedor o adquiera una en https://moodu.com");
        }
        if (!l.isActiva()) {
            return Map.of("ok", false, "mensaje", "La licencia esta desactivada. Contacte a su proveedor.");
        }
        if (!estaVigente(l)) {
            return Map.of("ok", false, "mensaje", "La licencia ha expirado. Renuevela para continuar.");
        }
        return Map.of(
            "ok", true,
            "mensaje", "Licencia valida",
            "correo", l.getCorreo(),
            "modulos", modulosHabilitados(l),
            "modulosDetalle", detallesModulos(l),
            "maquinasPermitidas", l.getMaquinasPermitidas(),
            "maquinasActivas", maquinaRepo.countByLicenciaIdAndActivaTrue(l.getId()),
            "expiracion", calcularExpiracion(l) != null ? calcularExpiracion(l).toString() : "INDEFINIDO",
            "vigente", true
        );
    }

    /** Desactiva una maquina (robo/venta). Devuelve true si estaba activa. */
    @Transactional
    public boolean desactivarMaquina(Long licenciaId, String idHardware) {
        Maquina m = maquinaRepo.findByLicenciaIdAndIdHardware(licenciaId, idHardware).orElse(null);
        if (m == null) return false;
        m.setActiva(false);
        maquinaRepo.save(m);
        return true;
    }

    public List<Maquina> maquinasDeLicencia(Long licenciaId) {
        return maquinaRepo.findByLicenciaId(licenciaId);
    }

    public Licencia guardar(Licencia l) { return licenciaRepo.save(l); }
    public Licencia buscarPorCorreo(String correo) { return licenciaRepo.findByCorreo(correo).orElse(null); }
}
