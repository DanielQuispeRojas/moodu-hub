package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.Licencia;
import com.idastasoft.licencias.model.ModuloCatalogo;
import com.idastasoft.licencias.repository.*;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** SP-1 (REQ-HUBPAQ-01 / REQ-HUBDISP-01): factor anual y pool de dispositivos. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HubPaqSp1Test {

    @Mock private HubParametroRepo parametroRepo;
    @Mock private LicenciaRepo licenciaRepo;
    @Mock private MaquinaRepo maquinaRepo;
    @Mock private LicenciaModuloRepo terminoRepo;
    @Mock private ModuloCatalogoRepo moduloRepo;
    @Mock private PackDispositivosRepo packRepo;
    @Mock private LicenciaPackRepo licenciaPackRepo;

    private ParametroService parametros;
    private LicenciaService licencias;

    @BeforeEach
    void setUp() {
        when(parametroRepo.findByClaveIgnoreCase(any())).thenReturn(Optional.empty());
        when(parametroRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(terminoRepo.findByLicenciaId(any())).thenReturn(List.of());
        when(licenciaPackRepo.findByLicencia(any())).thenReturn(Optional.empty());
        // Servicios concretos: se instancian reales (Mockito inline no los moca
        // en este JDK). Sin pack vigente el extra es 0, suficiente para el pool.
        parametros = new ParametroService(parametroRepo);
        PackDispositivosService packs = new PackDispositivosService(packRepo, licenciaPackRepo);
        licencias = new LicenciaService(licenciaRepo, maquinaRepo, terminoRepo,
                null, moduloRepo, packs, licenciaPackRepo);
    }

    private Licencia licencia(int base, String... modulos) {
        Licencia l = new Licencia();
        l.setId(1L);
        l.setCorreo("qa@test.com");
        l.setMaquinasPermitidas(base);
        l.setVigenciaUnidad(Licencia.UnidadVigencia.INDEFINIDO);
        l.setVigenciaValor(0);
        l.setActiva(true);
        l.setModulos(List.of(modulos));
        return l;
    }

    private ModuloCatalogo modulo(String clave, int dispositivos) {
        ModuloCatalogo m = new ModuloCatalogo();
        m.setClave(clave);
        m.setNombre(clave);
        m.setTipo(ModuloCatalogo.TipoLicencia.PAGA);
        m.setPrecio(BigDecimal.TEN);
        m.setDispositivosIncluidos(dispositivos);
        m.setActivo(true);
        return m;
    }

    @Test
    @DisplayName("factor anual por defecto = 10 si no hay parametro")
    void factorPorDefecto() {
        assertEquals(10, parametros.factorAnual());
    }

    @Test
    @DisplayName("factor anual valido se guarda; fuera de rango se rechaza")
    void factorGuardado() {
        assertEquals(12, parametros.guardarFactorAnual("12", "qa"));
        assertThrows(IllegalArgumentException.class, () -> parametros.guardarFactorAnual("0", "qa"));
        assertThrows(IllegalArgumentException.class, () -> parametros.guardarFactorAnual("25", "qa"));
        assertThrows(IllegalArgumentException.class, () -> parametros.guardarFactorAnual("abc", "qa"));
    }

    @Test
    @DisplayName("pool = base del core + dispositivos de modulos activos (+ pack vigente)")
    void poolUnico() {
        Licencia l = licencia(1, "recepcion", "caja");
        when(moduloRepo.findByClave("recepcion")).thenReturn(modulo("recepcion", 2));
        when(moduloRepo.findByClave("caja")).thenReturn(modulo("caja", 1));

        assertEquals(3, licencias.dispositivosIncluidosDe(l));
        assertEquals(4, licencias.dispositivosPermitidosDe(l));
        assertEquals(4, licencias.maquinasEfectivas(l));
    }

    @Test
    @DisplayName("modulo GRATIS/legacy sin dispositivos cuenta 0; licencia legacy sin campo usa base")
    void poolLegacy() {
        Licencia l = licencia(3, "gratis");
        ModuloCatalogo m = modulo("gratis", 0);
        when(moduloRepo.findByClave("gratis")).thenReturn(m);
        assertEquals(3, licencias.dispositivosPermitidosDe(l));
        assertNull(l.getDispositivosPermitidos());
    }

    @Test
    @DisplayName("snapshot del pool se actualiza al recalcular")
    void snapshot() {
        Licencia l = licencia(1, "recepcion");
        when(moduloRepo.findByClave("recepcion")).thenReturn(modulo("recepcion", 2));
        licencias.recalcularDispositivos(l);
        assertEquals(3, l.getDispositivosPermitidos());
        verify(licenciaRepo).save(l);
    }

    @Test
    @DisplayName("descuento con rango: vigente hoy aplica; fuera de rango no")
    void descuentoEnModelo() {
        ModuloCatalogo m = modulo("caja", 0);
        m.setPrecio(new BigDecimal("100"));
        m.setDescuentoPct(20);
        m.setDescuentoInicio(java.time.LocalDate.now().minusDays(1));
        m.setDescuentoFin(java.time.LocalDate.now().plusDays(1));
        assertTrue(m.descuentoVigente(java.time.LocalDate.now()));
        assertEquals(0, new BigDecimal("80.00").compareTo(
                m.precioConDescuento(new BigDecimal("100"), java.time.LocalDate.now())));

        m.setDescuentoInicio(java.time.LocalDate.now().plusDays(3));
        assertFalse(m.descuentoVigente(java.time.LocalDate.now()));
        assertEquals(0, new BigDecimal("100").compareTo(
                m.precioConDescuento(new BigDecimal("100"), java.time.LocalDate.now())));
    }

    @Test
    @DisplayName("respuesta de validacion expone el pool nuevo y mantiene los alias legacy")
    void clavesDeRespuesta() {
        Licencia l = licencia(1, "caja");
        when(moduloRepo.findByClave("caja")).thenReturn(modulo("caja", 2));
        when(maquinaRepo.countByLicenciaIdAndActivaTrue(1L)).thenReturn(1L);
        when(licenciaRepo.findByCorreoAndClaveActivacion("qa@test.com", "CLAVE"))
                .thenReturn(Optional.of(l));
        Map<String, Object> res = licencias.consultarEstado("qa@test.com", "CLAVE");
        assertEquals(Boolean.TRUE, res.get("ok"));
        assertEquals(3, res.get("dispositivosPermitidos"));
        assertEquals(1L, res.get("dispositivosActivos"));
        assertEquals(3, res.get("maquinasPermitidas"));
        assertEquals(1L, res.get("maquinasActivas"));
        verify(maquinaRepo, never()).save(any());
    }
}
