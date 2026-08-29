package com.idastasoft.licencias.controller;

import com.idastasoft.licencias.service.ValoracionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Valoraciones publicas: sistema ("moodu") y por modulo (destino = clave).
 * Un correo solo tiene una valoracion por destino (upsert). GET listas por
 * ultimo cambio; POST guarda/edita. Sin auth: la regla de negocio es que el
 * correo aporte una sola valoracion, no que exista una cuenta.
 */
@RestController
@RequestMapping("/api/valoraciones")
public class ValoracionController {

    private final ValoracionService service;

    public ValoracionController(ValoracionService service) {
        this.service = service;
    }

    /** Lista por destino: ?destino=moodu o ?destino=<clave modulo>. */
    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(value = "destino", required = false) String destino) {
        return ResponseEntity.ok(service.listar(destino));
    }

    /** Mi valoracion para pre-rellenar la edicion: ?destino=&correo=. */
    @GetMapping("/mia")
    public ResponseEntity<?> mia(@RequestParam(value = "destino", required = false) String destino,
                                 @RequestParam(value = "correo", required = false) String correo) {
        return ResponseEntity.ok(service.mia(destino, correo));
    }

    /** Guardar o editar. Body: { destino, correo, puntaje, comentario }. */
    @PostMapping
    public ResponseEntity<?> guardar(@RequestBody Map<String, Object> body) {
        try {
            String destino = (String) body.getOrDefault("destino", "moodu");
            String correo = (String) body.getOrDefault("correo", "");
            int puntaje = Integer.parseInt(String.valueOf(body.getOrDefault("puntaje", 0)));
            String comentario = (String) body.get("comentario");
            return ResponseEntity.ok(service.guardar(destino, correo, puntaje, comentario));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "Datos invalidos: " + e.getMessage()));
        }
    }
}