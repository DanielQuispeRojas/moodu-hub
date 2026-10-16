package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.ModuloFoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModuloFotoRepo extends JpaRepository<ModuloFoto, Long> {
    List<ModuloFoto> findByModuloIdOrderByOrdenAscIdAsc(Long moduloId);
    long countByModuloId(Long moduloId);
}
