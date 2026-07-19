package com.idastasoft.licencias.controller;

import com.idastasoft.licencias.model.Desarrollador;
import com.idastasoft.licencias.model.Licencia;
import com.idastasoft.licencias.model.ModuloCatalogo;
import com.idastasoft.licencias.model.Paquete;
import com.idastasoft.licencias.model.VersionSistema;
import com.idastasoft.licencias.repository.DesarrolladorRepo;
import com.idastasoft.licencias.repository.LicenciaRepo;
import com.idastasoft.licencias.repository.MaquinaRepo;
import com.idastasoft.licencias.repository.ModuloCatalogoRepo;
import com.idastasoft.licencias.repository.PaqueteRepo;
import com.idastasoft.licencias.repository.VersionSistemaRepo;
import com.idastasoft.licencias.service.AdminTokenService;
import com.idastasoft.licencias.service.HubConfigService;
import com.idastasoft.licencias.service.LicenciaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/gestion")
public class AdminGestionController {

    private final AdminTokenService tokenService;
    private final LicenciaRepo licenciaRepo;
    private final PaqueteRepo paqueteRepo;
    private final MaquinaRepo maquinaRepo;
    private final LicenciaService licenciaService;
    private final ModuloCatalogoRepo moduloRepo;
    private final VersionSistemaRepo versionRepo;
    private final DesarrolladorRepo desarrolladorRepo;
    private final HubConfigService hubConfigService;

    public AdminGestionController(AdminTokenService tokenService, LicenciaRepo licenciaRepo,
                                  PaqueteRepo paqueteRepo, MaquinaRepo maquinaRepo,
                                  LicenciaService licenciaService,
                                  ModuloCatalogoRepo moduloRepo,
                                  VersionSistemaRepo versionRepo,
                                  DesarrolladorRepo desarrolladorRepo,
                                  HubConfigService hubConfigService) {
        this.tokenService = tokenService;
        this.licenciaRepo = licenciaRepo;
        this.paqueteRepo = paqueteRepo;
        this.maquinaRepo = maquinaRepo;
        this.licenciaService = licenciaService;
        this.moduloRepo = moduloRepo;
        this.versionRepo = versionRepo;
        this.desarrolladorRepo = desarrolladorRepo;
        this.hubConfigService = hubConfigService;
    }

    private void requiereToken(String token) {
        if (!tokenService.valido(token)) {
            throw new RuntimeException("No autorizado");
        }
    }

    // ─── LICENCIAS ──────────────────────────────
    @GetMapping("/licencias")
    public ResponseEntity<?> listarLicencias(@RequestHeader("X-Admin-Token") String token) {
        requiereToken(token);
        return ResponseEntity.ok(licenciaRepo.findAll());
    }

    @PostMapping("/licencias")
    public ResponseEntity<?> crearLicencia(@RequestHeader("X-Admin-Token") String token,
                                           @RequestBody Map<String, Object> body) {
        requiereToken(token);
        Licencia l = new Licencia();
        l.setNombre((String) body.getOrDefault("nombre", ""));
        l.setApellido((String) body.getOrDefault("apellido", ""));
        l.setCorreo((String) body.getOrDefault("correo", ""));
        String clave = (String) body.get("claveActivacion");
        l.setClaveActivacion(clave != null && !clave.isBlank() ? clave
            : UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        l.setModulos((List<String>) body.getOrDefault("modulos", List.of()));
        l.setMaquinasPermitidas(Integer.parseInt(String.valueOf(body.getOrDefault("maquinasPermitidas", 3))));
        l.setVigenciaUnidad(Licencia.UnidadVigencia.valueOf(
            ((String) body.getOrDefault("vigenciaUnidad", "MESES")).toUpperCase()));
        l.setVigenciaValor(Integer.parseInt(String.valueOf(body.getOrDefault("vigenciaValor", 12))));
        l.setFechaInicio(LocalDateTime.now());
        l.setActiva(true);
        Licencia guardada = licenciaRepo.save(l);
        return ResponseEntity.ok(guardada);
    }

    @PutMapping("/licencias/{id}")
    public ResponseEntity<?> actualizarLicencia(@RequestHeader("X-Admin-Token") String token,
                                                @PathVariable Long id, @RequestBody Map<String, Object> body) {
        requiereToken(token);
        Licencia l = licenciaRepo.findById(id).orElseThrow(() -> new RuntimeException("No encontrada"));
        if (body.containsKey("nombre")) l.setNombre((String) body.get("nombre"));
        if (body.containsKey("apellido")) l.setApellido((String) body.get("apellido"));
        if (body.containsKey("correo")) l.setCorreo((String) body.get("correo"));
        if (body.containsKey("modulos")) l.setModulos((List<String>) body.get("modulos"));
        if (body.containsKey("maquinasPermitidas")) l.setMaquinasPermitidas(Integer.parseInt(String.valueOf(body.get("maquinasPermitidas"))));
        if (body.containsKey("vigenciaUnidad")) l.setVigenciaUnidad(Licencia.UnidadVigencia.valueOf(((String) body.get("vigenciaUnidad")).toUpperCase()));
        if (body.containsKey("vigenciaValor")) l.setVigenciaValor(Integer.parseInt(String.valueOf(body.get("vigenciaValor"))));
        if (body.containsKey("activa")) l.setActiva(Boolean.parseBoolean(String.valueOf(body.get("activa"))));
        return ResponseEntity.ok(licenciaRepo.save(l));
    }

    @DeleteMapping("/licencias/{id}")
    public ResponseEntity<?> eliminarLicencia(@RequestHeader("X-Admin-Token") String token, @PathVariable Long id) {
        requiereToken(token);
        licenciaRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ─── MAQUINAS (dispositivos de una licencia) ─────────
    @GetMapping("/licencias/{id}/maquinas")
    public ResponseEntity<?> maquinas(@RequestHeader("X-Admin-Token") String token, @PathVariable Long id) {
        requiereToken(token);
        return ResponseEntity.ok(licenciaService.maquinasDeLicencia(id));
    }

    @PostMapping("/licencias/{id}/maquinas/{idHardware}/desactivar")
    public ResponseEntity<?> desactivar(@RequestHeader("X-Admin-Token") String token,
                                        @PathVariable Long id, @PathVariable String idHardware) {
        requiereToken(token);
        boolean ok = licenciaService.desactivarMaquina(id, idHardware);
        return ResponseEntity.ok(Map.of("ok", ok, "mensaje",
            ok ? "Maquina desactivada. El equipo cerrara sesion en MOODU." : "Maquina no encontrada"));
    }

    // ─── PAQUETES ──────────────────────────────
    @GetMapping("/paquetes")
    public ResponseEntity<?> listarPaquetes(@RequestHeader("X-Admin-Token") String token) {
        requiereToken(token);
        return ResponseEntity.ok(paqueteRepo.findAll());
    }

    @PostMapping("/paquetes")
    public ResponseEntity<?> crearPaquete(@RequestHeader("X-Admin-Token") String token,
                                          @RequestBody Map<String, Object> body) {
        requiereToken(token);
        Paquete p = new Paquete();
        p.setNombre((String) body.getOrDefault("nombre", ""));
        p.setDescripcion((String) body.getOrDefault("descripcion", ""));
        p.setModulos((List<String>) body.getOrDefault("modulos", List.of()));
        p.setPrecio(java.math.BigDecimal.valueOf(Double.parseDouble(String.valueOf(body.getOrDefault("precio", 0)))));
        p.setActivo(true);
        return ResponseEntity.ok(paqueteRepo.save(p));
    }

    @PutMapping("/paquetes/{id}")
    public ResponseEntity<?> actualizarPaquete(@RequestHeader("X-Admin-Token") String token,
                                               @PathVariable Long id, @RequestBody Map<String, Object> body) {
        requiereToken(token);
        Paquete p = paqueteRepo.findById(id).orElseThrow(() -> new RuntimeException("No encontrado"));
        if (body.containsKey("nombre")) p.setNombre((String) body.get("nombre"));
        if (body.containsKey("descripcion")) p.setDescripcion((String) body.get("descripcion"));
        if (body.containsKey("modulos")) p.setModulos((List<String>) body.get("modulos"));
        if (body.containsKey("precio")) p.setPrecio(java.math.BigDecimal.valueOf(Double.parseDouble(String.valueOf(body.get("precio")))));
        if (body.containsKey("activo")) p.setActivo(Boolean.parseBoolean(String.valueOf(body.get("activo"))));
        return ResponseEntity.ok(paqueteRepo.save(p));
    }

    @DeleteMapping("/paquetes/{id}")
    public ResponseEntity<?> eliminarPaquete(@RequestHeader("X-Admin-Token") String token, @PathVariable Long id) {
        requiereToken(token);
        paqueteRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ─── CATALOGO DE MODULOS (oficiales y terceros) ─────────
    @GetMapping("/catalogo/modulos")
    public ResponseEntity<?> listarCatalogo(@RequestHeader("X-Admin-Token") String token) {
        requiereToken(token);
        return ResponseEntity.ok(moduloRepo.findAll());
    }

    @PostMapping("/catalogo/modulos")
    public ResponseEntity<?> crearModuloCatalogo(@RequestHeader("X-Admin-Token") String token,
                                                 @RequestBody Map<String, Object> body) {
        requiereToken(token);
        ModuloCatalogo m = new ModuloCatalogo();
        aplicarCamposModulo(m, body);
        return ResponseEntity.ok(moduloRepo.save(m));
    }

    @PutMapping("/catalogo/modulos/{id}")
    public ResponseEntity<?> actualizarModuloCatalogo(@RequestHeader("X-Admin-Token") String token,
                                                     @PathVariable Long id, @RequestBody Map<String, Object> body) {
        requiereToken(token);
        ModuloCatalogo m = moduloRepo.findById(id).orElseThrow(() -> new RuntimeException("No encontrado"));
        aplicarCamposModulo(m, body);
        return ResponseEntity.ok(moduloRepo.save(m));
    }

    @DeleteMapping("/catalogo/modulos/{id}")
    public ResponseEntity<?> eliminarModuloCatalogo(@RequestHeader("X-Admin-Token") String token, @PathVariable Long id) {
        requiereToken(token);
        moduloRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private void aplicarCamposModulo(ModuloCatalogo m, Map<String, Object> body) {
        if (body.containsKey("clave")) m.setClave((String) body.get("clave"));
        if (body.containsKey("nombre")) m.setNombre((String) body.get("nombre"));
        if (body.containsKey("descripcion")) m.setDescripcion((String) body.get("descripcion"));
        if (body.containsKey("imagen")) m.setImagen((String) body.get("imagen"));
        if (body.containsKey("desarrollador")) m.setDesarrollador((String) body.get("desarrollador"));
        if (body.containsKey("version")) m.setVersion((String) body.getOrDefault("version", "1.0.0"));
        if (body.containsKey("origen")) m.setOrigen(ModuloCatalogo.OrigenModulo.valueOf(((String) body.get("origen")).toUpperCase()));
        if (body.containsKey("tipo")) m.setTipo(ModuloCatalogo.TipoLicencia.valueOf(((String) body.get("tipo")).toUpperCase()));
        if (body.containsKey("precio")) m.setPrecio(BigDecimal.valueOf(Double.parseDouble(String.valueOf(body.getOrDefault("precio", 0)))));
        if (body.containsKey("linkDescarga")) m.setLinkDescarga((String) body.get("linkDescarga"));
        if (body.containsKey("linkRepositorio")) m.setLinkRepositorio((String) body.get("linkRepositorio"));
        if (body.containsKey("vigenciaDias")) m.setVigenciaDias(Integer.parseInt(String.valueOf(body.getOrDefault("vigenciaDias", 0))));
        if (body.containsKey("activo")) m.setActivo(Boolean.parseBoolean(String.valueOf(body.get("activo"))));
    }

    // ─── VERSIONES DEL SISTEMA (instalador MOODU) ─────────
    @GetMapping("/versiones")
    public ResponseEntity<?> listarVersiones(@RequestHeader("X-Admin-Token") String token) {
        requiereToken(token);
        return ResponseEntity.ok(versionRepo.findAll());
    }

    @PostMapping("/versiones")
    public ResponseEntity<?> crearVersion(@RequestHeader("X-Admin-Token") String token,
                                         @RequestBody Map<String, Object> body) {
        requiereToken(token);
        VersionSistema v = new VersionSistema();
        v.setVersion((String) body.getOrDefault("version", "0.0.0"));
        v.setUrlDescarga((String) body.getOrDefault("urlDescarga", ""));
        v.setNotas((String) body.getOrDefault("notas", ""));
        v.setFecha(LocalDateTime.now());
        v.setActivo(true);
        return ResponseEntity.ok(versionRepo.save(v));
    }

    @PutMapping("/versiones/{id}")
    public ResponseEntity<?> actualizarVersion(@RequestHeader("X-Admin-Token") String token,
                                             @PathVariable Long id, @RequestBody Map<String, Object> body) {
        requiereToken(token);
        VersionSistema v = versionRepo.findById(id).orElseThrow(() -> new RuntimeException("No encontrado"));
        if (body.containsKey("version")) v.setVersion((String) body.get("version"));
        if (body.containsKey("urlDescarga")) v.setUrlDescarga((String) body.get("urlDescarga"));
        if (body.containsKey("notas")) v.setNotas((String) body.get("notas"));
        if (body.containsKey("activo")) v.setActivo(Boolean.parseBoolean(String.valueOf(body.get("activo"))));
        return ResponseEntity.ok(versionRepo.save(v));
    }

    @DeleteMapping("/versiones/{id}")
    public ResponseEntity<?> eliminarVersion(@RequestHeader("X-Admin-Token") String token, @PathVariable Long id) {
        requiereToken(token);
        versionRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ─── DESARROLLADORES (cuentas para publicar modulos terceros) ─────────
    @GetMapping("/desarrolladores")
    public ResponseEntity<?> listarDesarrolladores(@RequestHeader("X-Admin-Token") String token) {
        requiereToken(token);
        return ResponseEntity.ok(desarrolladorRepo.findAll());
    }

    @PostMapping("/desarrolladores")
    public ResponseEntity<?> crearDesarrollador(@RequestHeader("X-Admin-Token") String token,
                                                @RequestBody Map<String, Object> body) {
        requiereToken(token);
        String correo = (String) body.getOrDefault("correo", "");
        if (desarrolladorRepo.existsByCorreo(correo)) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "El correo ya existe"));
        }
        Desarrollador d = new Desarrollador();
        d.setCorreo(correo);
        d.setNombre((String) body.getOrDefault("nombre", ""));
        String clave = (String) body.get("clave");
        d.setClave(clave != null && !clave.isBlank() ? clave : UUID.randomUUID().toString().substring(0, 8));
        d.setActivo(true);
        return ResponseEntity.ok(desarrolladorRepo.save(d));
    }

    @PutMapping("/desarrolladores/{id}")
    public ResponseEntity<?> actualizarDesarrollador(@RequestHeader("X-Admin-Token") String token,
                                                    @PathVariable Long id, @RequestBody Map<String, Object> body) {
        requiereToken(token);
        Desarrollador d = desarrolladorRepo.findById(id).orElseThrow(() -> new RuntimeException("No encontrado"));
        if (body.containsKey("nombre")) d.setNombre((String) body.get("nombre"));
        if (body.containsKey("correo")) d.setCorreo((String) body.get("correo"));
        if (body.containsKey("clave")) d.setClave((String) body.get("clave"));
        if (body.containsKey("activo")) d.setActivo(Boolean.parseBoolean(String.valueOf(body.get("activo"))));
        return ResponseEntity.ok(desarrolladorRepo.save(d));
    }

    @DeleteMapping("/desarrolladores/{id}")
    public ResponseEntity<?> eliminarDesarrollador(@RequestHeader("X-Admin-Token") String token, @PathVariable Long id) {
        requiereToken(token);
        desarrolladorRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ─── CONFIG DEL HUB (comision) ─────────
    @GetMapping("/config")
    public ResponseEntity<?> obtenerConfig(@RequestHeader("X-Admin-Token") String token) {
        requiereToken(token);
        return ResponseEntity.ok(Map.of("comision", hubConfigService.getComision()));
    }
}


