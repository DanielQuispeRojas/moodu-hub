package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.Maquina;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MaquinaRepo extends JpaRepository<Maquina, Long> {
    List<Maquina> findByLicenciaId(Long licenciaId);
    List<Maquina> findByLicenciaCorreo(String correo);
    Optional<Maquina> findByLicenciaIdAndIdHardware(Long licenciaId, String idHardware);
    long countByLicenciaIdAndActivaTrue(Long licenciaId);
    long countByLicenciaCorreoAndActivaTrue(String correo);
}
