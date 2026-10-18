package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.HubParametro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HubParametroRepo extends JpaRepository<HubParametro, Long> {

    Optional<HubParametro> findByClaveIgnoreCase(String clave);

    List<HubParametro> findAllByOrderByClaveAsc();
}