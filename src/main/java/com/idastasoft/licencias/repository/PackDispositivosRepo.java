package com.idastasoft.licencias.repository;

import com.idastasoft.licencias.model.PackDispositivos;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackDispositivosRepo extends JpaRepository<PackDispositivos, String> {

    List<PackDispositivos> findByActivoTrueOrderByDispositivosAsc();
}