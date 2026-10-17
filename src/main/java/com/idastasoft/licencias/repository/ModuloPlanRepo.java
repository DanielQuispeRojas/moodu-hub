package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.ModuloPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModuloPlanRepo extends JpaRepository<ModuloPlan, Long> {

    List<ModuloPlan> findByModuloIdOrderByOrdenAscIdAsc(Long moduloId);

    List<ModuloPlan> findByModuloIdAndActivoTrueOrderByOrdenAscIdAsc(Long moduloId);

    void deleteByModuloId(Long moduloId);
}