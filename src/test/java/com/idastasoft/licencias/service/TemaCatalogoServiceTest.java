package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.TemaCatalogo;
import com.idastasoft.licencias.repository.TemaCatalogoRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Temas del Hub: los de FABRICA (los presets que trae MOODU de serie) NO se ofrecen
 * en la Tienda, solo se ven en el panel admin.
 *
 * <p>Lo que se cuida aca: el filtro NO se hace en la query sino en Java, porque la
 * columna es Boolean NULLABLE (filas viejas) y en Postgres {@code es_fabrica = false}
 * no cumple con NULL: las ocultaria de la Tienda sin querer. Y el NULL se lee como
 * "no es de fabrica" (= visible), que es lo que corresponde a un tema del admin.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TemaCatalogoServiceTest {

    @Mock
    private TemaCatalogoRepo repo;

    private TemaCatalogoService servicio;

    /** Repo en memoria: guardar de verdad, como en produccion (un mock que no
     *  persistiera haria fallar el flujo sin que hubiera un bug real). */
    private final Map<Long, TemaCatalogo> store = new LinkedHashMap<>();
    private long siguiente = 1;

    private static final String JSON_OK = "{\"nombre\":\"Prueba\",\"base\":\"light\",\"vars\":{"
            + "\"primary\":\"#2563eb\",\"primaryHover\":\"#1d4ed8\",\"bgBody\":\"#ffffff\","
            + "\"bgSurface\":\"#f8fafc\",\"textPrimary\":\"#0f172a\",\"success\":\"#16a34a\","
            + "\"warning\":\"#f59e0b\",\"danger\":\"#dc2626\"}}";

    @BeforeEach
    void preparar() {
        store.clear();
        siguiente = 1;
        when(repo.findAllByOrderByNombreAsc()).thenAnswer(i -> new ArrayList<>(store.values()));
        when(repo.findByActivoTrueOrderByNombreAsc()).thenAnswer(i -> {
            List<TemaCatalogo> out = new ArrayList<>();
            for (TemaCatalogo t : store.values()) if (t.isActivo()) out.add(t);
            out.sort((a, b) -> String.valueOf(a.getNombre()).compareToIgnoreCase(String.valueOf(b.getNombre())));
            return out;
        });
        when(repo.findByClaveIgnoreCase(anyString())).thenAnswer(i -> {
            // OJO: asignar a Object primero — String.valueOf(i.getArgument(0)) resuelve al
            // overload valueOf(char[]) en runtime y tira ClassCast (patron 11.451).
            Object arg = i.getArgument(0);
            String clave = String.valueOf(arg).trim();
            for (TemaCatalogo t : store.values()) {
                if (t.getClave() != null && t.getClave().equalsIgnoreCase(clave)) return Optional.of(t);
            }
            return Optional.empty();
        });
        when(repo.findById(any())).thenAnswer(i -> Optional.ofNullable(store.get(i.getArgument(0))));
        when(repo.save(any(TemaCatalogo.class))).thenAnswer(i -> {
            TemaCatalogo t = i.getArgument(0);
            if (t.getId() == null) t.setId(siguiente++);
            store.put(t.getId(), t);
            return t;
        });
        servicio = new TemaCatalogoService(repo);
    }

    private Map<String, Object> cuerpo(String nombre) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("nombre", nombre);
        b.put("grupo", "claros");
        b.put("contenido", JSON_OK);
        return b;
    }

    private TemaCatalogo guardar(String clave, boolean esFabrica, boolean activo) {
        return guardar(clave, esFabrica, activo, esFabrica ? "sistema" : "root");
    }

    private TemaCatalogo guardar(String clave, boolean esFabrica, boolean activo, String creadoPor) {
        TemaCatalogo t = new TemaCatalogo();
        t.setClave(clave);
        t.setNombre(clave);
        t.setGrupo("claros");
        t.setVersion("1.0.0");
        t.setBase("light");
        t.setContenido(JSON_OK);
        t.setActivo(activo);
        t.setEsFabrica(esFabrica ? Boolean.TRUE : null);
        t.setCreadoPor(creadoPor);
        return repo.save(t);
    }

    @Test
    @DisplayName("La Tienda no muestra los temas de fabrica pero el admin si")
    void listarPublicos_ocultaLosDeFabrica() {
        guardar("agua-clara", true, true);
        guardar("oscuro-noche", false, true);

        List<Map<String, Object>> publicos = servicio.listarPublicos();

        assertEquals(1, publicos.size(), "solo debe quedar el tema del admin");
        assertEquals("oscuro-noche", publicos.get(0).get("clave"));
        assertEquals(2, servicio.listarTodos().size(), "el admin ve los dos");
    }

    @Test
    @DisplayName("Un tema de fabrica tampoco se puede ver ni descargar por su clave")
    void fabrica_noSeSirvePorClave() {
        guardar("agua-clara", true, true);

        assertNull(servicio.detalle("agua-clara"), "el detalle publico da 404");
        assertNull(servicio.entidadPublica("agua-clara"), "la descarga da 404");
        assertNotNull(servicio.entidad("agua-clara"), "el admin si lo encuentra");
    }

    @Test
    @DisplayName("Un tema normal con esFabrica vacio (fila vieja) SI se ofrece")
    void null_esFabrica_seOfrece() {
        guardar("tema-viejo", false, true);

        assertEquals(1, servicio.listarPublicos().size());
        assertNotNull(servicio.detalle("tema-viejo"));
    }

    @Test
    @DisplayName("Crear un tema de fabrica lo guarda marcado y fuera de la Tienda")
    void crear_conEsFabrica() {
        Map<String, Object> body = cuerpo("Preset interno");
        body.put("esFabrica", true);

        Map<String, Object> r = servicio.crear(body, "root");

        assertTrue(Boolean.TRUE.equals(r.get("ok")));
        @SuppressWarnings("unchecked")
        Map<String, Object> ficha = (Map<String, Object>) r.get("tema");
        assertEquals(Boolean.TRUE, ficha.get("esFabrica"));
        assertTrue(servicio.listarPublicos().isEmpty());
        assertEquals(1, servicio.listarTodos().size());
    }

    @Test
    @DisplayName("Editar el flag saca o mete el tema de la Tienda")
    void actualizar_esFabrica() {
        TemaCatalogo t = guardar("mi-tema", false, true);
        assertEquals(1, servicio.listarPublicos().size());

        servicio.actualizar(t.getId(), Map.of("esFabrica", true));
        assertTrue(servicio.listarPublicos().isEmpty(), "al marcarlo de fabrica sale de la Tienda");

        servicio.actualizar(t.getId(), Map.of("esFabrica", false));
        assertEquals(1, servicio.listarPublicos().size(), "al desmarcarlo vuelve");
    }

    @Test
    @DisplayName("La siembra de fabrica deja todos los presets fuera de la Tienda")
    void sembrarFabrica_marcaLosPreset() {
        int sembrados = servicio.sembrarFabrica();

        assertTrue(sembrados > 0, "siembra los presets");
        assertTrue(servicio.listarPublicos().isEmpty(), "ningun preset va a la Tienda");
        assertEquals(sembrados, servicio.listarTodos().size());
        for (Map<String, Object> f : servicio.listarTodos()) {
            assertEquals(Boolean.TRUE, f.get("esFabrica"));
            assertEquals("sistema", f.get("creadoPor"));
        }
    }

    @Test
    @DisplayName("Backfill: los presets ya sembrados se marcan al arrancar (sin migracion)")
    void sembrarFabrica_backfillDeLosViejos() {
        // Como quedaron en una BD sembrada antes del flag: creadoPor = sistema, esFabrica NULL.
        guardar("agua-clara", false, true, "sistema");

        int marcados = servicio.sembrarFabrica();

        assertEquals(1, marcados, "marca el que faltaba");
        assertTrue(servicio.listarPublicos().isEmpty());
        assertEquals(Boolean.TRUE, servicio.listarTodos().get(0).get("esFabrica"));

        // Idempotente: al siguiente arranque no vuelve a marcar.
        assertEquals(0, servicio.sembrarFabrica());
    }
}