package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.HubContacto;
import com.idastasoft.licencias.repository.HubContactoRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Medios de contacto del soporte de MOODU (REQ-HUB-PLANES-01).
 *
 * <p>Viven en BD (tabla {@code hub_contacto}, fila unica): el disco del
 * hosting es efimero y lo escrito en un archivo se pierde en cada
 * redeploy. hub-config.json queda como semilla de la primera vez.</p>
 */
@Service
public class ContactoService {

    private final HubContactoRepo repo;

    public ContactoService(HubContactoRepo repo) {
        this.repo = repo;
    }

    /** Fila vigente; si no existe, se crea vacia (id 1). */
    public HubContacto obtener() {
        return repo.findById(1).orElseGet(() -> {
            HubContacto c = new HubContacto();
            c.setId(1);
            // Sin catch de DataIntegrityViolation: si dos peticiones compiten
            // en la carrera, el segundo save explota con duplicate y el primero
            // gano la fila. Es una fila unica creada al arrancar, no por request.
            try {
                return repo.saveAndFlush(c);
            } catch (RuntimeException e) {
                return repo.findById(1).orElse(c);
            }
        });
    }

    /** Solo los medios con valor (los vacios no se exponen). */
    public Map<String, Object> mapa() {
        return obtener().comoMapa();
    }

    @Transactional
    public HubContacto guardar(Map<String, Object> datos) {
        HubContacto c = obtener();
        c.desdeMapa(datos);
        return repo.save(c);
    }
}