package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.PackDispositivos;
import com.idastasoft.licencias.repository.PackDispositivosRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Siembra el catalogo de packs de dispositivos la PRIMERA vez (5 / 10 / 15).
 *
 * <p><b>Precios en NULL a proposito</b>: un pack sin precio no se ofrece (la
 * Tienda lo oculta y {@code comprar} lo rechaza), asi que sembrar no inventa
 * una tarifa que nadie haya definido. Los precios los pone el administrador en
 * el Hub (tab Dispositivos).</p>
 *
 * <p>Despues de esto manda la tabla {@code pack_dispositivos} (la que edita el
 * admin), nunca esta semilla.</p>
 */
@Service
public class PackDispositivosSemillaRunner {

    private final PackDispositivosRepo repo;

    public PackDispositivosSemillaRunner(PackDispositivosRepo repo) {
        this.repo = repo;
    }

    @jakarta.annotation.PostConstruct
    @Transactional
    public void sembrarSiNoHay() {
        if (repo.count() > 0) return;
        List<PackDispositivos> packs = List.of(
            pack("D5", 5, "5 dispositivos extra"),
            pack("D10", 10, "10 dispositivos extra"),
            pack("D15", 15, "15 dispositivos extra")
        );
        try {
            repo.saveAll(packs);
            repo.flush();
        } catch (RuntimeException e) {
            System.err.println("[Hub] No se pudo sembrar el catalogo de packs de dispositivos: " + e.getMessage());
        }
    }

    private PackDispositivos pack(String codigo, int dispositivos, String nombre) {
        PackDispositivos p = new PackDispositivos();
        p.setCodigo(codigo);
        p.setNombre(nombre);
        p.setDispositivos(dispositivos);
        p.setPrecioMensual(null);
        p.setPrecioPermanente(null);
        p.setActivo(true);
        p.setDescripcion(dispositivos + " equipos o mas, por mensualidad o de forma permanente.");
        return p;
    }
}