package com.idastasoft.licencias.controller;

import com.idastasoft.licencias.model.ClienteUsuario;
import com.idastasoft.licencias.model.Licencia;
import com.idastasoft.licencias.model.Maquina;
import com.idastasoft.licencias.repository.ClienteUsuarioRepo;
import com.idastasoft.licencias.repository.LicenciaRepo;
import com.idastasoft.licencias.repository.MaquinaRepo;
import com.idastasoft.licencias.service.ClienteSesionService;
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
    private final ClienteSesionService sesiones;

    public ClienteController(ClienteUsuarioRepo clienteRepo, LicenciaRepo licenciaRepo,
                             MaquinaRepo maquinaRepo, LicenciaService licenciaService,
                             PasswordEncoder passwordEncoder,
                             com.idastasoft.licencias.repository.PaqueteRepo paqueteRepo,
                             ClienteSesionService sesiones) {
        this.clienteRepo = clienteRepo;
        this.licenciaRepo = licenciaRepo;
        this.maquinaRepo = maquinaRepo;
        this.licenciaService = licenciaService;
        this.passwordEncoder = passwordEncoder;
        this.paqueteRepo = paqueteRepo;
        this.sesiones = sesiones;
    }

    /** H-02: todo dato del cliente necesita sesion, no solo el correo de la URL. */
    private ResponseEntity<?> noAutorizado() {
        return ResponseEntity.status(401).body(Map.of("ok", false,
                "mensaje", "Inicie sesion para gestionar sus dispositivos"));
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
        String token = sesiones.emitir(c.get().getCorreo());
        return ResponseEntity.ok(Map.of("ok", true, "correo", c.get().getCorreo(),
                "nombre", c.get().getNombre(), "token", token));
    }

    /** Cierra la sesion del panel cliente y revoca el token. */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "X-Cliente-Token", required = false) String token) {
        sesiones.revocar(token);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Cliente logueado: sus licencias y sus maquinas (para desligar). */
    @GetMapping("/mis-dispositivos")
    public ResponseEntity<?> misDispositivos(@RequestParam String correo,
                                              @RequestHeader(value = "X-Cliente-Token", required = false) String token) {
        if (!sesiones.autoriza(token, correo)) return noAutorizado();
        Licencia l = licenciaRepo.findByCorreo(correo).orElse(null);
        if (l == null) {
            // H-22 (preexistente): Map.of NO admite valores null -> 500 en un
            // cliente recien registrado sin licencia. LinkedHashMap lo permite.
            Map<String, Object> vacio = new java.util.LinkedHashMap<>();
            vacio.put("licencia", null);
            vacio.put("maquinas", List.of());
            return ResponseEntity.ok(vacio);
        }
        List<Maquina> maquinas = maquinaRepo.findByLicenciaCorreo(correo);
        Map<String, Object> lic = new java.util.LinkedHashMap<>();
        lic.put("correo", l.getCorreo());
        lic.put("modulos", l.getModulos());
        lic.put("maquinasPermitidas", l.getMaquinasPermitidas()); // alias legacy
        lic.put("dispositivosPermitidos", licenciaService.dispositivosPermitidosDe(l));
        lic.put("dispositivosActivos", maquinaRepo.countByLicenciaIdAndActivaTrue(l.getId()));
        lic.put("vigente", licenciaService.estaVigente(l));
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("licencia", lic);
        out.put("maquinas", maquinas);
        return ResponseEntity.ok(out);
    }

    /** Cliente desliga una maquina de su licencia. */
    @PostMapping("/maquinas/{idHardware}/desactivar")
    public ResponseEntity<?> desactivarPropia(@RequestParam String correo, @PathVariable String idHardware,
                                               @RequestHeader(value = "X-Cliente-Token", required = false) String token) {
        if (!sesiones.autoriza(token, correo)) return noAutorizado();
        Licencia l = licenciaRepo.findByCorreo(correo).orElse(null);
        if (l == null) return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "Sin licencia"));
        boolean ok = licenciaService.desactivarMaquina(l.getId(), idHardware);
        return ResponseEntity.ok(Map.of("ok", ok, "mensaje",
            ok ? "Maquina desactivada. El equipo cerrara sesion." : "No encontrada"));
    }

    /** Borra el registro de una maquina YA desvinculada (sin rastro en el Hub). */
    @DeleteMapping("/maquinas/{idHardware}")
    public ResponseEntity<?> eliminarPropia(@RequestParam String correo, @PathVariable String idHardware,
                                             @RequestHeader(value = "X-Cliente-Token", required = false) String token) {
        if (!sesiones.autoriza(token, correo)) return noAutorizado();
        boolean ok = licenciaService.eliminarMaquinaPorCorreo(correo, idHardware);
        if (!ok) {
            return ResponseEntity.badRequest().body(Map.of("ok", false,
                "mensaje", "No se pudo eliminar: primero desvincula la maquina o no existe."));
        }
        return ResponseEntity.ok(Map.of("ok", true, "mensaje", "Registro eliminado."));
    }
}
