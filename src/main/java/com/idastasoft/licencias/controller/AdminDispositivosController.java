package com.idastasoft.licencias.controller;

import com.idastasoft.licencias.model.Licencia;
import com.idastasoft.licencias.model.PackDispositivos;
import com.idastasoft.licencias.service.LicenciaService;
import com.idastasoft.licencias.service.PackDispositivosService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ADMIN de los packs de dispositivos (REQ-HUB-PACK-DISP-01).
 *
 * <p>Vive bajo {@code /api/admin/gestion/**}, asi que lo cubre el JWT de admin
 * del Hub (no hace falta token a mano, ver JwtAuthFilter).</p>
 */
@RestController
@RequestMapping("/api/admin/gestion/dispositivos")
public class AdminDispositivosController {

    private final PackDispositivosService packService;
    private final LicenciaService licenciaService;

    public AdminDispositivosController(PackDispositivosService packService, LicenciaService licenciaService) {
        this.packService = packService;
        this.licenciaService = licenciaService;
    }

    /** Catalogo completo (incluye los packs sin precio: los que aun no se ofrecen). */
    @GetMapping("/packs")
    public ResponseEntity<?> listar() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("packs", packService.catalogoCompleto());
        return ResponseEntity.ok(res);
    }

    /** Crea o actualiza un pack (si ya existe el codigo, lo edita). */
    @PostMapping("/packs")
    public ResponseEntity<?> guardar(@RequestBody Map<String, Object> body) {
        String codigo = (String) body.getOrDefault("codigo", "");
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            PackDispositivos p = new PackDispositivos();
            p.setCodigo(codigo);
            p.setNombre((String) body.get("nombre"));
            p.setDescripcion((String) body.get("descripcion"));
            p.setDispositivos(intDe(body.get("dispositivos"), 5));
            p.setPrecioMensual(precioDe(body.get("precioMensual")));
            p.setPrecioPermanente(precioDe(body.get("precioPermanente")));
            p.setActivo(!Boolean.FALSE.equals(body.get("activo")));
            PackDispositivos guardado = packService.guardar(p);
            res.put("ok", true);
            res.put("mensaje", "Pack guardado");
            res.put("pack", guardado);
        } catch (IllegalArgumentException e) {
            res.put("ok", false);
            res.put("mensaje", e.getMessage());
        }
        return ResponseEntity.ok(res);
    }

    @PutMapping("/packs/{codigo}")
    public ResponseEntity<?> actualizar(@PathVariable String codigo, @RequestBody Map<String, Object> body) {
        body.put("codigo", codigo);
        return guardar(body);
    }

    @DeleteMapping("/packs/{codigo}")
    public ResponseEntity<?> eliminar(@PathVariable String codigo) {
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            packService.eliminar(codigo);
            res.put("ok", true);
            res.put("mensaje", "Pack eliminado");
        } catch (RuntimeException e) {
            res.put("ok", false);
            res.put("mensaje", "No se pudo eliminar el pack: " + e.getMessage());
        }
        return ResponseEntity.ok(res);
    }

    /** Quita el pack de una licencia (vuelve al cupo base). */
    @PostMapping("/licencias/quitar-pack")
    public ResponseEntity<?> quitarPack(@RequestBody Map<String, Object> body) {
        String correo = (String) body.getOrDefault("correo", "");
        Map<String, Object> res = new LinkedHashMap<>();
        boolean habia = licenciaService.quitarPackDispositivos(correo);
        res.put("ok", true);
        res.put("mensaje", habia ? "Pack quitado: el cupo vuelve al base" : "Esa licencia no tenia pack");
        return ResponseEntity.ok(res);
    }

    /**
     * Resumen de TODAS las licencias de una vez: tipo de MOODU, cupo base +
     * extra y el pack vigente. El panel lo usa para la columna "Tipo" y para
     * la tarjeta de cupo sin llamar endpoint por licencia.
     */
    @GetMapping("/resumen")
    public ResponseEntity<?> resumen() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Licencia l : licenciaService.todasLasLicencias()) {
            Map<String, Object> m = new LinkedHashMap<>();
            var tipo = licenciaService.tipoDe(l);
            var pack = packService.packDe(l);
            m.put("id", l.getId());
            m.put("correo", l.getCorreo());
            m.put("activa", l.isActiva());
            m.put("tipoMoodu", tipo.name());
            m.put("capacidades", tipo.capacidades());
            m.put("ilimitados", tipo.maquinasIlimitadas());
            m.put("base", l.getMaquinasPermitidas());
            m.put("extra", packService.dispositivosExtraDe(l));
            m.put("total", licenciaService.maquinasEfectivas(l));
            m.put("codigoPack", pack == null ? null : pack.getCodigo());
            m.put("planPack", pack == null ? null : pack.getPlan());
            m.put("packVigente", pack != null && pack.vigente());
            m.put("vigenciaPack", pack == null || pack.getVigenciaHasta() == null
                ? null : pack.getVigenciaHasta().toString());
            out.add(m);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("licencias", out);
        return ResponseEntity.ok(res);
    }

    /**
     * Asigna (o reemplaza) el pack de una licencia. El pago lo hace el
     * administrador fuera del Hub (§11.90: hoy es presencial); aca queda
     * registrado, que es lo mismo que hace la compra publica pero por correo
     * (el admin no conoce la clave de activacion).
     */
    @PostMapping("/licencias/asignar-pack")
    public ResponseEntity<?> asignarPack(@RequestBody Map<String, Object> body) {
        String correo = (String) body.getOrDefault("correo", "");
        String codigo = (String) body.getOrDefault("codigo", "");
        String plan = (String) body.getOrDefault("plan", "PERMANENTE");
        return ResponseEntity.ok(licenciaService.asignarPackPorCorreo(correo, codigo, plan));
    }

    /** Estado del cupo de una licencia (lo usa el modal Ver de la licencia). */
    @GetMapping("/licencias/{correo}/dispositivos")
    public ResponseEntity<?> estadoLicencia(@PathVariable String correo) {
        Map<String, Object> res = new LinkedHashMap<>();
        Licencia l = licenciaService.buscarPorCorreo(correo);
        if (l == null) {
            res.put("ok", false);
            res.put("mensaje", "Licencia no encontrada");
            return ResponseEntity.ok(res);
        }
        res.put("ok", true);
        res.put("tipoMoodu", licenciaService.tipoDe(l).name());
        res.put("base", l.getMaquinasPermitidas());
        res.put("total", licenciaService.maquinasEfectivas(l));
        res.put("pack", packService.packDe(l));
        return ResponseEntity.ok(res);
    }

    private int intDe(Object v, int porDefecto) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return porDefecto;
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    /** Precio: null/blank = "no se ofrece con esa forma de pago". */
    private BigDecimal precioDe(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Precio invalido: " + s);
        }
    }
}