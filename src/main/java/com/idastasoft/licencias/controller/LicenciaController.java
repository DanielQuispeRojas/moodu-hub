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

    /**
     * Plan del marketplace Comprape (docs/comprape.md R8/R9): modo, cupo de items y
     * cuando puede volver a cambiar su lista. SOLO LECTURA (no registra maquina),
     * para que MOODU lo consulte seguido sin gastar cupo de maquinas.
     */
    @GetMapping("/comprape/estado")
    public ResponseEntity<?> estadoComprape(@RequestParam String correo, @RequestParam String clave) {
        return ResponseEntity.ok(licenciaService.estadoComprape(correo, clave));
    }

    /**
     * Activa un modulo GRATUITO pedido desde la Tienda (REQ-HUB-ACTIVA-GRATIS-01).
     *
     * <p>Publico porque MOODU solo tiene correo + clave de activacion. El Hub
     * <b>valida que el modulo sea realmente gratis</b>: si es de paga responde
     * {@code ok:false} con el motivo y NO toca la licencia. Un modulo gratuito
     * se otorga PERMANENTE.</p>
     *
     * <p>Responde 200 tambien en error (el escritorio lee {@code ok:false}),
     * igual que {@code /validar}.</p>
     */
    @PostMapping("/activar-gratuito")
    public ResponseEntity<?> activarGratuito(@RequestBody Map<String, Object> body) {
        String correo = (String) body.getOrDefault("correo", "");
        String clave = (String) body.getOrDefault("claveActivacion", "");
        String modulo = (String) body.getOrDefault("modulo", "");
        return ResponseEntity.ok(licenciaService.activarModuloGratuito(correo, clave, modulo));
    }
}
