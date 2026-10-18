package com.idastasoft.licencias.service;

import com.idastasoft.licencias.model.Licencia;
import com.idastasoft.licencias.model.LicenciaPack;
import com.idastasoft.licencias.model.PackDispositivos;
import com.idastasoft.licencias.repository.LicenciaPackRepo;
import com.idastasoft.licencias.repository.PackDispositivosRepo;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Packs de dispositivos adicionales (REQ-HUB-PACK-DISP-01).
 *
 * <p>Lo que se verifica aqui es el criterio de <b>oferta</b> (un pack sin
 * precio NO se ofrece: la Tienda lo oculta y la compra lo rechaza, para no
 * regalar cupo) y el calculo del <b>extra vigente</b> (un pack mensual
 * vencido no suma nada).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PackDispositivosServiceTest {

    @Mock
    private PackDispositivosRepo packRepo;
    @Mock
    private LicenciaPackRepo licenciaPackRepo;

    private PackDispositivosService service;

    @BeforeEach
    void setUp() {
        service = new PackDispositivosService(packRepo, licenciaPackRepo);
    }

    private Licencia licencia() {
        Licencia l = new Licencia();
        l.setId(1L);
        return l;
    }

    private PackDispositivos pack(String codigo, int dispositivos, String planConPrecio) {
        PackDispositivos p = new PackDispositivos();
        p.setCodigo(codigo);
        p.setDispositivos(dispositivos);
        p.setActivo(true);
        if ("MENSUAL".equals(planConPrecio)) p.setPrecioMensual(new BigDecimal("25.00"));
        if ("PERMANENTE".equals(planConPrecio)) p.setPrecioPermanente(new BigDecimal("150.00"));
        return p;
    }

    // ─── Catalogo: solo lo que se puede comprar ──────────────────────

    @Test
    @DisplayName("el catalogo publico oculta los packs sin precio (no se pueden comprar)")
    void catalogoSoloConPrecio() {
        when(packRepo.findByActivoTrueOrderByDispositivosAsc()).thenReturn(List.of(
                pack("D5", 5, "MENSUAL"),
                pack("D10", 10, null),        // sin ningun precio -> no se ofrece
                pack("D15", 15, "PERMANENTE")));

        List<PackDispositivos> out = service.catalogo();

        assertEquals(2, out.size());
        assertEquals(List.of("D5", "D15"), out.stream().map(PackDispositivos::getCodigo).toList());
    }

    @Test
    @DisplayName("el catalogo de admin si muestra los packs sin precio (para poder ponerles precio)")
    void catalogoAdminMuestraTodos() {
        when(packRepo.findAll()).thenReturn(List.of(pack("D10", 10, null)));
        assertEquals(1, service.catalogoCompleto().size());
    }

    @Test
    @DisplayName("un pack se ofrece con la forma de pago que tenga precio")
    void ofertaPorFormaDePago() {
        PackDispositivos p = pack("D5", 5, "PERMANENTE");
        assertTrue(p.offered());
        assertTrue(p.tienePrecio("PERMANENTE"));
        assertFalse(p.tienePrecio("MENSUAL"));
        assertNull(p.precioDe("MENSUAL"));
        assertEquals(new BigDecimal("150.00"), p.precioDe("permanente"));
    }

    // ─── Validacion al guardar ───────────────────────────────────────

    @Test
    @DisplayName("el codigo se normaliza a mayus y sin espacios")
    void guardaNormalizaCodigo() {
        when(packRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        PackDispositivos p = pack(" d5 ", 5, "MENSUAL");
        service.guardar(p);
        assertEquals("D5", p.getCodigo());
    }

    @Test
    @DisplayName("un pack debe dar al menos 1 dispositivo y tener codigo")
    void guardaValida() {
        assertThrows(IllegalArgumentException.class, () -> service.guardar(pack("D0", 0, "MENSUAL")));
        PackDispositivos sinCodigo = new PackDispositivos();
        sinCodigo.setDispositivos(5);
        assertThrows(IllegalArgumentException.class, () -> service.guardar(sinCodigo));
    }

    @Test
    @DisplayName("si no viene nombre, se arma con la cantidad de dispositivos")
    void guardaPoneNombrePorDefecto() {
        when(packRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        PackDispositivos p = pack("D20", 20, "MENSUAL");
        p.setNombre(null);
        service.guardar(p);
        assertEquals("20 dispositivos extra", p.getNombre());
    }

    // ─── Extra vigente (lo que suma al cupo base) ────────────────────

    @Test
    @DisplayName("sin pack, la licencia no tiene dispositivos extra")
    void sinPackCeroExtra() {
        when(licenciaPackRepo.findByLicencia(any())).thenReturn(Optional.empty());
        assertEquals(0, service.dispositivosExtraDe(licencia()));
    }

    @Test
    @DisplayName("pack PERMANENTE vigente suma sus dispositivos")
    void permanenteVigenteSuma() {
        when(licenciaPackRepo.findByLicencia(any())).thenReturn(Optional.of(packComprado("D10", LicenciaPack.Plan.PERMANENTE, null)));
        when(packRepo.findById("D10")).thenReturn(Optional.of(pack("D10", 10, "PERMANENTE")));

        assertEquals(10, service.dispositivosExtraDe(licencia()));
    }

    @Test
    @DisplayName("pack MENSUAL vigente suma; vencido NO suma (la licencia vuelve a su cupo base)")
    void mensualVenceYNoSuma() {
        when(packRepo.findById("D5")).thenReturn(Optional.of(pack("D5", 5, "MENSUAL")));

        when(licenciaPackRepo.findByLicencia(any())).thenReturn(Optional.of(
                packComprado("D5", LicenciaPack.Plan.MENSUAL, LocalDateTime.now().plusDays(20))));
        assertEquals(5, service.dispositivosExtraDe(licencia()));

        when(licenciaPackRepo.findByLicencia(any())).thenReturn(Optional.of(
                packComprado("D5", LicenciaPack.Plan.MENSUAL, LocalDateTime.now().minusDays(1))));
        assertEquals(0, service.dispositivosExtraDe(licencia()));
    }

    @Test
    @DisplayName("pack inactivo o borrado del catalogo no suma (y no rompe)")
    void packInactivoOSinCatalogoNoSuma() {
        when(packRepo.findById("D5")).thenReturn(Optional.of(pack("D5", 5, "MENSUAL")));
        when(licenciaPackRepo.findByLicencia(any())).thenReturn(Optional.of(
                packComprado("D5", LicenciaPack.Plan.PERMANENTE, null)));

        PackDispositivos inactivo = pack("D5", 5, "MENSUAL");
        inactivo.setActivo(false);
        when(packRepo.findById("D5")).thenReturn(Optional.of(inactivo));
        assertEquals(0, service.dispositivosExtraDe(licencia()));

        when(packRepo.findById("D5")).thenReturn(Optional.empty());
        assertEquals(0, service.dispositivosExtraDe(licencia()));
    }

    @Test
    @DisplayName("el pack se busca por codigo normalizado; un codigo vacio no rompe")
    void buscaPorCodigo() {
        when(packRepo.findById("D15")).thenReturn(Optional.of(pack("D15", 15, "MENSUAL")));
        assertNotNull(service.pack(" d15 "));
        assertNull(service.pack(""));
        assertNull(service.pack(null));
        lenient().when(packRepo.findById("D15")).thenReturn(Optional.empty());
    }

    private LicenciaPack packComprado(String codigo, LicenciaPack.Plan plan, LocalDateTime hasta) {
        LicenciaPack lp = new LicenciaPack();
        lp.setLicencia(licencia());
        lp.setCodigo(codigo);
        lp.setPlanEnum(plan);
        lp.setVigenciaHasta(hasta);
        lp.setActivo(true);
        return lp;
    }
}