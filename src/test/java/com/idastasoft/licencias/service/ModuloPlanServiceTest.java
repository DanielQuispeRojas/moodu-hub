package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.ModuloCatalogo;
import com.idastasoft.licencias.model.ModuloPlan;
import com.idastasoft.licencias.repository.ModuloPlanRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Planes por modulo del catalogo (REQ-HUB-PLANES-01).
 *
 * <p>Lo importante aqui es la <b>sintesis legacy</b>: los modulos ya cargados
 * en el Hub no tienen filas en {@code modulo_plan} y no deben romperse: la
 * Tienda tiene que seguir viendo un plan, deducido del precio/vigencia
 * que ya tenia el modulo.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModuloPlanServiceTest {

    @Mock
    private ModuloPlanRepo repo;

    private ModuloPlanService service;

    @BeforeEach
    void setUp() {
        service = new ModuloPlanService(repo);
    }

    private ModuloCatalogo modulo(String clave, ModuloCatalogo.TipoLicencia tipo,
                                  BigDecimal precio, int vigenciaDias) {
        ModuloCatalogo m = new ModuloCatalogo();
        m.setId(1L);
        m.setClave(clave);
        m.setNombre(clave);
        m.setTipo(tipo);
        m.setPrecio(precio);
        m.setVigenciaDias(vigenciaDias);
        return m;
    }

    private void sinPlanesPropios() {
        when(repo.findByModuloIdOrderByOrdenAscIdAsc(1L)).thenReturn(List.of());
    }

    // ─── Sintesis desde los campos legacy ─────────────────────────

    @Test
    @DisplayName("modulo GRATIS sin planes -> plan PERMANENTE precio 0")
    void gratisSeSintetizaPermanente() {
        sinPlanesPropios();
        List<Map<String, Object>> out = service.planesPublicos(1L,
                modulo("gratis", ModuloCatalogo.TipoLicencia.GRATIS, BigDecimal.ZERO, 0));
        assertEquals(1, out.size());
        assertEquals("PERMANENTE", out.get(0).get("tipo"));
        assertEquals(Boolean.TRUE, out.get(0).get("gratuito"));
        assertEquals(0, out.get(0).get("vigenciaDias"));
        assertEquals("Gratuito", out.get(0).get("etiqueta"));
    }

    @Test
    @DisplayName("modulo PAGA de 30 dias -> plan MENSUAL x1")
    void pagaSeSintetizaMensual() {
        sinPlanesPropios();
        List<Map<String, Object>> out = service.planesPublicos(1L,
                modulo("caja", ModuloCatalogo.TipoLicencia.PAGA, new BigDecimal("1500"), 30));
        assertEquals(1, out.size());
        assertEquals("MENSUAL", out.get(0).get("tipo"));
        assertEquals(1, out.get(0).get("periodos"));
        assertEquals(30, out.get(0).get("vigenciaDias"));
        assertEquals("Mensual", out.get(0).get("etiqueta"));
    }

    @Test
    @DisplayName("modulo PAGA de 365 dias -> plan ANUAL x1")
    void pagoSeSintetizaAnual() {
        sinPlanesPropios();
        List<Map<String, Object>> out = service.planesPublicos(1L,
                modulo("recepcion", ModuloCatalogo.TipoLicencia.PAGA, new BigDecimal("3500"), 365));
        assertEquals("ANUAL", out.get(0).get("tipo"));
        assertEquals("Anual", out.get(0).get("etiqueta"));
    }

    @Test
    @DisplayName("modulo PAGA sin vigencia (0 dias) -> PERMANENTE")
    void sinVigenciaSeSintetizaPermanente() {
        sinPlanesPropios();
        List<Map<String, Object>> out = service.planesPublicos(1L,
                modulo("legacy", ModuloCatalogo.TipoLicencia.PAGA, new BigDecimal("900"), 0));
        assertEquals("PERMANENTE", out.get(0).get("tipo"));
        assertEquals("Permanente", out.get(0).get("etiqueta"));
    }

    // ─── Planes propios ────────────────────────────────────────────

    @Test
    @DisplayName("planes propios mandan sobre el legacy (cada plan su precio)")
    void planesPropiosMandan() {
        ModuloPlan m = new ModuloPlan();
        m.setId(10L);
        m.setModulo(modulo("caja", ModuloCatalogo.TipoLicencia.PAGA, new BigDecimal("1500"), 30));
        m.setTipo(ModuloPlan.TipoPlan.MENSUAL);
        m.setPrecio(new BigDecimal("39"));
        m.setPeriodos(1);
        ModuloPlan a = new ModuloPlan();
        a.setId(11L);
        a.setTipo(ModuloPlan.TipoPlan.PERMANENTE);
        a.setPrecio(new BigDecimal("1500"));
        a.setPeriodos(1);
        when(repo.findByModuloIdOrderByOrdenAscIdAsc(1L)).thenReturn(List.of(m, a));

        List<Map<String, Object>> out = service.planesPublicos(1L,
                modulo("caja", ModuloCatalogo.TipoLicencia.PAGA, new BigDecimal("1500"), 30));
        assertEquals(2, out.size());
        assertEquals("MENSUAL", out.get(0).get("tipo"));
        assertEquals(new BigDecimal("39"), out.get(0).get("precio"));
        assertEquals("PERMANENTE", out.get(1).get("tipo"));
    }

    @Test
    @DisplayName("un plan inactivo no se expone en la Tienda")
    void planInactivoNoSeExpone() {
        ModuloPlan p = new ModuloPlan();
        p.setId(12L);
        p.setTipo(ModuloPlan.TipoPlan.MENSUAL);
        p.setPrecio(new BigDecimal("10"));
        p.setPeriodos(1);
        p.setActivo(false);
        when(repo.findByModuloIdOrderByOrdenAscIdAsc(1L)).thenReturn(List.of(p));

        List<Map<String, Object>> out = service.planesPublicos(1L,
                modulo("caja", ModuloCatalogo.TipoLicencia.PAGA, new BigDecimal("1500"), 30));
        assertTrue(out.isEmpty(), "con un unico plan inactivo la lista publica queda vacia");
    }

    // ─── ADMIN ─────────────────────────────────────────────────────

    @Test
    @DisplayName("guardar crea el plan si no existe de ese tipo")
    void guardarCreaSiNoExiste() {
        when(repo.findByModuloIdOrderByOrdenAscIdAsc(1L)).thenReturn(List.of());
        when(repo.save(any(ModuloPlan.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> out = service.guardar(
                modulo("caja", ModuloCatalogo.TipoLicencia.PAGA, new BigDecimal("1500"), 30),
                Map.of("tipo", "MENSUAL", "precio", "39", "periodos", 1));
        assertEquals("MENSUAL", out.get("tipo"));
        assertEquals(new BigDecimal("39"), out.get("precio"));
    }

    @Test
    @DisplayName("guardar con tipo repetido EDITA el existente (no duplica)")
    void guardarEditaElExistente() {
        ModuloPlan p = new ModuloPlan();
        p.setId(13L);
        p.setTipo(ModuloPlan.TipoPlan.MENSUAL);
        p.setPrecio(new BigDecimal("39"));
        p.setPeriodos(1);
        when(repo.findByModuloIdOrderByOrdenAscIdAsc(1L)).thenReturn(List.of(p));
        when(repo.save(any(ModuloPlan.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> out = service.guardar(
                modulo("caja", ModuloCatalogo.TipoLicencia.PAGA, new BigDecimal("1500"), 30),
                Map.of("tipo", "MENSUAL", "precio", "49"));
        assertEquals(13L, out.get("id"), "debe editar el plan existente, no crear otro");
        assertEquals(new BigDecimal("49"), out.get("precio"));
    }

    @Test
    @DisplayName("PERMANENTE ignora los periodos que le manden")
    void permanenteIgnoraPeriodos() {
        when(repo.findByModuloIdOrderByOrdenAscIdAsc(1L)).thenReturn(List.of());
        when(repo.save(any(ModuloPlan.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> out = service.guardar(
                modulo("caja", ModuloCatalogo.TipoLicencia.PAGA, new BigDecimal("1500"), 30),
                Map.of("tipo", "PERMANENTE", "precio", "1500", "periodos", 12));
        assertEquals(1, out.get("periodos"));
        assertEquals(0, out.get("vigenciaDias"));
    }

    @Test
    @DisplayName("tipo de plan invalido -> error claro (no un 500)")
    void tipoInvalidoDaErrorClaro() {
        assertThrows(IllegalArgumentException.class, () -> service.guardar(
                modulo("caja", ModuloCatalogo.TipoLicencia.PAGA, BigDecimal.ONE, 30),
                Map.of("tipo", "TRIMESTRAL", "precio", "1")));
    }

    // ─── Modulos que son un TIER de MOODU (PLUS / PRO) ────────────────

    @Test
    @DisplayName("moodu_plus solo admite PERMANENTE")
    void plusSoloPermanente() {
        when(repo.findByModuloIdOrderByOrdenAscIdAsc(1L)).thenReturn(List.of());
        when(repo.save(any(ModuloPlan.class))).thenAnswer(i -> i.getArgument(0));

        // Se acepta PERMANENTE.
        Map<String, Object> ok = service.guardar(modulo("moodu_plus", ModuloCatalogo.TipoLicencia.PAGA, new BigDecimal("1200"), 0),
                Map.of("tipo", "PERMANENTE", "precio", "1200"));
        assertEquals("PERMANENTE", ok.get("tipo"));

        // Y se rechaza el mensual, con el motivo.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.guardar(
                modulo("moodu_plus", ModuloCatalogo.TipoLicencia.PAGA, new BigDecimal("1200"), 0),
                Map.of("tipo", "MENSUAL", "precio", "1200")));
        assertTrue(e.getMessage().contains("PERMANENTE"), e.getMessage());
    }

    @Test
    @DisplayName("moodu_pro admite MENSUAL y ANUAL, pero no PERMANENTE")
    void proNoAdmitePermanente() {
        when(repo.findByModuloIdOrderByOrdenAscIdAsc(1L)).thenReturn(List.of());
        when(repo.save(any(ModuloPlan.class))).thenAnswer(i -> i.getArgument(0));

        for (String tipo : new String[]{"MENSUAL", "ANUAL"}) {
            Map<String, Object> ok = service.guardar(modulo("moodu_pro", ModuloCatalogo.TipoLicencia.PAGA, new BigDecimal("299"), 30),
                    Map.of("tipo", tipo, "precio", "299"));
            assertEquals(tipo, ok.get("tipo"));
        }
        assertThrows(IllegalArgumentException.class, () -> service.guardar(
                modulo("moodu_pro", ModuloCatalogo.TipoLicencia.PAGA, new BigDecimal("299"), 30),
                Map.of("tipo", "PERMANENTE", "precio", "299")));
    }

    @Test
    @DisplayName("un modulo normal NO se restringe (puede ser permanente, mensual o anual)")
    void moduloNormalSinRestriccion() {
        when(repo.findByModuloIdOrderByOrdenAscIdAsc(1L)).thenReturn(List.of());
        when(repo.save(any(ModuloPlan.class))).thenAnswer(i -> i.getArgument(0));

        for (String tipo : new String[]{"PERMANENTE", "MENSUAL", "ANUAL"}) {
            Map<String, Object> ok = service.guardar(modulo("recepcion", ModuloCatalogo.TipoLicencia.PAGA, new BigDecimal("3500"), 0),
                    Map.of("tipo", tipo, "precio", "3500"));
            assertEquals(tipo, ok.get("tipo"));
        }
    }
}