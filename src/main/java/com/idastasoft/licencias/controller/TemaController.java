package com.idastasoft.licencias.controller;

import com.idastasoft.licencias.model.TemaCatalogo;
import com.idastasoft.licencias.service.TemaCatalogoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Catalogo publico de THEMAS de MOODU. Lo consume Tienda > Temas del desktop.
 * No requiere auth (mismo criterio que el catalogo de modulos): solo expone
 * temas ACTIVOS y su archivo .json ya validado por el Hub.
 */
@RestController
@RequestMapping("/api/catalogo/temas")
public class TemaController {

    private final TemaCatalogoService service;

    public TemaController(TemaCatalogoService service) {
        this.service = service;
    }

    /** Grupos visuales disponibles (cerrados, los define el Hub). */
    @GetMapping("/grupos")
    public ResponseEntity<?> grupos() {
        return ResponseEntity.ok(TemaCatalogoService.GRUPOS);
    }

    /** Lista de temas activos (muestras de color, sin el archivo). */
    @GetMapping
    public ResponseEntity<?> listar() {
        return ResponseEntity.ok(service.listarPublicos());
    }

    /** Detalle de un tema con el archivo .json (el desktop lo revalida al aplicar). */
    @GetMapping("/{clave}")
    public ResponseEntity<?> detalle(@PathVariable String clave) {
        Map<String, Object> t = service.detalle(clave);
        if (t == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "Tema no encontrado"));
        }
        return ResponseEntity.ok(t);
    }

    /** Archivo .json del tema como descarga (para compartir o guardar). */
    @GetMapping("/{clave}/json")
    public ResponseEntity<?> descargar(@PathVariable String clave) {
        TemaCatalogo t = service.entidad(clave);
        if (t == null || !t.isActivo() || t.getContenido() == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "Tema no encontrado"));
        }
        byte[] bytes = t.getContenido().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"moodu-theme-" + t.getClave() + ".json\"")
                .header("Cache-Control", "public, max-age=3600")
                .body(bytes);
    }
}