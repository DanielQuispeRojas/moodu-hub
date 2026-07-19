package com.idastasoft.licencias.controller;

import com.idastasoft.licencias.model.ModuloCatalogo;
import com.idastasoft.licencias.model.VersionSistema;
import com.idastasoft.licencias.repository.ModuloCatalogoRepo;
import com.idastasoft.licencias.repository.VersionSistemaRepo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Catalogo publico del MOODU Hub. Lo consume la Tienda del desktop.
 * No requiere auth: expone modulos y la version disponible del sistema.
 */
@RestController
@RequestMapping("/api/catalogo")
public class CatalogoController {

    private final ModuloCatalogoRepo moduloRepo;
    private final VersionSistemaRepo versionRepo;

    public CatalogoController(ModuloCatalogoRepo moduloRepo, VersionSistemaRepo versionRepo) {
        this.moduloRepo = moduloRepo;
        this.versionRepo = versionRepo;
    }

    /** Lista de modulos activos del catalogo (oficiales y terceros). */
    @GetMapping("/modulos")
    public ResponseEntity<?> listarModulos() {
        List<ModuloCatalogo> modulos = moduloRepo.findAll().stream()
                .filter(ModuloCatalogo::isActivo)
                .toList();
        return ResponseEntity.ok(modulos);
    }

    /** Detalle de un modulo por clave. */
    @GetMapping("/modulos/{clave}")
    public ResponseEntity<?> detalleModulo(@PathVariable String clave) {
        ModuloCatalogo m = moduloRepo.findByClave(clave);
        if (m == null || !m.isActivo()) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "Modulo no encontrado"));
        }
        return ResponseEntity.ok(m);
    }

    /** Version disponible del instalador MOODU (la mas reciente activa). */
    @GetMapping("/version-sistema")
    public ResponseEntity<?> versionSistema() {
        List<VersionSistema> todas = versionRepo.findAll().stream()
                .filter(VersionSistema::isActivo)
                .sorted((a, b) -> b.getFecha().compareTo(a.getFecha()))
                .toList();
        if (todas.isEmpty()) {
            return ResponseEntity.ok(Map.of("version", "0.0.0", "urlDescarga", "", "notas", ""));
        }
        VersionSistema v = todas.get(0);
        return ResponseEntity.ok(Map.of(
                "version", v.getVersion(),
                "urlDescarga", v.getUrlDescarga() != null ? v.getUrlDescarga() : "",
                "notas", v.getNotas() != null ? v.getNotas() : "",
                "fecha", v.getFecha().toString()
        ));
    }
}
