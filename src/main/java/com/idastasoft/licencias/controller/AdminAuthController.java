package com.idastasoft.licencias.controller;

import com.idastasoft.licencias.service.AdminService;
import com.idastasoft.licencias.service.AdminTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminService adminService;
    private final AdminTokenService tokenService;

    public AdminAuthController(AdminService adminService, AdminTokenService tokenService) {
        this.adminService = adminService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String usuario = body.getOrDefault("usuario", "");
        String clave = body.getOrDefault("clave", "");
        if (!adminService.autenticar(usuario, clave)) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Credenciales invalidas"));
        }
        String token = tokenService.crear(usuario);
        return ResponseEntity.ok(Map.of("ok", true, "token", token,
            "nombre", adminService.nombreAdmin(usuario)));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("X-Admin-Token") String token) {
        tokenService.revocar(token);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
