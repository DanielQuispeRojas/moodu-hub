package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.Desarrollador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DesarrolladorRepo extends JpaRepository<Desarrollador, Long> {
    Desarrollador findByCorreo(String correo);
    boolean existsByCorreo(String correo);
}
