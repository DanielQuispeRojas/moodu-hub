package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.ClienteUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ClienteUsuarioRepo extends JpaRepository<ClienteUsuario, Long> {
    Optional<ClienteUsuario> findByCorreo(String correo);
    boolean existsByCorreo(String correo);
}
