package com.idastasoft.licencias.controller;

import com.idastasoft.licencias.model.ClienteUsuario;
import com.idastasoft.licencias.model.Licencia;
import com.idastasoft.licencias.model.Maquina;
import com.idastasoft.licencias.repository.ClienteUsuarioRepo;
import com.idastasoft.licencias.repository.LicenciaRepo;
import com.idastasoft.licencias.repository.MaquinaRepo;
import com.idastasoft.licencias.service.LicenciaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/cliente")
public class ClienteController {

    private final ClienteUsuarioRepo clienteRepo;
    private final LicenciaRepo licenciaRepo;
    private final MaquinaRepo maquinaRepo;
    private final LicenciaService licenciaService;
    private final PasswordEncoder passwordEncoder;
    private final com.idastasoft.licencias.repository.PaqueteRepo paqueteRepo;

    public ClienteController(ClienteUsuarioRepo clienteRepo, LicenciaRepo licenciaRepo,
                             MaquinaRepo maquinaRepo, LicenciaService licenciaService,
                             PasswordEncoder passwordEncoder,
                             com.idastasoft.licencias.repository.PaqueteRepo paqueteRepo) {
        this.clienteRepo = clienteRepo;
        this.licenciaRepo = licenciaRepo;
        this.maquinaRepo = maquinaRepo;
        this.licenciaService = licenciaService;
        this.passwordEncoder = passwordEncoder;
        this.paqueteRepo = paqueteRepo;
    }

    /** Visitante: lista de paquetes activos (precios y modulos). */
    @GetMapping("/paquetes")
    public ResponseEntity<?> paquetesPublicos() {
        return ResponseEntity.ok(paqueteRepo.findByActivoTrue());
    }

    /** Registro de cliente (visitante se hace usuario para gestionar sus dispositivos). */
    @PostMapping("/registro")
    public ResponseEntity<?> registro(@RequestBody Map<String, String> body) {
        String nombre = body.getOrDefault("nombre", "");
        String correo = body.getOrDefault("correo", "");
        String clave = body.getOrDefault("clave", "");
        if (correo.isBlank() || clave.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "Correo y clave requeridos"));
        }
        if (clienteRepo.existsByCorreo(correo)) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "El correo ya esta registrado"));
        }
        ClienteUsuario c = new ClienteUsuario();
        c.setNombre(nombre);
        c.setCorreo(correo);
        c.setClave(passwordEncoder.encode(clave));
        clienteRepo.save(c);
        return ResponseEntity.ok(Map.of("ok", true, "mensaje", "Registrado. Ahora puede iniciar sesion."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String correo = body.getOrDefault("correo", "");
        String clave = body.getOrDefault("clave", "");
        Optional<ClienteUsuario> c = clienteRepo.findByCorreo(correo);
        if (c.isEmpty() || !passwordEncoder.matches(clave, c.get().getClave())) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Credenciales invalidas"));
        }
        return ResponseEntity.ok(Map.of("ok", true, "correo", c.get().getCorreo(), "nombre", c.get().getNombre()));
    }

    /** Cliente logueado: sus licencias y sus maquinas (para desligar). */
    @GetMapping("/mis-dispositivos")
    public ResponseEntity<?> misDispositivos(@RequestParam String correo) {
        Licencia l = licenciaRepo.findByCorreo(correo).orElse(null);
        if (l == null) {
            return ResponseEntity.ok(Map.of("licencia", null, "maquinas", List.of()));
        }
        List<Maquina> maquinas = maquinaRepo.findByLicenciaCorreo(correo);
        return ResponseEntity.ok(Map.of(
            "licencia", Map.of(
                "correo", l.getCorreo(),
                "modulos", l.getModulos(),
                "maquinasPermitidas", l.getMaquinasPermitidas(),
                "vigente", licenciaService.estaVigente(l)
            ),
            "maquinas", maquinas
        ));
    }

    /** Cliente desliga una maquina de su licencia. */
    @PostMapping("/maquinas/{idHardware}/desactivar")
    public ResponseEntity<?> desactivarPropia(@RequestParam String correo, @PathVariable String idHardware) {
        Licencia l = licenciaRepo.findByCorreo(correo).orElse(null);
        if (l == null) return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "Sin licencia"));
        boolean ok = licenciaService.desactivarMaquina(l.getId(), idHardware);
        return ResponseEntity.ok(Map.of("ok", ok, "mensaje",
            ok ? "Maquina desactivada. El equipo cerrara sesion." : "No encontrada"));
    }
}
