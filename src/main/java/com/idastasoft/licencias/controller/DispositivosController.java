package com.idastasoft.licencias.controller;

import com.idastasoft.licencias.service.LicenciaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Packs de dispositivos adicionales (REQ-HUB-PACK-DISP-01).
 *
 * <p>Publico porque MOODU solo tiene correo + clave de activacion (el desktop
 * nunca carga el JWT del admin). El Hub sigue siendo el que valida: una licencia
 * no vigente no compra, un pack sin precio no se ofrece y con MOODU Pro no hace
 * falta (ya son ilimitados).</p>
 *
 * <p>El pago NO ocurre aqui: el pack queda registrado y el admin lo confirma
 * (hoy el pago es presencial, §11.90; la pasarela es roadmap).</p>
 */
@RestController
@RequestMapping("/api/dispositivos")
public class DispositivosController {

    private final LicenciaService licenciaService;

    public DispositivosController(LicenciaService licenciaService) {
        this.licenciaService = licenciaService;
    }

    /** Catalogo de packs con precios (lo que muestra la Tienda > Dispositivos). */
    @GetMapping("/catalogo")
    public ResponseEntity<?> catalogo() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("packs", licenciaService.catalogoPacks());
        return ResponseEntity.ok(res);
    }

    /**
     * Estado del cupo de la licencia: base + extra, plan y vencimiento del pack.
     * SOLO LECTURA (no consume cupo ni registra maquina).
     */
    @GetMapping("/estado")
    public ResponseEntity<?> estado(@RequestParam String correo, @RequestParam String clave) {
        return ResponseEntity.ok(licenciaService.estadoDispositivos(correo, clave));
    }

    /** Compra (o reemplaza) el pack activo de la licencia. */
    @PostMapping("/comprar")
    public ResponseEntity<?> comprar(@RequestBody Map<String, Object> body) {
        String correo = (String) body.getOrDefault("correo", "");
        String clave = (String) body.getOrDefault("clave", "");
        String codigo = (String) body.getOrDefault("codigo", "");
        String plan = (String) body.getOrDefault("plan", "");
        return ResponseEntity.ok(licenciaService.comprarPackDispositivos(correo, clave, codigo, plan));
    }
}