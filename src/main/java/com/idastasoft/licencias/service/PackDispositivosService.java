package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.Licencia;
import com.idastasoft.licencias.model.LicenciaPack;
import com.idastasoft.licencias.model.PackDispositivos;
import com.idastasoft.licencias.repository.LicenciaPackRepo;
import com.idastasoft.licencias.repository.PackDispositivosRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Packs de dispositivos adicionales (REQ-HUB-PACK-DISP-01).
 *
 * <p>Solo catalogo + calculo: el pack COMPRADO de una licencia vive aqui
 * ({@link LicenciaPack}) y el proceso de compra en
 * {@link LicenciaService#comprarPackDispositivos}, que ya tiene las validaciones
 * de licencia. Aqui no hay dependencia de vuelta (evita ciclo con el service).</p>
 */
@Service
public class PackDispositivosService {

    private final PackDispositivosRepo packRepo;
    private final LicenciaPackRepo licenciaPackRepo;

    public PackDispositivosService(PackDispositivosRepo packRepo, LicenciaPackRepo licenciaPackRepo) {
        this.packRepo = packRepo;
        this.licenciaPackRepo = licenciaPackRepo;
    }

    /** Catalogo para la Tienda: solo los que tienen al menos un precio. */
    public List<PackDispositivos> catalogo() {
        return packRepo.findByActivoTrueOrderByDispositivosAsc().stream()
            .filter(PackDispositivos::offered)
            .toList();
    }

    /** Catalogo completo (admin: incluye los sin precio y los inactivos). */
    public List<PackDispositivos> catalogoCompleto() {
        return packRepo.findAll();
    }

    @Transactional
    public PackDispositivos guardar(PackDispositivos p) {
        if (p.getCodigo() == null || p.getCodigo().isBlank()) {
            throw new IllegalArgumentException("El pack necesita un codigo");
        }
        p.setCodigo(p.getCodigo().trim().toUpperCase());
        if (p.getDispositivos() <= 0) {
            throw new IllegalArgumentException("El pack debe dar al menos 1 dispositivo");
        }
        if (p.getNombre() == null || p.getNombre().isBlank()) {
            p.setNombre(p.getDispositivos() + " dispositivos extra");
        }
        return packRepo.save(p);
    }

    @Transactional
    public void eliminar(String codigo) {
        packRepo.deleteById(codigo.trim().toUpperCase());
    }

    /** Pack del catalogo (o null si el codigo no existe / esta inactivo). */
    @Transactional(readOnly = true)
    public PackDispositivos pack(String codigo) {
        if (codigo == null || codigo.isBlank()) return null;
        return packRepo.findById(codigo.trim().toUpperCase()).orElse(null);
    }

    /** El pack ADQUIRIDO de la licencia (puede estar vencido o inactivo). */
    @Transactional(readOnly = true)
    public LicenciaPack packDe(Licencia l) {
        return licenciaPackRepo.findByLicencia(l).orElse(null);
    }

    /**
     * Dispositivos EXTRA vigentes de la licencia (0 si no tiene pack, si esta
     * inactivo o si el mensual ya vencio).
     */
    @Transactional(readOnly = true)
    public int dispositivosExtraDe(Licencia l) {
        LicenciaPack lp = packDe(l);
        if (lp == null || !lp.vigente()) return 0;
        PackDispositivos p = pack(lp.getCodigo());
        if (p == null || !p.isActivo()) return 0;
        lp.setDispositivos(p.getDispositivos());
        return p.getDispositivos();
    }
}