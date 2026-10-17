package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.LicenciaModulo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LicenciaModuloRepo extends JpaRepository<LicenciaModulo, Long> {
    List<LicenciaModulo> findByLicenciaId(Long licenciaId);
    Optional<LicenciaModulo> findByLicenciaIdAndModuloClave(Long licenciaId, String moduloClave);
    void deleteByLicenciaId(Long licenciaId);
}