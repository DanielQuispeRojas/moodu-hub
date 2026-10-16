package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.LicenciaModulo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LicenciaModuloRepo extends JpaRepository<LicenciaModulo, Long> {
    List<LicenciaModulo> findByLicenciaId(Long licenciaId);
    void deleteByLicenciaId(Long licenciaId);
}
