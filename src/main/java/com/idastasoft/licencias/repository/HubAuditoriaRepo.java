package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.HubAuditoria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HubAuditoriaRepo extends JpaRepository<HubAuditoria, Long> {
    List<HubAuditoria> findAllByOrderByFechaDesc(Pageable pageable);
}
