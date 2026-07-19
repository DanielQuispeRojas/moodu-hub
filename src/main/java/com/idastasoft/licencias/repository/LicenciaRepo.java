package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.Licencia;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LicenciaRepo extends JpaRepository<Licencia, Long> {
    Optional<Licencia> findByCorreoAndClaveActivacion(String correo, String clave);
    Optional<Licencia> findByCorreo(String correo);
}
