package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.TemaCatalogo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemaCatalogoRepo extends JpaRepository<TemaCatalogo, Long> {

    List<TemaCatalogo> findByActivoTrueOrderByNombreAsc();

    List<TemaCatalogo> findAllByOrderByNombreAsc();

    Optional<TemaCatalogo> findByClaveIgnoreCase(String clave);
}