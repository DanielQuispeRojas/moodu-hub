package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.HubAuditoria;
import com.idastasoft.licencias.repository.HubAuditoriaRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MEJ-02: auditoria de las decisiones del Hub. Las entidades del Hub ya no
 * tienen auditoria propia (el core la tiene, pero el Hub es otro proceso):
 * este servicio es la fuente unica para licencias, planes, precios y claves.
 *
 * <p>Nunca guarda secretos: el detalle se acota y los callers solo pasan
 * identificadores y nombres, nunca claves de activacion ni passwords.</p>
 */
@Service
public class HubAuditoriaService {

    private final HubAuditoriaRepo repo;

    public HubAuditoriaService(HubAuditoriaRepo repo) { this.repo = repo; }

    public void registrar(String usuario, String accion, String entidad, String id, String detalle) {
        HubAuditoria a = new HubAuditoria();
        a.setUsuario(cortar(usuario == null || usuario.isBlank() ? "sistema" : usuario, 255));
        a.setAccion(cortar(accion, 100));
        a.setEntidad(cortar(entidad, 100));
        a.setEntidadId(cortar(id, 100));
        a.setDetalle(detalle == null ? null : cortar(detalle, 2000));
        repo.save(a);
    }

    public List<HubAuditoria> recientes(int limite) {
        int n = Math.max(1, Math.min(200, limite));
        return repo.findAllByOrderByFechaDesc(PageRequest.of(0, n));
    }

    private String cortar(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}
