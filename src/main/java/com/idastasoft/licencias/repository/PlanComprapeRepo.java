package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.PlanComprape;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanComprapeRepo extends JpaRepository<PlanComprape, Long> {

    Optional<PlanComprape> findByLicenciaId(Long licenciaId);
}