package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.HubContacto;
import com.idastasoft.licencias.repository.HubContactoRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Siembra los medios de contacto desde hub-config.json la PRIMERA vez.
 *
 * <p>Despues de eso manda la fila de {@code hub_contacto} (la que edita el
 * panel admin): el archivo es solo el valor de partida, porque el disco del
 * hosting es efimero y lo que se deje solo alli se pierde al redesplegar.</p>
 */
@Service
public class ContactoSemillaRunner {

    private final HubContactoRepo repo;
    private final HubConfigService config;

    public ContactoSemillaRunner(HubContactoRepo repo, HubConfigService config) {
        this.repo = repo;
        this.config = config;
    }

    @jakarta.annotation.PostConstruct
    @Transactional
    public void sembrarSiNoHay() {
        if (repo.findById(1).isPresent()) return;
        Map<String, Object> semilla = config.getContactoSemilla();
        if (semilla == null || semilla.isEmpty()) return;
        HubContacto c = new HubContacto();
        c.setId(1);
        // Se ignoran claves de ayuda (_notas): no son medios.
        java.util.Map<String, Object> limpio = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> e : semilla.entrySet()) {
            if (e.getKey().startsWith("_")) continue;
            limpio.put(e.getKey(), e.getValue());
        }
        c.desdeMapa(limpio);
        try {
            repo.saveAndFlush(c);
        } catch (RuntimeException e) {
            // Gano otra peticion: no es motivo para tumbar el arranque.
            System.err.println("[Hub] No se pudo sembrar el contacto: " + e.getMessage());
        }
    }
}