package com.idastasoft.licencias.controller;

import com.idastasoft.licencias.model.ModuloCatalogo;
import com.idastasoft.licencias.model.ModuloFoto;
import com.idastasoft.licencias.model.VersionSistema;
import com.idastasoft.licencias.repository.ModuloCatalogoRepo;
import com.idastasoft.licencias.repository.ModuloFotoRepo;
import com.idastasoft.licencias.repository.VersionSistemaRepo;
import com.idastasoft.licencias.service.ContactoService;
import com.idastasoft.licencias.service.HubConfigService;
import com.idastasoft.licencias.service.ModuloPlanService;
import com.idastasoft.licencias.service.ParametroService;
import com.idastasoft.licencias.service.ValoracionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogo publico del MOODU Hub. Lo consume la Tienda del desktop.
 * No requiere auth: expone modulos y la version disponible del sistema.
 */
@RestController
@RequestMapping("/api/catalogo")
public class CatalogoController {

    private final ModuloCatalogoRepo moduloRepo;
    private final ModuloFotoRepo fotoRepo;
    private final VersionSistemaRepo versionRepo;
    private final ModuloPlanService planService;
    private final HubConfigService hubConfig;
    private final ContactoService contactoService;
    private final ParametroService parametroService;
    private final ValoracionService valoracionService;

    public CatalogoController(ModuloCatalogoRepo moduloRepo, ModuloFotoRepo fotoRepo,
                               VersionSistemaRepo versionRepo, ModuloPlanService planPlanService,
                               HubConfigService hubConfig, ContactoService contactoService,
                               ParametroService parametroService,
                               ValoracionService valoracionService) {
        this.moduloRepo = moduloRepo;
        this.fotoRepo = fotoRepo;
        this.versionRepo = versionRepo;
        this.planService = planPlanService;
        this.hubConfig = hubConfig;
        this.contactoService = contactoService;
        this.parametroService = parametroService;
        this.valoracionService = valoracionService;
    }

    private ModuloCatalogo conFotos(ModuloCatalogo m) {
        m.setFotoIds(fotoRepo.findByModuloIdOrderByOrdenAscIdAsc(m.getId())
                .stream().map(ModuloFoto::getId).toList());
        return m;
    }

    /**
     * Modulo + fotos + planes, como mapas (la entidad no lleva planes: viven en
     * otra tabla). Si el modulo no tiene planes propios, se sintetiza uno desde
     * los campos legacy para que lo ya cargado siga funcionando.
     */
    private Map<String, Object> conPlanes(ModuloCatalogo m) {
        conFotos(m);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", m.getId());
        out.put("clave", m.getClave());
        out.put("nombre", m.getNombre());
        out.put("descripcion", m.getDescripcion());
        out.put("imagen", m.getImagen());
        out.put("iconoUrl", m.getIconoUrl());
        out.put("fotoIds", m.getFotoIds());
        out.put("desarrollador", m.getDesarrollador());
        out.put("version", m.getVersion());
        out.put("fechaPublicacion", m.getFechaPublicacion() != null ? m.getFechaPublicacion().toString() : null);
        out.put("origen", m.getOrigen().name());
        out.put("tipo", m.getTipo().name());
        out.put("precio", m.getPrecio());
        out.put("linkDescarga", m.getLinkDescarga());
        out.put("linkRepositorio", m.getLinkRepositorio());
        out.put("vigenciaDias", m.getVigenciaDias());
        out.put("activo", m.isActivo());
        out.put("planes", planService.planesPublicos(m.getId(), m));
        // Reputacion del modulo (solo lo visible): la Tienda la muestra en la
        // tarjeta para que el usuario vea las opiniones sin abrir el detalle
        // (REQ-HUB-OPINIONES-01). El resumen completo sigue en /api/valoraciones.
        out.put("valoracion", valoracionService.resumen(m.getClave()));
        return out;
    }

    /** Lista de modulos activos del catalogo (oficiales y terceros). */
    @GetMapping("/modulos")
    public ResponseEntity<?> listarModulos() {
        List<Map<String, Object>> modulos = new ArrayList<>();
        for (ModuloCatalogo m : moduloRepo.findAll()) {
            if (m.isActivo()) modulos.add(conPlanes(m));
        }
        return ResponseEntity.ok(modulos);
    }

    /** Detalle de un modulo por clave. */
    @GetMapping("/modulos/{clave}")
    public ResponseEntity<?> detalleModulo(@PathVariable String clave) {
        ModuloCatalogo m = moduloRepo.findByClave(clave);
        if (m == null || !m.isActivo()) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "Modulo no encontrado"));
        }
        return ResponseEntity.ok(conPlanes(m));
    }

    /**
     * Medios de contacto del soporte de MOODU (REQ-HUB-PLANES-01).
     * Los muestra la Tienda en el modal "Mejorar Plan". Editables desde el
     * panel admin del Hub (tabla hub_contacto), sin redesplegar.
     */
    @GetMapping("/contacto")
    public ResponseEntity<?> contacto() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("contacto", contactoService.mapa());
        out.put("sitioWeb", hubConfig.getSitioWeb());
        return ResponseEntity.ok(out);
    }

    /**
     * Tarjeta de identidad del Hub: donde esta, donde esta la tienda Comprape y
     * si se anuncia una mudanza de hosting (REQ-HUB-DESCUBRIMIENTO-01).
     *
     * <p>El desktop lo usa para 2 cosas: (a) avisar con plazo que el Hub actual
     * se va a mudar, (b) saber la URL de Comprape sin que el usuario la escriba.
     * La mudanza solo se anuncia si hay URL nueva: sin destino no hay nada que
     * hacer.</p>
     *
     * <p>Este aviso vive mientras el Hub VIEJO siga vivo (es lo unico que puede
     * anunciarlo a tiempo). Cuando ya cayo, el desktop cae al descriptor estatico
     * (GitHub/moodu.com) que publica el admin: por eso son dos mecanismos.</p>
     */
    @GetMapping("/hub")
    public ResponseEntity<?> hub(HttpServletRequest req) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.putAll(parametroService.mudanzaYUrls(urlBase(req)));
        return ResponseEntity.ok(out);
    }

    /**
     * Imagen de la portada de Soporte (la que sube el admin). Los bytes salen
     * del base64 guardado en BD (el disco del hosting es efimero). Cache largo:
     * la portada cambia solo cuando el admin la cambia.
     */
    @GetMapping("/portada/{id}")
    public ResponseEntity<?> portada(@PathVariable Integer id) {
        byte[] bytes = contactoService.portadaBytes(id);
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(mimeImagen(bytes))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(bytes);
    }

    /** PNG / JPEG / WEBP por los bytes magicos (no por la extension). */
    private static MediaType mimeImagen(byte[] b) {
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return MediaType.IMAGE_PNG;
        }
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) {
            return MediaType.IMAGE_JPEG;
        }
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /** URL publica del Hub deducida de la peticion (respeta el proxy: X-Forwarded-Proto). */
    private static String urlBase(HttpServletRequest req) {
        if (req == null) return null;
        String proto = req.getHeader("X-Forwarded-Proto");
        if (proto == null || proto.isBlank()) proto = req.getScheme();
        String host = req.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) host = req.getHeader("Host");
        if (host == null || host.isBlank()) return null;
        return proto + "://" + host;
    }

    /** Fotos de un modulo (solo metadata; los bytes van por /fotos/{id}). */
    @GetMapping("/modulos/{clave}/fotos")
    public ResponseEntity<?> fotosModulo(@PathVariable String clave) {
        ModuloCatalogo m = moduloRepo.findByClave(clave);
        if (m == null || !m.isActivo()) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "Modulo no encontrado"));
        }
        var out = fotoRepo.findByModuloIdOrderByOrdenAscIdAsc(m.getId()).stream()
                .map(f -> Map.of("id", f.getId(), "nombre", f.getNombre()));
        return ResponseEntity.ok(out.toList());
    }

    /** Bytes de una foto del catalogo (publica, cacheable). */
    @GetMapping("/modulos/{clave}/fotos/{fotoId}")
    public ResponseEntity<?> fotoBytes(@PathVariable String clave, @PathVariable Long fotoId) {
        ModuloCatalogo m = moduloRepo.findByClave(clave);
        if (m == null || !m.isActivo()) {
            return ResponseEntity.status(404).build();
        }
        ModuloFoto f = fotoRepo.findById(fotoId).orElse(null);
        if (f == null || !f.getModulo().getId().equals(m.getId())) {
            return ResponseEntity.status(404).build();
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(f.getDatos());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).build();
        }
        MediaType tipo = switch (f.getMime()) {
            case "image/jpeg" -> MediaType.IMAGE_JPEG;
            case "image/webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.IMAGE_PNG;
        };
        return ResponseEntity.ok()
                .contentType(tipo)
                .header("Cache-Control", "public, max-age=86400")
                .body(bytes);
    }

    /** Version disponible del instalador MOODU (la mas reciente activa). */
    @GetMapping("/version-sistema")
    public ResponseEntity<?> versionSistema() {
        List<VersionSistema> todas = versionRepo.findAll().stream()
                .filter(VersionSistema::isActivo)
                .sorted((a, b) -> b.getFecha().compareTo(a.getFecha()))
                .toList();
        if (todas.isEmpty()) {
            return ResponseEntity.ok(Map.of("version", "0.0.0", "urlDescarga", "", "notas", ""));
        }
        VersionSistema v = todas.get(0);
        return ResponseEntity.ok(Map.of(
                "version", v.getVersion(),
                "urlDescarga", v.getUrlDescarga() != null ? v.getUrlDescarga() : "",
                "notas", v.getNotas() != null ? v.getNotas() : "",
                "fecha", v.getFecha().toString()
        ));
    }
}
