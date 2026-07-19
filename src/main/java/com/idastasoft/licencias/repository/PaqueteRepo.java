package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.Paquete;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaqueteRepo extends JpaRepository<Paquete, Long> {
    List<Paquete> findByActivoTrue();
}
