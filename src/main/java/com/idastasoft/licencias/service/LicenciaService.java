package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.Licencia;
import com.idastasoft.licencias.model.Maquina;
import com.idastasoft.licencias.repository.LicenciaRepo;
import com.idastasoft.licencias.repository.MaquinaRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class LicenciaService {

    private final LicenciaRepo licenciaRepo;
    private final MaquinaRepo maquinaRepo;

    public LicenciaService(LicenciaRepo licenciaRepo, MaquinaRepo maquinaRepo) {
        this.licenciaRepo = licenciaRepo;
        this.maquinaRepo = maquinaRepo;
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
            "modulos", l.getModulos(),
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
