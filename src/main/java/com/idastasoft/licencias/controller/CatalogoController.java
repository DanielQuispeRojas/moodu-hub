package com.idastasoft.licencias.controller;

import com.idastasoft.licencias.model.ModuloCatalogo;
import com.idastasoft.licencias.model.ModuloFoto;
import com.idastasoft.licencias.model.VersionSistema;
import com.idastasoft.licencias.repository.ModuloCatalogoRepo;
import com.idastasoft.licencias.repository.ModuloFotoRepo;
import com.idastasoft.licencias.repository.VersionSistemaRepo;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
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
    private final ModuloFotoRepo fotoRepo;
    private final VersionSistemaRepo versionRepo;

    public CatalogoController(ModuloCatalogoRepo moduloRepo, ModuloFotoRepo fotoRepo, VersionSistemaRepo versionRepo) {
        this.moduloRepo = moduloRepo;
        this.fotoRepo = fotoRepo;
        this.versionRepo = versionRepo;
    }

    private ModuloCatalogo conFotos(ModuloCatalogo m) {
        m.setFotoIds(fotoRepo.findByModuloIdOrderByOrdenAscIdAsc(m.getId())
                .stream().map(ModuloFoto::getId).toList());
        return m;
    }

    /** Lista de modulos activos del catalogo (oficiales y terceros). */
    @GetMapping("/modulos")
    public ResponseEntity<?> listarModulos() {
        List<ModuloCatalogo> modulos = moduloRepo.findAll().stream()
                .filter(ModuloCatalogo::isActivo)
                .map(this::conFotos)
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
        return ResponseEntity.ok(conFotos(m));
    }

    /** Fotos de un modulo (solo metadata; los bytes van por /fotos/{id}). */
    @GetMapping("/modulos/{clave}/fotos")
    public ResponseEntity<?> fotosModulo(@PathVariable String clave) {
        ModuloCatalogo m = moduloRepo.findByClave(clave);
        if (m == null || !m.isActivo()) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "Modulo no encontrado"));
        }
        var out = fotoRepo.findByModuloIdOrderByOrdenAscIdAsc(m.getId()).stream()
                .map(f -> Map.of("id", f.getId(), "nombre", f.getNombre()));
        return ResponseEntity.ok(out.toList());
    }

    /** Bytes de una foto del catalogo (publica, cacheable). */
    @GetMapping("/modulos/{clave}/fotos/{fotoId}")
    public ResponseEntity<?> fotoBytes(@PathVariable String clave, @PathVariable Long fotoId) {
        ModuloCatalogo m = moduloRepo.findByClave(clave);
        if (m == null || !m.isActivo()) {
            return ResponseEntity.status(404).build();
        }
        ModuloFoto f = fotoRepo.findById(fotoId).orElse(null);
        if (f == null || !f.getModulo().getId().equals(m.getId())) {
            return ResponseEntity.status(404).build();
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(f.getDatos());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).build();
        }
        MediaType tipo = switch (f.getMime()) {
            case "image/jpeg" -> MediaType.IMAGE_JPEG;
            case "image/webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.IMAGE_PNG;
        };
        return ResponseEntity.ok()
                .contentType(tipo)
                .header("Cache-Control", "public, max-age=86400")
                .body(bytes);
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
