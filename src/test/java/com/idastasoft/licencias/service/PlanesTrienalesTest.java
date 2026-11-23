package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.HubParametro;
import com.idastasoft.licencias.model.LicenciaModulo;
import com.idastasoft.licencias.model.ModuloCatalogo;
import com.idastasoft.licencias.model.ModuloPlan;
import com.idastasoft.licencias.model.TipoMoodu;
import com.idastasoft.licencias.repository.HubParametroRepo;
import com.idastasoft.licencias.repository.LicenciaModuloRepo;
import com.idastasoft.licencias.repository.ModuloPlanRepo;
import com.idastasoft.licencias.repository.ModuloCatalogoRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * HUB-11 (TC-HUB-11): plan TRIENAL en vez de PERMANENTE para los modulos de
 * pago, con un tope de anios configurable en el Hub.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanesTrienalesTest {

    @Mock private HubParametroRepo parametroRepo;
    @Mock private ModuloPlanRepo planRepo;
    @Mock private LicenciaModuloRepo terminoRepo;
    @Mock private ModuloCatalogoRepo moduloRepo;

    private final Map<String, String> valores = new HashMap<>();
    private ParametroService parametros;
    private ModuloPlanService planes;

    @BeforeEach
    void setUp() {
        valores.clear();
        when(parametroRepo.findByClaveIgnoreCase(anyString())).thenAnswer(inv -> {
            String clave = inv.getArgument(0, String.class);
            String v = valores.get(clave);
            return v == null ? Optional.empty() : Optional.of(new HubParametro(clave, v));
        });
        when(parametroRepo.save(any(HubParametro.class))).thenAnswer(inv -> {
            HubParametro p = inv.getArgument(0);
            valores.put(p.getClave(), p.getValor());
            return p;
        });
        when(planRepo.findByModuloIdOrderByOrdenAscIdAsc(any())).thenReturn(new ArrayList<>());
        when(planRepo.save(any(ModuloPlan.class))).thenAnswer(i -> i.getArgument(0));
        parametros = new ParametroService(parametroRepo);
        planes = new ModuloPlanService(planRepo, parametros);
    }

    private ModuloCatalogo modulo(String clave, ModuloCatalogo.TipoLicencia tipo) {
        ModuloCatalogo m = new ModuloCatalogo();
        m.setId(1L);
        m.setClave(clave);
        m.setNombre(clave);
        m.setTipo(tipo);
        m.setPrecio(new BigDecimal("1500"));
        m.setVigenciaDias(0);
        return m;
    }

    @Test
    @DisplayName("TRIENAL calcula su vigencia con el tope del Hub (3 por defecto)")
    void trienalUsaTope() {
        ModuloPlan p = new ModuloPlan();
        p.setTipo(ModuloPlan.TipoPlan.TRIENAL);
        assertEquals(1095, p.vigenciaDias(3));
        assertEquals(1825, p.vigenciaDias(5));
    }

    @Test
    @DisplayName("Crear plan TRIENAL con precio propio y etiqueta dinamica")
    void crearTrienalConPrecioPropio() {
        valores.put(ParametroService.MAX_ANIOS_LICENCIA, "3");
        Map<String, Object> r = planes.guardar(modulo("caja", ModuloCatalogo.TipoLicencia.PAGA),
                Map.of("tipo", "TRIENAL", "precio", 1500, "periodos", 1));
        assertEquals("TRIENAL", r.get("tipo"));
        assertEquals(1095, r.get("vigenciaDias"));
        assertEquals("Plan de 3 años", r.get("etiqueta"));
        assertEquals(1, r.get("periodos"));
    }

    @Test
    @DisplayName("Cambiar el tope a 5 recalcula sin recompilar")
    void cambiarTopeRecalcula() {
        valores.put(ParametroService.MAX_ANIOS_LICENCIA, "5");
        assertEquals(5, parametros.maxAniosLicencia());
        Map<String, Object> r = planes.guardar(modulo("caja", ModuloCatalogo.TipoLicencia.PAGA),
                Map.of("tipo", "TRIENAL", "precio", 1500));
        assertEquals(1825, r.get("vigenciaDias"));
        assertEquals("Plan de 5 años", r.get("etiqueta"));
    }

    @Test
    @DisplayName("PERMANENTE ya no se puede crear como plan de pago")
    void permanenteRechazado() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                planes.guardar(modulo("caja", ModuloCatalogo.TipoLicencia.PAGA),
                        Map.of("tipo", "PERMANENTE", "precio", 1500)));
        assertTrue(e.getMessage().contains("Ya no se ofrece"));
    }

    @Test
    @DisplayName("Un plan que supera el tope de anios se rechaza con motivo")
    void topeDeAniosSeRespeta() {
        valores.put(ParametroService.MAX_ANIOS_LICENCIA, "3");
        ModuloCatalogo m = modulo("caja", ModuloCatalogo.TipoLicencia.PAGA);
        ModuloPlan mensual = new ModuloPlan();
        mensual.setTipo(ModuloPlan.TipoPlan.MENSUAL);
        mensual.setPrecio(new BigDecimal("100"));
        when(planRepo.findByModuloIdOrderByOrdenAscIdAsc(1L)).thenReturn(List.of(mensual));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                planes.guardar(m, Map.of("tipo", "ANUAL", "periodos", 4, "precio", 1000)));
        assertTrue(e.getMessage().contains("3 años"));

        valores.put(ParametroService.MAX_ANIOS_LICENCIA, "5");
        Map<String, Object> ok = planes.guardar(m, Map.of("tipo", "ANUAL", "periodos", 4, "precio", 1000));
        assertEquals("ANUAL", ok.get("tipo"));
        assertEquals(1460, ok.get("vigenciaDias"));
    }

    @Test
    @DisplayName("Un valor corrupto del parametro cae al default 3")
    void parametroCorruptoCaeAlDefault() {
        valores.put(ParametroService.MAX_ANIOS_LICENCIA, "xxx");
        assertEquals(3, parametros.maxAniosLicencia());
        valores.put(ParametroService.MAX_ANIOS_LICENCIA, "0");
        assertEquals(3, parametros.maxAniosLicencia());
        valores.put(ParametroService.MAX_ANIOS_LICENCIA, "99");
        assertEquals(3, parametros.maxAniosLicencia());
    }

    @Test
    @DisplayName("El rango del parametro se valida al guardar")
    void rangoDelParametro() {
        assertThrows(IllegalArgumentException.class, () ->
                parametros.guardarVarios(Map.of(ParametroService.MAX_ANIOS_LICENCIA, 0), "admin"));
        assertThrows(IllegalArgumentException.class, () ->
                parametros.guardarVarios(Map.of(ParametroService.MAX_ANIOS_LICENCIA, 21), "admin"));
        parametros.guardarVarios(Map.of(ParametroService.MAX_ANIOS_LICENCIA, 5), "admin");
        assertEquals("5", valores.get(ParametroService.MAX_ANIOS_LICENCIA));
    }

    @Test
    @DisplayName("Legacy: un plan PERMANENTE de pago se convierte; el GRATIS se conserva")
    void migrarPlanesPermanentes() {
        ModuloPlan pago = new ModuloPlan();
        pago.setTipo(ModuloPlan.TipoPlan.PERMANENTE);
        pago.setModulo(modulo("caja", ModuloCatalogo.TipoLicencia.PAGA));
        ModuloPlan gratis = new ModuloPlan();
        gratis.setTipo(ModuloPlan.TipoPlan.PERMANENTE);
        gratis.setModulo(modulo("gratis", ModuloCatalogo.TipoLicencia.GRATIS));
        when(planRepo.findAll()).thenReturn(List.of(pago, gratis));

        assertEquals(1, planes.convertirPermanentesDePago());
        assertEquals(ModuloPlan.TipoPlan.TRIENAL, pago.getTipo());
        assertEquals(ModuloPlan.TipoPlan.PERMANENTE, gratis.getTipo());
    }

    @Test
    @DisplayName("Un termino TRIENAL caduca tantos anios como el tope desde su inicio")
    void terminoTrienalCaduca() {
        LicenciaService licencias = new LicenciaService(null, null, terminoRepo, null, moduloRepo, null, null);
        LicenciaModulo t = new LicenciaModulo();
        t.setPlan(LicenciaModulo.PlanModulo.TRIENAL);
        t.setFechaInicio(LocalDateTime.of(2026, 11, 22, 10, 0));
        // Sin ParametroService inyectado rige el default 3.
        assertEquals(LocalDateTime.of(2029, 11, 22, 10, 0), licencias.expiracionTermino(t));
    }

    @Test
    @DisplayName("Legacy: un termino PERMANENTE de pago se convierte desde hoy; el GRATIS no")
    void migrarTerminosPermanentes() {
        LicenciaModulo pago = new LicenciaModulo();
        pago.setModuloClave("caja");
        pago.setPlan(LicenciaModulo.PlanModulo.PERMANENTE);
        pago.setFechaInicio(LocalDateTime.of(2020, 1, 1, 0, 0));
        LicenciaModulo gratis = new LicenciaModulo();
        gratis.setModuloClave("gratis");
        gratis.setPlan(LicenciaModulo.PlanModulo.PERMANENTE);
        when(terminoRepo.findAll()).thenReturn(List.of(pago, gratis));
        when(moduloRepo.findByClave("caja")).thenReturn(modulo("caja", ModuloCatalogo.TipoLicencia.PAGA));
        when(moduloRepo.findByClave("gratis")).thenReturn(modulo("gratis", ModuloCatalogo.TipoLicencia.GRATIS));

        LicenciaService licencias = new LicenciaService(null, null, terminoRepo, null, moduloRepo, null, null);
        assertEquals(1, licencias.convertirPermanentesDePago());
        assertEquals(LicenciaModulo.PlanModulo.TRIENAL, pago.getPlan());
        assertTrue(pago.getFechaInicio().isAfter(LocalDateTime.of(2026, 1, 1, 0, 0)));
        assertEquals(LicenciaModulo.PlanModulo.PERMANENTE, gratis.getPlan());
    }

    @Test
    @DisplayName("MOODU PLUS admite TRIENAL; PRO sigue sin plazo cerrado")
    void periodicidadesPorTier() {
        assertTrue(TipoMoodu.PLUS.admite("TRIENAL"));
        assertFalse(TipoMoodu.PRO.admite("TRIENAL"));
        assertTrue(TipoMoodu.PLUS.admite("PERMANENTE"));
    }
}
