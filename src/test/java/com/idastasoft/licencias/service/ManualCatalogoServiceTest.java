package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.ModuloCatalogo;
import com.idastasoft.licencias.repository.ModuloCatalogoRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManualCatalogoServiceTest {

    @Mock private ModuloCatalogoRepo moduloRepo;

    private ManualCatalogoService service;
    private Map<Long, ModuloCatalogo> store;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        store = new LinkedHashMap<>();
        nextId = 1;
        when(moduloRepo.findAll()).thenAnswer(i -> new java.util.ArrayList<>(store.values()));
        when(moduloRepo.saveAndFlush(any(ModuloCatalogo.class))).thenAnswer(i -> {
            ModuloCatalogo m = i.getArgument(0);
            if (m.getId() == null) m.setId(nextId++);
            store.put(m.getId(), m);
            return m;
        });
        when(moduloRepo.save(any(ModuloCatalogo.class))).thenAnswer(i -> {
            ModuloCatalogo m = i.getArgument(0);
            if (m.getId() == null) m.setId(nextId++);
            store.put(m.getId(), m);
            return m;
        });
        when(moduloRepo.findById(any(Long.class))).thenAnswer(i ->
                Optional.ofNullable(store.get(i.getArgument(0, Long.class))));
        when(moduloRepo.findByClave(any(String.class))).thenAnswer(i -> {
            String clave = i.getArgument(0, String.class);
            return store.values().stream().filter(m -> clave.equals(m.getClave())).findFirst().orElse(null);
        });
        service = new ManualCatalogoService(moduloRepo);
    }

    private ModuloCatalogo modulo(String clave) {
        ModuloCatalogo m = new ModuloCatalogo();
        m.setClave(clave);
        m.setNombre(clave.toUpperCase());
        m.setId(nextId++);
        store.put(m.getId(), m);
        return m;
    }

    private static String manualValido() {
        return "{\"version\":\"1.0\",\"titulo\":\"Manual\","
                + "\"secciones\":[{\"clave\":\"funciones\",\"titulo\":\"Funciones\",\"items\":["
                + "{\"titulo\":\"Abrir\",\"objetivo\":\"Abrir la caja\",\"pasos\":["
                + "{\"t\":\"Paso 1\",\"d\":\"Haz clic\"}]}]}],"
                + "\"desarrollo\":{\"nota\":\"interno\"},"
                + "\"preguntas\":[{\"p\":\"¿Por qué?\",\"r\":\"Porque sí\"}],"
                + "\"imagenes\":{\"principal\":\"data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=\"}}";
    }

    @Test
    void normalizar_validoDevuelveJsonCompacto() {
        String normalizado = service.normalizar(manualValido());
        assertNotNull(normalizado);
        assertTrue(normalizado.contains("\"secciones\""));
        assertFalse(normalizado.contains("\n"));
    }

    @Test
    void normalizar_vacioLimpiaElManual() {
        assertNull(service.normalizar(null));
        assertNull(service.normalizar("   "));
    }

    @Test
    void normalizar_sinSeccionesRechaza() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.normalizar("{\"secciones\":[]}"));
        assertTrue(e.getMessage().contains("al menos una seccion"));
    }

    @Test
    void normalizar_pasoSinTRechazaConClaveReal() {
        String malo = "{\"secciones\":[{\"titulo\":\"Funciones\",\"items\":[{\"titulo\":\"Abrir\","
                + "\"pasos\":[{\"d\":\"sin titulo\"}]}]}]}";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.normalizar(malo));
        assertTrue(e.getMessage().contains("'t'"));
    }

    @Test
    void normalizar_preguntaSinRRechaza() {
        String malo = manualValido().replace("{\"p\":\"¿Por qué?\",\"r\":\"Porque sí\"}",
                "{\"p\":\"¿Por qué?\"}");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.normalizar(malo));
        assertTrue(e.getMessage().contains("'p' y 'r'"));
    }

    @Test
    void normalizar_imagenPorRutaRechaza() {
        String malo = manualValido().replace("data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=",
                "manuales/caja/principal.svg");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.normalizar(malo));
        assertTrue(e.getMessage().contains("data-URL"));
    }

    @Test
    void publico_jsonCorruptoNoRompe() {
        ModuloCatalogo m = modulo("caja");
        m.setManual("{esto no es json");
        Map<String, Object> vista = service.publico(m);
        assertEquals(false, vista.get("tieneManual"));
        assertNotNull(vista.get("motivo"));
    }

    @Test
    void publico_sinManualDaMotivo() {
        Map<String, Object> vista = service.publico(modulo("caja"));
        assertEquals(false, vista.get("tieneManual"));
        assertNull(vista.get("manual"));
    }

    @Test
    void guardar_persisteYDevuelveLaVista() {
        ModuloCatalogo m = modulo("caja");
        Map<String, Object> r = service.guardar(m.getId(), manualValido());
        assertEquals(true, r.get("ok"));
        assertEquals("caja", r.get("clave"));
        assertEquals(true, r.get("tieneManual"));
        assertTrue(store.get(m.getId()).tieneManual());
    }

    @Test
    void sembrarParaModulo_altaTardiaRecibeSuGuia() {
        ModuloCatalogo caja = modulo("caja"); // alta despues del arranque
        assertEquals(true, service.sembrarParaModulo(caja));
        assertTrue(caja.tieneManual());
        assertEquals(false, service.sembrarParaModulo(caja)); // no sobrescribe
    }

    @Test
    void sembrarFabrica_soloUnaVezYNuncaSobrescribe() {
        for (String clave : new String[]{"adquisiciones", "almacen", "caja", "recepcion", "restaurante", "votaciones"}) {
            modulo(clave);
        }
        int primera = service.sembrarFabrica();
        assertEquals(6, primera);
        assertTrue(store.values().stream().allMatch(ModuloCatalogo::tieneManual));
        String editado = "{\"secciones\":[{\"titulo\":\"Editado\",\"items\":[{\"titulo\":\"X\","
                + "\"pasos\":[{\"t\":\"Y\"}]}]}]}";
        store.get(3L).setManual(editado); // el admin edito "caja" (id 3)
        assertEquals(0, service.sembrarFabrica());
        assertEquals(editado, store.get(3L).getManual());
    }
}
