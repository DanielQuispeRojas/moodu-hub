package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.VersionSistema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VersionSistemaRepo extends JpaRepository<VersionSistema, Long> {
}
