package com.idastasoft.licencias.controller;

import com.idastasoft.licencias.model.Desarrollador;
import com.idastasoft.licencias.model.Licencia;
import com.idastasoft.licencias.model.ModuloCatalogo;
import com.idastasoft.licencias.model.ModuloFoto;
import com.idastasoft.licencias.model.Paquete;
import com.idastasoft.licencias.model.VersionSistema;
import com.idastasoft.licencias.repository.DesarrolladorRepo;
import com.idastasoft.licencias.repository.LicenciaRepo;
import com.idastasoft.licencias.repository.MaquinaRepo;
import com.idastasoft.licencias.repository.ModuloCatalogoRepo;
import com.idastasoft.licencias.repository.ModuloFotoRepo;
import com.idastasoft.licencias.repository.PaqueteRepo;
import com.idastasoft.licencias.repository.VersionSistemaRepo;
import com.idastasoft.licencias.service.HubConfigService;
import com.idastasoft.licencias.service.LicenciaService;
import com.idastasoft.licencias.service.ValoracionService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/gestion")
public class AdminGestionController {

    private final LicenciaRepo licenciaRepo;
    private final PaqueteRepo paqueteRepo;
    private final MaquinaRepo maquinaRepo;
    private final LicenciaService licenciaService;
    private final ModuloCatalogoRepo moduloRepo;
    private final ModuloFotoRepo fotoRepo;
    private final VersionSistemaRepo versionRepo;
    private final DesarrolladorRepo desarrolladorRepo;
    private final HubConfigService hubConfigService;
    private final ValoracionService valoracionService;

    public AdminGestionController(LicenciaRepo licenciaRepo,
                                  PaqueteRepo paqueteRepo, MaquinaRepo maquinaRepo,
                                  LicenciaService licenciaService,
                                  ModuloCatalogoRepo moduloRepo,
                                  ModuloFotoRepo fotoRepo,
                                  VersionSistemaRepo versionRepo,
                                  DesarrolladorRepo desarrolladorRepo,
                                  HubConfigService hubConfigService,
                                  ValoracionService valoracionService) {
        this.licenciaRepo = licenciaRepo;
        this.paqueteRepo = paqueteRepo;
        this.maquinaRepo = maquinaRepo;
        this.licenciaService = licenciaService;
        this.moduloRepo = moduloRepo;
        this.fotoRepo = fotoRepo;
        this.versionRepo = versionRepo;
        this.desarrolladorRepo = desarrolladorRepo;
        this.hubConfigService = hubConfigService;
        this.valoracionService = valoracionService;
    }

    // ─── LICENCIAS ──────────────────────────────
    @GetMapping("/licencias")
    public ResponseEntity<?> listarLicencias() {
        return ResponseEntity.ok(licenciaRepo.findAll());
    }

    @PostMapping("/licencias")
    @Transactional
    public ResponseEntity<?> crearLicencia(@RequestBody Map<String, Object> body) {
        Licencia l = new Licencia();
        l.setNombre((String) body.getOrDefault("nombre", ""));
        l.setApellido((String) body.getOrDefault("apellido", ""));
        l.setCorreo((String) body.getOrDefault("correo", ""));
        String clave = (String) body.get("claveActivacion");
        l.setClaveActivacion(clave != null && !clave.isBlank() ? clave
            : UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        l.setMaquinasPermitidas(Integer.parseInt(String.valueOf(body.getOrDefault("maquinasPermitidas", 3))));
        l.setVigenciaUnidad(Licencia.UnidadVigencia.valueOf(
            ((String) body.getOrDefault("vigenciaUnidad", "MESES")).toUpperCase()));
        l.setVigenciaValor(Integer.parseInt(String.valueOf(body.getOrDefault("vigenciaValor", 12))));
        l.setFechaInicio(LocalDateTime.now());
        l.setActiva(true);
        Licencia guardada = licenciaRepo.save(l);
        // Terminos por modulo: detalle explicito o herencia de la global.
        Object det = body.get("modulosDetalle");
        if (det instanceof List) {
            licenciaService.guardarTerminos(guardada, (List<Map<String, Object>>) det);
        } else {
            licenciaService.heredarTerminos(guardada, (List<String>) body.getOrDefault("modulos", List.of()));
        }
        return ResponseEntity.ok(licenciaRepo.findById(guardada.getId()).orElse(guardada));
    }

    @PutMapping("/licencias/{id}")
    @Transactional
    public ResponseEntity<?> actualizarLicencia(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Licencia l = licenciaRepo.findById(id).orElseThrow(() -> new RuntimeException("No encontrada"));
        if (body.containsKey("nombre")) l.setNombre((String) body.get("nombre"));
        if (body.containsKey("apellido")) l.setApellido((String) body.get("apellido"));
        if (body.containsKey("correo")) l.setCorreo((String) body.get("correo"));
        if (body.containsKey("maquinasPermitidas")) l.setMaquinasPermitidas(Integer.parseInt(String.valueOf(body.get("maquinasPermitidas"))));
        if (body.containsKey("vigenciaUnidad")) l.setVigenciaUnidad(Licencia.UnidadVigencia.valueOf(((String) body.get("vigenciaUnidad")).toUpperCase()));
        if (body.containsKey("vigenciaValor")) l.setVigenciaValor(Integer.parseInt(String.valueOf(body.get("vigenciaValor"))));
        if (body.containsKey("activa")) l.setActiva(Boolean.parseBoolean(String.valueOf(body.get("activa"))));
        licenciaRepo.save(l);
        // Con detalle explicito manda el detalle; con lista simple se hereda
        // la (nueva) vigencia global. Sin ninguno no se tocan los terminos.
        Object det = body.get("modulosDetalle");
        if (det instanceof List) {
            licenciaService.guardarTerminos(l, (List<Map<String, Object>>) det);
        } else if (body.containsKey("modulos")) {
            licenciaService.heredarTerminos(l, (List<String>) body.get("modulos"));
        }
        return ResponseEntity.ok(licenciaRepo.findById(id).orElse(l));
    }

    @DeleteMapping("/licencias/{id}")
    public ResponseEntity<?> eliminarLicencia(@PathVariable Long id) {
        licenciaRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ─── MAQUINAS (dispositivos de una licencia) ─────────
    @GetMapping("/licencias/{id}/maquinas")
    public ResponseEntity<?> maquinas(@PathVariable Long id) {
        return ResponseEntity.ok(licenciaService.maquinasDeLicencia(id));
    }

    @PostMapping("/licencias/{id}/maquinas/{idHardware}/desactivar")
    public ResponseEntity<?> desactivar(@PathVariable Long id, @PathVariable String idHardware) {
        boolean ok = licenciaService.desactivarMaquina(id, idHardware);
        return ResponseEntity.ok(Map.of("ok", ok, "mensaje",
            ok ? "Maquina desactivada. El equipo cerrara sesion en MOODU." : "Maquina no encontrada"));
    }

    // ─── PAQUETES ──────────────────────────────
    @GetMapping("/paquetes")
    public ResponseEntity<?> listarPaquetes() {
        return ResponseEntity.ok(paqueteRepo.findAll());
    }

    @PostMapping("/paquetes")
    public ResponseEntity<?> crearPaquete(@RequestBody Map<String, Object> body) {
        Paquete p = new Paquete();
        p.setNombre((String) body.getOrDefault("nombre", ""));
        p.setDescripcion((String) body.getOrDefault("descripcion", ""));
        p.setModulos((List<String>) body.getOrDefault("modulos", List.of()));
        p.setPrecio(BigDecimal.valueOf(Double.parseDouble(String.valueOf(body.getOrDefault("precio", 0)))));
        p.setActivo(true);
        return ResponseEntity.ok(paqueteRepo.save(p));
    }

    @PutMapping("/paquetes/{id}")
    public ResponseEntity<?> actualizarPaquete(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Paquete p = paqueteRepo.findById(id).orElseThrow(() -> new RuntimeException("No encontrado"));
        if (body.containsKey("nombre")) p.setNombre((String) body.get("nombre"));
        if (body.containsKey("descripcion")) p.setDescripcion((String) body.get("descripcion"));
        if (body.containsKey("modulos")) p.setModulos((List<String>) body.get("modulos"));
        if (body.containsKey("precio")) p.setPrecio(BigDecimal.valueOf(Double.parseDouble(String.valueOf(body.get("precio")))));
        if (body.containsKey("activo")) p.setActivo(Boolean.parseBoolean(String.valueOf(body.get("activo"))));
        return ResponseEntity.ok(paqueteRepo.save(p));
    }

    @DeleteMapping("/paquetes/{id}")
    public ResponseEntity<?> eliminarPaquete(@PathVariable Long id) {
        paqueteRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ─── CATALOGO DE MODULOS (oficiales y terceros) ─────────
    @GetMapping("/catalogo/modulos")
    public ResponseEntity<?> listarCatalogo() {
        var todos = moduloRepo.findAll();
        for (ModuloCatalogo m : todos) {
            m.setFotoIds(fotoRepo.findByModuloIdOrderByOrdenAscIdAsc(m.getId())
                    .stream().map(ModuloFoto::getId).toList());
        }
        return ResponseEntity.ok(todos);
    }

    @PostMapping("/catalogo/modulos")
    public ResponseEntity<?> crearModuloCatalogo(@RequestBody Map<String, Object> body) {
        ModuloCatalogo m = new ModuloCatalogo();
        aplicarCamposModulo(m, body);
        return ResponseEntity.ok(moduloRepo.save(m));
    }

    @PutMapping("/catalogo/modulos/{id}")
    public ResponseEntity<?> actualizarModuloCatalogo(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ModuloCatalogo m = moduloRepo.findById(id).orElseThrow(() -> new RuntimeException("No encontrado"));
        aplicarCamposModulo(m, body);
        return ResponseEntity.ok(moduloRepo.save(m));
    }

    @DeleteMapping("/catalogo/modulos/{id}")
    public ResponseEntity<?> eliminarModuloCatalogo(@PathVariable Long id) {
        moduloRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ─── FOTOS DEL MODULO (max 10, REQ-HUB-FOTOS-01) ─────────
    // En BD (base64): el disco del hosting es efimero y en filesystem se
    // perderian con cada redeploy. Tope 1 MB por foto, solo PNG/JPEG/WebP.
    @PostMapping("/catalogo/modulos/{id}/fotos")
    public ResponseEntity<?> subirFoto(@PathVariable Long id,
                                       @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        ModuloCatalogo m = moduloRepo.findById(id).orElse(null);
        if (m == null) return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "Modulo no encontrado"));
        if (fotoRepo.countByModuloId(id) >= 10) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "Maximo 10 fotos por modulo"));
        }
        String mime = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!mime.equals("image/png") && !mime.equals("image/jpeg") && !mime.equals("image/webp")) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "Solo PNG, JPEG o WebP"));
        }
        if (file.getSize() > 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "Maximo 1 MB por foto"));
        }
        try {
            ModuloFoto f = new ModuloFoto();
            f.setModulo(m);
            f.setNombre(file.getOriginalFilename() != null ? file.getOriginalFilename() : "foto");
            f.setMime(mime);
            f.setDatos(java.util.Base64.getEncoder().encodeToString(file.getBytes()));
            int max = fotoRepo.findByModuloIdOrderByOrdenAscIdAsc(id).stream()
                    .mapToInt(ModuloFoto::getOrden).max().orElse(-1);
            f.setOrden(max + 1);
            ModuloFoto g = fotoRepo.save(f);
            return ResponseEntity.ok(Map.of("ok", true, "id", g.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "No se pudo guardar la foto"));
        }
    }

    @GetMapping("/catalogo/modulos/{id}/fotos")
    public ResponseEntity<?> listarFotos(@PathVariable Long id) {
        if (!moduloRepo.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "Modulo no encontrado"));
        }
        var out = fotoRepo.findByModuloIdOrderByOrdenAscIdAsc(id).stream().map(f -> Map.of(
                "id", f.getId(), "nombre", f.getNombre(), "mime", f.getMime(), "orden", f.getOrden()));
        return ResponseEntity.ok(out.toList());
    }

    @DeleteMapping("/catalogo/modulos/{id}/fotos/{fotoId}")
    public ResponseEntity<?> eliminarFoto(@PathVariable Long id, @PathVariable Long fotoId) {
        ModuloFoto f = fotoRepo.findById(fotoId).orElse(null);
        if (f == null || !f.getModulo().getId().equals(id)) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "Foto no encontrada"));
        }
        fotoRepo.deleteById(fotoId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Icono del modulo (upload PNG/JPEG/WebP <= 512 KB, se guarda data-URL). */
    @PostMapping("/catalogo/modulos/{id}/icono")
    public ResponseEntity<?> subirIcono(@PathVariable Long id,
                                        @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        ModuloCatalogo m = moduloRepo.findById(id).orElse(null);
        if (m == null) return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "Modulo no encontrado"));
        String mime = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!mime.equals("image/png") && !mime.equals("image/jpeg") && !mime.equals("image/webp")) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "Solo PNG, JPEG o WebP"));
        }
        if (file.getSize() > 512 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "Maximo 512 KB para el icono"));
        }
        try {
            String b64 = java.util.Base64.getEncoder().encodeToString(file.getBytes());
            m.setIconoUrl("data:" + mime + ";base64," + b64);
            moduloRepo.save(m);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "No se pudo guardar el icono"));
        }
    }

    private void aplicarCamposModulo(ModuloCatalogo m, Map<String, Object> body) {
        if (body.containsKey("clave")) m.setClave((String) body.get("clave"));
        if (body.containsKey("nombre")) m.setNombre((String) body.get("nombre"));
        if (body.containsKey("descripcion")) m.setDescripcion((String) body.get("descripcion"));
        if (body.containsKey("imagen")) m.setImagen((String) body.get("imagen"));
        if (body.containsKey("desarrollador")) m.setDesarrollador((String) body.get("desarrollador"));
        if (body.containsKey("version")) m.setVersion((String) body.getOrDefault("version", "1.0.0"));
        //Fecha de publicacion AUTOMATICA: si no viene (o viene vacia) se usa
        //la fecha actual. Solo se respeta la enviada si trae un valor real.
        if (body.containsKey("fechaPublicacion") && body.get("fechaPublicacion") != null
                && !((String) body.get("fechaPublicacion")).isBlank()) {
            m.setFechaPublicacion(java.time.LocalDate.parse(((String) body.get("fechaPublicacion")).trim()));
        } else if (m.getFechaPublicacion() == null) {
            m.setFechaPublicacion(java.time.LocalDate.now());
        }
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
    public ResponseEntity<?> listarVersiones() {
        return ResponseEntity.ok(versionRepo.findAll());
    }

    @PostMapping("/versiones")
    public ResponseEntity<?> crearVersion(@RequestBody Map<String, Object> body) {
        VersionSistema v = new VersionSistema();
        v.setVersion((String) body.getOrDefault("version", "0.0.0"));
        v.setUrlDescarga((String) body.getOrDefault("urlDescarga", ""));
        v.setNotas((String) body.getOrDefault("notas", ""));
        v.setFecha(LocalDateTime.now());
        v.setActivo(true);
        return ResponseEntity.ok(versionRepo.save(v));
    }

    @PutMapping("/versiones/{id}")
    public ResponseEntity<?> actualizarVersion(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        VersionSistema v = versionRepo.findById(id).orElseThrow(() -> new RuntimeException("No encontrado"));
        if (body.containsKey("version")) v.setVersion((String) body.get("version"));
        if (body.containsKey("urlDescarga")) v.setUrlDescarga((String) body.get("urlDescarga"));
        if (body.containsKey("notas")) v.setNotas((String) body.get("notas"));
        if (body.containsKey("activo")) v.setActivo(Boolean.parseBoolean(String.valueOf(body.get("activo"))));
        return ResponseEntity.ok(versionRepo.save(v));
    }

    @DeleteMapping("/versiones/{id}")
    public ResponseEntity<?> eliminarVersion(@PathVariable Long id) {
        versionRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ─── DESARROLLADORES (cuentas para publicar modulos terceros) ─────────
    @GetMapping("/desarrolladores")
    public ResponseEntity<?> listarDesarrolladores() {
        return ResponseEntity.ok(desarrolladorRepo.findAll());
    }

    @PostMapping("/desarrolladores")
    public ResponseEntity<?> crearDesarrollador(@RequestBody Map<String, Object> body) {
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
    public ResponseEntity<?> actualizarDesarrollador(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Desarrollador d = desarrolladorRepo.findById(id).orElseThrow(() -> new RuntimeException("No encontrado"));
        if (body.containsKey("nombre")) d.setNombre((String) body.get("nombre"));
        if (body.containsKey("correo")) d.setCorreo((String) body.get("correo"));
        if (body.containsKey("clave")) d.setClave((String) body.get("clave"));
        if (body.containsKey("activo")) d.setActivo(Boolean.parseBoolean(String.valueOf(body.get("activo"))));
        return ResponseEntity.ok(desarrolladorRepo.save(d));
    }

    @DeleteMapping("/desarrolladores/{id}")
    public ResponseEntity<?> eliminarDesarrollador(@PathVariable Long id) {
        desarrolladorRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ─── VALORACIONES (moderacion) ─────────
    @GetMapping("/valoraciones")
    public ResponseEntity<?> listarValoraciones(@RequestParam(value = "destino", required = false) String destino) {
        return ResponseEntity.ok(valoracionService.listarAdmin(destino));
    }

    @PutMapping("/valoraciones/{id}")
    public ResponseEntity<?> actualizarValoracion(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean visible = Boolean.parseBoolean(String.valueOf(body.getOrDefault("visible", true)));
        valoracionService.ocultar(id, visible);
        return ResponseEntity.ok(Map.of("ok", true, "visible", visible));
    }

    @DeleteMapping("/valoraciones/{id}")
    public ResponseEntity<?> eliminarValoracion(@PathVariable Long id) {
        valoracionService.eliminar(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ─── CONFIG DEL HUB (comision) ─────────
    @GetMapping("/config")
    public ResponseEntity<?> obtenerConfig() {
        return ResponseEntity.ok(Map.of("comision", hubConfigService.getComision()));
    }
}
