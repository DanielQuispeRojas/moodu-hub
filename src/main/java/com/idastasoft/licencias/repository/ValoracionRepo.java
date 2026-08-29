package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.Valoracion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ValoracionRepo extends JpaRepository<Valoracion, Long> {

    Optional<Valoracion> findByDestinoAndCorreo(String destino, String correo);

    List<Valoracion> findByDestinoAndVisibleTrueOrderByFechaModificacionDesc(String destino);

    List<Valoracion> findByDestinoOrderByFechaModificacionDesc(String destino);

    List<Valoracion> findAllByOrderByFechaModificacionDesc();
}