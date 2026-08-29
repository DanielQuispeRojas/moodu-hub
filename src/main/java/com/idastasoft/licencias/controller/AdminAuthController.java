package com.idastasoft.licencias.controller;

import com.idastasoft.licencias.service.AdminService;
import com.idastasoft.licencias.service.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminService adminService;
    private final JwtService jwtService;

    public AdminAuthController(AdminService adminService, JwtService jwtService) {
        this.adminService = adminService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String usuario = body.getOrDefault("usuario", "");
        String clave = body.getOrDefault("clave", "");
        if (!adminService.autenticar(usuario, clave)) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Credenciales invalidas"));
        }
        String token = jwtService.generarToken(usuario);
        return ResponseEntity.ok(Map.of("ok", true, "token", token,
            "nombre", adminService.nombreAdmin(usuario)));
    }

    // Logout es stateless con JWT: el frontend simplemente borra el token.
    // Se mantiene el endpoint por compatibilidad pero no hace nada server-side.
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
