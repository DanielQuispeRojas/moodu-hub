package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.ModuloCatalogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModuloCatalogoRepo extends JpaRepository<ModuloCatalogo, Long> {
    ModuloCatalogo findByClave(String clave);
}
