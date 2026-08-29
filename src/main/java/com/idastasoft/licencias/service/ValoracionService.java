package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.Valoracion;
import com.idastasoft.licencias.repository.ValoracionRepo;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Valoraciones de MOODU y de modulos. Regla: un correo solo puede tener UNA
 * valoracion por destino (system = "moodu" o clave de modulo). Guardar una
 * segunda vez = EDITAR la existente (upsert) refrescando fechaModificacion.
 * El ordenamiento SIEMPRE es por fechaModificacion (ultimo cambio), no por
 * fechaCreacion.
 */
@Service
public class ValoracionService {

    private final ValoracionRepo repo;

    public ValoracionService(ValoracionRepo repo) {
        this.repo = repo;
    }

    public static final String DESTINO_MOODU = "moodu";

    public Map<String, Object> guardar(String destino, String correo, int puntaje, String comentario) {
        String dest = (destino == null || destino.isBlank()) ? DESTINO_MOODU : destino.trim().toLowerCase();
        String mail = correo == null ? "" : correo.trim().toLowerCase();
        if (mail.isBlank() || !mail.contains("@") || mail.length() < 5) {
            throw new IllegalArgumentException("Correo electronico invalido");
        }
        if (puntaje < 1 || puntaje > 5) {
            throw new IllegalArgumentException("La valoracion debe ser de 1 a 5 estrellas");
        }
        if (dest.length() > 100) dest = dest.substring(0, 100);

        Valoracion v = repo.findByDestinoAndCorreo(dest, mail).orElse(null);
        if (v == null) {
            v = new Valoracion();
            v.setDestino(dest);
            v.setCorreo(mail);
            v.setFechaCreacion(LocalDateTime.now());
            v.setVisible(true);
        }
        v.setPuntaje(puntaje);
        v.setComentario(comentario != null && !comentario.isBlank() ? comentario.trim() : null);
        v.setFechaModificacion(LocalDateTime.now());
        repo.save(v);
        return toMap(v);
    }

    public Map<String, Object> listar(String destino) {
        String dest = (destino == null || destino.isBlank()) ? DESTINO_MOODU : destino.trim().toLowerCase();
        List<Valoracion> items = repo.findByDestinoAndVisibleTrueOrderByFechaModificacionDesc(dest);
        return respuesta(dest, items);
    }

    public Map<String, Object> mia(String destino, String correo) {
        String dest = (destino == null || destino.isBlank()) ? DESTINO_MOODU : destino.trim().toLowerCase();
        String mail = correo == null ? "" : correo.trim().toLowerCase();
        if (mail.isBlank()) {
            return Map.of("tiene", false);
        }
        Valoracion v = repo.findByDestinoAndCorreo(dest, mail).orElse(null);
        if (v == null) {
            return Map.of("tiene", false);
        }
        Map<String, Object> m = toMap(v);
        m.put("tiene", true);
        return m;
    }

    /** Para el admin: lista TODAS (incluye ocultas). destino vacio = todas. */
    public List<Map<String, Object>> listarAdmin(String destino) {
        if (destino == null || destino.isBlank()) {
            return repo.findAllByOrderByFechaModificacionDesc().stream()
                    .map(this::toMap)
                    .toList();
        }
        String dest = destino.trim().toLowerCase();
        return repo.findByDestinoOrderByFechaModificacionDesc(dest).stream()
                .map(this::toMap)
                .toList();
    }

    public void ocultar(Long id, boolean visible) {
        Valoracion v = repo.findById(id).orElseThrow(() -> new RuntimeException("Valoracion no encontrada"));
        v.setVisible(visible);
        v.setFechaModificacion(LocalDateTime.now());
        repo.save(v);
    }

    public void eliminar(Long id) {
        repo.deleteById(id);
    }

    /** Promedio redondeado a 1 decimal + total. Solo visibles. */
    private Map<String, Object> respuesta(String destino, List<Valoracion> items) {
        double promedio = items.isEmpty() ? 0 : Math.round(items.stream().mapToInt(Valoracion::getPuntaje).average().orElse(0) * 10.0) / 10.0;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("destino", destino);
        r.put("promedio", promedio);
        r.put("total", items.size());
        r.put("items", items.stream().map(this::toMap).toList());
        return r;
    }

    private Map<String, Object> toMap(Valoracion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("destino", v.getDestino());
        m.put("correo", v.getCorreo());
        m.put("puntaje", v.getPuntaje());
        m.put("comentario", v.getComentario());
        m.put("fechaCreacion", v.getFechaCreacion().toString());
        m.put("fechaModificacion", v.getFechaModificacion().toString());
        m.put("visible", v.isVisible());
        return m;
    }
}