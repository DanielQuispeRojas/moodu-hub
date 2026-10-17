package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.Licencia;
import com.idastasoft.licencias.model.PlanComprape;
import com.idastasoft.licencias.model.PlanComprape.NivelComprape;
import com.idastasoft.licencias.repository.LicenciaRepo;
import com.idastasoft.licencias.repository.PlanComprapeRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plan del marketplace Comprape de una licencia (docs/comprape.md 4.2, R8/R9).
 *
 * <p><b>Frontera:</b> el Hub es la verdad COMERCIAL (que plan tiene, cuanto cupo le
 * queda, cuando puede volver a cambiar la lista). MOODU solo lo muestra y jamas lo
 * decide; Comprape es quien acepta o rechaza la publicacion.</p>
 *
 * <p><b>Sin fila = GRATUITO:</b> una licencia que nunca se configuro tiene el plan
 * por defecto (fail-safe: nadie publica 20 items de la nada esperando permiso).</p>
 */
@Service
public class PlanComprapeService {

    private final PlanComprapeRepo repo;
    private final LicenciaRepo licenciaRepo;

    public PlanComprapeService(PlanComprapeRepo repo, LicenciaRepo licenciaRepo) {
        this.repo = repo;
        this.licenciaRepo = licenciaRepo;
    }

    /** Estado que lee MOODU (solo lectura, nunca inventa). */
    @Transactional(readOnly = true)
    public Map<String, Object> estadoDe(Licencia licencia) {
        PlanComprape p = licencia != null
                ? repo.findByLicenciaId(licencia.getId()).orElse(null)
                : null;
        // Sin fila: gratuito con todo el cupo y sin ventana activa.
        NivelComprape efectivo = p != null ? p.planEfectivo() : NivelComprape.GRATUITO;
        //OJO: las dos ramas del ternario deben ser del MISMO tipo (Integer). Si una
        //rama es int y la otra Integer, Java hace unboxing numerico y con PLUS
        //(limite null) esto revienta con NullPointerException al leer el estado.
        Integer limite = (p != null) ? p.limiteEfectivo() : Integer.valueOf(PlanComprape.LIMITE_GRATUITO);
        int publicados = p != null ? p.getItemsPublicados() : 0;
        long dias = p != null ? p.diasParaCambiar() : 0L;
        LocalDate proxima = p != null ? p.getProximaActualizacion() : null;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plan", efectivo.name());
        m.put("planContratado", (p != null ? p.getPlan() : NivelComprape.GRATUITO).name());
        m.put("vigenciaHasta", (p != null ? p.getVigenciaHasta() : null) != null
                ? p.getVigenciaHasta().toString() : null);
        m.put("itemsPublicados", publicados);
        m.put("itemsLimite", limite);
        m.put("itemsDisponibles", (limite != null)
                ? Integer.valueOf(Math.max(0, limite.intValue() - publicados))
                : null);
        m.put("proximaActualizacion", proxima != null ? proxima.toString() : null);
        m.put("diasParaCambiar", dias);
        m.put("listaCongelada", dias > 0);
        //El precio mayorista NUNCA se congela (decision 2026-10-16): lo que espera
        //son agregar/quitar items.
        m.put("precioEditable", true);
        return m;
    }

    /** Admin: contractor/cambiar el plan de una licencia.
     *  noRollbackFor: rechazar una entrada invalida NO es un fallo que deba
     *  deshacer la transaccion (no se escribio nada). Sin esto Spring marca la
     *  tx rollback-only y al commit responde 500 en vez del 400 del mensaje. */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public Map<String, Object> guardarPlan(Long licenciaId, String plan, String vigenciaHasta) {
        Licencia l = licenciaRepo.findById(licenciaId)
                .orElseThrow(() -> new IllegalArgumentException("Licencia " + licenciaId + " no existe"));
        NivelComprape nuevo;
        try {
            nuevo = plan == null || plan.isBlank()
                    ? NivelComprape.GRATUITO
                    : NivelComprape.valueOf(plan.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Plan invalido: use GRATUITO o PLUS");
        }

        LocalDateTime vence = null;
        if (nuevo == NivelComprape.PLUS) {
            vence = parseFecha(vigenciaHasta);
            if (vence == null || !vence.isAfter(LocalDateTime.now())) {
                //PLUS es mensual y sin permanente: la vigencia es obligatoria y futura.
                throw new IllegalArgumentException(
                        "Comprape PLUS es mensual: indica una vigencia futura (AAAA-MM-DDTHH:MM)");
            }
        }

        PlanComprape p = repo.findByLicenciaId(licenciaId).orElse(null);
        if (p == null) {
            p = new PlanComprape(l, nuevo, vence);
        }
        p.setPlan(nuevo);
        p.setVigenciaHasta(vence);
        repo.save(p);
        return estadoDe(l);
    }

    /** Solo lectura del plan (para el panel admin). */
    @Transactional(readOnly = true)
    public Map<String, Object> verPlan(Long licenciaId) {
        return estadoDe(licenciaRepo.findById(licenciaId).orElse(null));
    }

    /**
     * Registrar una publicacion (Fase 3): suma el consumo y, en plan gratuito,
     * abre la ventana de 15 dias para cambiar la lista.
     */
    @Transactional
    public Map<String, Object> registrarPublicacion(Long licenciaId, int itemsPublicados) {
        Licencia l = licenciaRepo.findById(licenciaId)
                .orElseThrow(() -> new IllegalArgumentException("Licencia " + licenciaId + " no existe"));
        PlanComprape p = repo.findByLicenciaId(licenciaId).orElseGet(() -> new PlanComprape(l, NivelComprape.GRATUITO, null));
        p.setItemsPublicados(Math.max(0, itemsPublicados));
        repo.save(p);
        return estadoDe(l);
    }

    /**
     * Marcar cuando podra volver a cambiar su lista (Fase 3): en gratuito se
     * bloquea 15 dias desde el cambio; en PLUS queda libre al instante.
     */
    @Transactional
    public Map<String, Object> abrirVentana(Long licenciaId) {
        Licencia l = licenciaRepo.findById(licenciaId)
                .orElseThrow(() -> new IllegalArgumentException("Licencia " + licenciaId + " no existe"));
        PlanComprape p = repo.findByLicenciaId(licenciaId).orElseGet(() -> new PlanComprape(l, NivelComprape.GRATUITO, null));
        p.setProximaActualizacion(p.plusVigente()
                ? null
                : LocalDate.now().plusDays(PlanComprape.VENTANA_DIAS_GRATUITO));
        repo.save(p);
        return estadoDe(l);
    }

    private LocalDateTime parseFecha(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();
        try {
            if (v.length() <= 10) return LocalDate.parse(v).atStartOfDay();
            return LocalDateTime.parse(v.replace(' ', 'T'));
        } catch (Exception e) {
            return null;
        }
    }
}