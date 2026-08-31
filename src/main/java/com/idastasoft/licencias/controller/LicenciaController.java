package com.idastasoft.licencias.controller;

import com.idastasoft.licencias.service.LicenciaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/licencias")
public class LicenciaController {

    private final LicenciaService licenciaService;

    public LicenciaController(LicenciaService licenciaService) {
        this.licenciaService = licenciaService;
    }

    /**
     * Valida una licencia desde el desktop MOODU. Es publico (el desktop solo
     * tiene correo + clave de activacion + id de hardware).
     */
    @PostMapping("/validar")
    public ResponseEntity<?> validar(@RequestBody Map<String, Object> body) {
        String correo = (String) body.getOrDefault("correo", "");
        String clave = (String) body.getOrDefault("claveActivacion", "");
        String idHardware = (String) body.getOrDefault("idHardware", "unknown");
        String nombreEquipo = (String) body.getOrDefault("nombreEquipo", "");
        boolean forzar = Boolean.TRUE.equals(body.get("forzarActivacion"));
        Map<String, Object> res = licenciaService.validar(correo, clave, idHardware, nombreEquipo, forzar);
        return ResponseEntity.ok(res);
    }

    /** El desktop consulta los modulos vigentes de una licencia ya validada.
     *  SOLO LECTURA: NO registra ni activa maquinas (antes usaba validar() con
     *  idHardware "ping", que llenaba el cupo de maquinas con entradas basura). */
    @GetMapping("/estado")
    public ResponseEntity<?> estado(@RequestParam String correo, @RequestParam String claveActivacion) {
        Map<String, Object> res = licenciaService.consultarEstado(correo, claveActivacion);
        return ResponseEntity.ok(res);
    }
}
