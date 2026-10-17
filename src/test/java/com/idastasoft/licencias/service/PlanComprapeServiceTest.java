package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.Licencia;
import com.idastasoft.licencias.model.PlanComprape;
import com.idastasoft.licencias.repository.LicenciaRepo;
import com.idastasoft.licencias.repository.PlanComprapeRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Plan del marketplace Comprape (docs/comprape.md 4.2, R8/R9).
 *
 * <p>Lo que se cuida aca NO es el dato: es que el ternario que arma el limite no
 * haga <b>unboxing numerico</b> (una rama int y la otra Integer), porque con PLUS
 * el limite es null y reventaba con NullPointerException.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanComprapeServiceTest {

    @Mock
    private PlanComprapeRepo repo;
    @Mock
    private LicenciaRepo licenciaRepo;

    private PlanComprapeService servicio;

    /** Repo en memoria: los metodos que crean la fila devuelven estadoDe() que RE-LEE
     *  del repo (fuente unica de verdad, asi en produccion). Un mock que no
     *  persistiera haria fallar el flujo entero sin que hubiera un bug real. */
    private final Map<Long, PlanComprape> store = new HashMap<>();

    @BeforeEach
    void preparar() {
        Licencia l = new Licencia();
        l.setId(1L);
        l.setCorreo("dueno@acme.pe");
        when(licenciaRepo.findById(1L)).thenReturn(Optional.of(l));
        when(repo.findByLicenciaId(any())).thenAnswer(i -> Optional.ofNullable(store.get(i.getArgument(0))));
        when(repo.save(any(PlanComprape.class))).thenAnswer(i -> {
            PlanComprape p = i.getArgument(0);
            store.put(1L, p);
            return p;
        });
        servicio = new PlanComprapeService(repo, licenciaRepo);
    }

    /** Deja una fila ya guardada (como si el admin la hubiera configurado antes). */
    private void fila(PlanComprape p) {
        store.put(1L, p);
    }

    private Licencia licencia() {
        Licencia l = new Licencia();
        l.setId(1L);
        l.setCorreo("dueno@acme.pe");
        return l;
    }

    @Test
    @DisplayName("sin plan configurado: GRATUITO con 20 items y sin ventana")
    void sinFila_esGratuito() {
        Map<String, Object> e = servicio.estadoDe(licencia());

        assertEquals("GRATUITO", e.get("plan"));
        assertEquals(20, e.get("itemsLimite"));
        assertEquals(0, e.get("itemsPublicados"));
        assertEquals(20, e.get("itemsDisponibles"));
        assertEquals(false, e.get("listaCongelada"));
        assertEquals(0L, e.get("diasParaCambiar"));
    }

    @Test
    @DisplayName("PLUS: sin tope de items y la lista NO se congela")
    void plus_sinTopeYSinCongelar() {
        fila(new PlanComprape(null, PlanComprape.NivelComprape.PLUS,
                LocalDateTime.now().plusMonths(1)));

        Map<String, Object> e = servicio.estadoDe(licencia());

        assertEquals("PLUS", e.get("plan"));
        assertNull(e.get("itemsLimite"), "PLUS no tiene tope");
        assertNull(e.get("itemsDisponibles"), "sin tope no hay 'cupos libres'");
        assertEquals(false, e.get("listaCongelada"));
    }

    @Test
    @DisplayName("PLUS vencido cae a GRATUITO (nunca queda colgado)")
    void plusVencido_caeAGratuito() {
        fila(new PlanComprape(null, PlanComprape.NivelComprape.PLUS,
                LocalDateTime.now().minusDays(1)));

        Map<String, Object> e = servicio.estadoDe(licencia());

        assertEquals("GRATUITO", e.get("plan"), "el plan efectivo es el que manda");
        assertEquals("PLUS", e.get("planContratado"), "pero se ve lo que se compro");
        assertEquals(20, e.get("itemsLimite"));
    }

    @Test
    @DisplayName("ventana de 15 dias: cuenta los dias que faltan")
    void ventana_de15Dias() {
        PlanComprape p = new PlanComprape(null, PlanComprape.NivelComprape.GRATUITO, null);
        p.setProximaActualizacion(LocalDate.now().plusDays(9));
        fila(p);

        Map<String, Object> e = servicio.estadoDe(licencia());

        assertEquals(9L, e.get("diasParaCambiar"));
        assertEquals(true, e.get("listaCongelada"));
        assertEquals(true, e.get("precioEditable"),
                "el precio NUNCA espera: lo que congela es la lista");
    }

    @Test
    @DisplayName("PLUS exige vigencia futura: es mensual y SIN permanente")
    void plus_exigeVigencia() {
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> servicio.guardarPlan(1L, "PLUS", null));
        assertTrue(e1.getMessage().contains("mensual"), e1.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> servicio.guardarPlan(1L, "PLUS", "2020-01-01T00:00"), "vigencia pasada");
    }

    @Test
    @DisplayName("plan invalido -> 400 con motivo, no 500")
    void planInvalido() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> servicio.guardarPlan(1L, "PRO", null));
        assertTrue(e.getMessage().contains("GRATUITO"), e.getMessage());
    }

    @Test
    @DisplayName("guardar PLUS con vigencia futura deja el plan activo")
    void guardarPlus_ok() {
        String fut = LocalDateTime.now().plusMonths(1).withNano(0).toString();

        Map<String, Object> e = servicio.guardarPlan(1L, "plus", fut);

        assertEquals("PLUS", e.get("plan"));
        assertNotNull(e.get("vigenciaHasta"));
        assertNull(e.get("itemsLimite"));
    }

    @Test
    @DisplayName("volver a GRATUITO limpia la vigencia del PLUS")
    void volverAGratuito() {
        fila(new PlanComprape(null, PlanComprape.NivelComprape.PLUS,
                LocalDateTime.now().plusMonths(1)));

        Map<String, Object> e = servicio.guardarPlan(1L, "GRATUITO", null);

        assertEquals("GRATUITO", e.get("plan"));
        assertNull(e.get("vigenciaHasta"));
        assertEquals(20, e.get("itemsLimite"));
    }

    @Test
    @DisplayName("abrir la ventana la bloquea 15 dias en gratuito y nada en PLUS")
    void abrirVentana() {
        Map<String, Object> g = servicio.abrirVentana(1L);
        assertEquals(15L, g.get("diasParaCambiar"));
        assertEquals(true, g.get("listaCongelada"));

        fila(new PlanComprape(null, PlanComprape.NivelComprape.PLUS,
                LocalDateTime.now().plusMonths(1)));
        Map<String, Object> p = servicio.abrirVentana(1L);
        assertEquals(0L, p.get("diasParaCambiar"));
        assertEquals(false, p.get("listaCongelada"));
    }

    @Test
    @DisplayName("registrar una publicacion mueve el consumo y recalcula los cupos libres")
    void registrarPublicacion() {
        Map<String, Object> e = servicio.registrarPublicacion(1L, 12);

        assertEquals(12, e.get("itemsPublicados"));
        assertEquals(20, e.get("itemsLimite"));
        assertEquals(8, e.get("itemsDisponibles"));
    }
}