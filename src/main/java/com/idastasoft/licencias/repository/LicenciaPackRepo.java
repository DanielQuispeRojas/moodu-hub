package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.Licencia;
import com.idastasoft.licencias.model.LicenciaPack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LicenciaPackRepo extends JpaRepository<LicenciaPack, Long> {

    Optional<LicenciaPack> findByLicencia(Licencia licencia);
}